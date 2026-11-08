package com.example.pdftoolkit.reorder

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

data class ReorderResult(val uri: Uri, val displayName: String)

data class ReorderUiState(
    val docId: Int = 0,                    // changes on every newly opened PDF (thumbnail cache key)
    val fileName: String? = null,
    val pageCount: Int = 0,
    val order: List<Int> = emptyList(),    // original zero-based page indexes in the current order
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: ReorderResult? = null
) {
    val hasFile get() = fileName != null && order.isNotEmpty()
    val isModified get() = order.indices.any { order[it] != it }
}

class ReorderPdfViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = ReorderPdfProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(ReorderUiState())
    val state: StateFlow<ReorderUiState> = _state.asStateFlow()

    private var thumbnailer: PdfPageThumbnailer? = null
    private var workingFile: File? = null
    private var saveJob: Job? = null

    fun onPdfPicked(uri: Uri) {
        saveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, result = null) }
            try {
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                val file = processor.copyToCache(uri)
                val newThumbnailer = withContext(Dispatchers.IO) { PdfPageThumbnailer(file) }

                if (newThumbnailer.pageCount == 0) {
                    withContext(Dispatchers.IO) { newThumbnailer.close() }
                    throw IllegalStateException("This PDF has no pages")
                }

                val old = thumbnailer
                thumbnailer = newThumbnailer
                workingFile = file
                withContext(Dispatchers.IO) { old?.close() }

                _state.update {
                    it.copy(
                        docId = it.docId + 1,
                        fileName = name,
                        pageCount = newThumbnailer.pageCount,
                        order = List(newThumbnailer.pageCount) { i -> i },
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

    suspend fun thumbnail(originalIndex: Int): Bitmap? =
        try { thumbnailer?.thumbnail(originalIndex) } catch (e: Exception) { null }

    /** Moves the page at position [from] to position [to] (others shift). */
    fun movePage(from: Int, to: Int) {
        _state.update { s ->
            if (from !in s.order.indices || to !in s.order.indices || from == to) return@update s
            val list = s.order.toMutableList()
            list.add(to, list.removeAt(from))
            s.copy(order = list)
        }
    }

    fun resetOrder() {
        _state.update { it.copy(order = List(it.pageCount) { i -> i }) }
    }

    fun save() {
        val source = workingFile ?: return
        val s = _state.value
        if (s.isSaving || !s.isModified) return

        saveJob = viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "reorder_output.pdf")
            _state.update {
                it.copy(isSaving = true, progress = 0f, progressLabel = "Reordering pages…", error = null)
            }
            try {
                processor.reorder(source, s.order, out) { p ->
                    _state.update { it.copy(progress = p * 0.9f) }
                }
                _state.update { it.copy(progress = 0.95f, progressLabel = "Saving file…") }

                val base = (s.fileName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
                val name = "${base}_reordered.pdf"
                val uri = sink.save(out, name)

                _state.update {
                    it.copy(isSaving = false, progress = 1f, result = ReorderResult(uri, name))
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