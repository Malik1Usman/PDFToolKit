package com.example.pdftoolkit.delete

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.reorder.MediaStoreOutputSink
import com.example.pdftoolkit.reorder.PdfOutputSink
import com.example.pdftoolkit.reorder.PdfPageThumbnailer
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DeleteResult(val uri: Uri, val displayName: String, val deleted: Int, val remaining: Int)

data class DeleteUiState(
    val docId: Int = 0,                      // changes per opened PDF (thumbnail cache key)
    val fileName: String? = null,
    val pageCount: Int = 0,
    val selected: Set<Int> = emptySet(),     // zero-based page indexes marked for deletion
    val showConfirm: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: DeleteResult? = null
) {
    val hasFile get() = fileName != null && pageCount > 0
    val allSelected get() = pageCount > 0 && selected.size == pageCount
    val remaining get() = pageCount - selected.size
    /** Delete is allowed only when something is selected AND at least one page stays. */
    val canDelete get() = selected.isNotEmpty() && !allSelected && !isSaving
}

class DeletePagesViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = DeletePagesProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(DeleteUiState())
    val state: StateFlow<DeleteUiState> = _state.asStateFlow()

    private var thumbnailer: PdfPageThumbnailer? = null
    private var workingFile: File? = null
    private var saveJob: Job? = null

    fun onPdfPicked(uri: Uri) {
        saveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, result = null, showConfirm = false) }
            try {
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                val file = processor.copyToCache(uri)
                val newThumbnailer = withContext(Dispatchers.IO) { PdfPageThumbnailer(file) }

                if (newThumbnailer.pageCount == 0) {
                    withContext(Dispatchers.IO) { newThumbnailer.close() }
                    throw IllegalStateException("This PDF has no pages")
                }

                val old = thumbnailer
                val oldFile = workingFile
                thumbnailer = newThumbnailer
                workingFile = file
                withContext(Dispatchers.IO) {
                    old?.close()
                    oldFile?.delete()
                }

                _state.update {
                    it.copy(
                        docId = it.docId + 1,
                        fileName = name,
                        pageCount = newThumbnailer.pageCount,
                        selected = emptySet(),
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    suspend fun thumbnail(index: Int): Bitmap? =
        try { thumbnailer?.thumbnail(index) } catch (e: Exception) { null }

    // ───── selection ─────
    fun toggleSelect(index: Int) = _state.update {
        it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
    }

    fun selectAll() = _state.update { it.copy(selected = (0 until it.pageCount).toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    // ───── delete flow ─────
    fun requestDelete() = _state.update { if (it.canDelete) it.copy(showConfirm = true) else it }
    fun dismissConfirm() = _state.update { it.copy(showConfirm = false) }

    fun confirmDelete() {
        val source = workingFile ?: return
        val s = _state.value
        if (!s.canDelete) {
            _state.update { it.copy(showConfirm = false) }
            return
        }

        saveJob = viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "delete_output.pdf")
            _state.update {
                it.copy(showConfirm = false, isSaving = true, progress = 0f,
                    progressLabel = "Removing pages…", error = null)
            }
            try {
                processor.deletePages(source, s.selected, out) { p ->
                    _state.update { it.copy(progress = p * 0.9f) }
                }
                _state.update { it.copy(progress = 0.95f, progressLabel = "Saving file…") }

                val base = (s.fileName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
                val name = "${base}_pages_removed.pdf"
                val uri = sink.save(out, name)

                _state.update {
                    it.copy(
                        isSaving = false,
                        progress = 1f,
                        selected = emptySet(),
                        result = DeleteResult(uri, name, s.selected.size, s.pageCount - s.selected.size)
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isSaving = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            } finally {
                out.delete()
            }
        }
    }

    fun cancelSave() {
        saveJob?.cancel()
    }

    fun dismissResult() = _state.update { it.copy(result = null) }
    fun clearError() = _state.update { it.copy(error = null) }

    private fun queryDisplayName(uri: Uri): String =
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "document.pdf"

    private fun Throwable.toUserMessage(): String = when (this) {
        is SecurityException, is InvalidPasswordException ->
            "This PDF is password protected. Please remove the password first."
        else -> message ?: "Something went wrong. Please try another file."
    }

    override fun onCleared() {
        saveJob?.cancel()
        thumbnailer?.close()
        workingFile?.delete()
        super.onCleared()
    }
}