package com.example.pdftoolkit.rotate

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

enum class RotateKind { PDF, IMAGE }

data class RotateResult(val uri: Uri, val displayName: String, val mimeType: String)

data class RotateUiState(
    val docId: Int = 0,                         // changes per opened file (thumbnail cache key)
    val kind: RotateKind? = null,
    val fileName: String? = null,
    // PDF
    val pageCount: Int = 0,
    val rotations: List<Int> = emptyList(),     // extra clockwise degrees per page (0/90/180/270)
    val selected: Set<Int> = emptySet(),        // empty = rotate ALL pages
    // Image
    val image: Bitmap? = null,                  // down-sampled preview, EXIF already applied
    val imageRotation: Int = 0,
    // Status
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: RotateResult? = null
) {
    val hasFile get() = kind != null
    val isModified
        get() = when (kind) {
            RotateKind.PDF -> rotations.any { it != 0 }
            RotateKind.IMAGE -> imageRotation != 0
            null -> false
        }
    val rotatedPageCount get() = rotations.count { it != 0 }
}

class RotateViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = RotateProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(RotateUiState())
    val state: StateFlow<RotateUiState> = _state.asStateFlow()

    private var thumbnailer: PdfPageThumbnailer? = null
    private var workingFile: File? = null
    private var imageUri: Uri? = null
    private var imageMime: String = "image/jpeg"
    private var saveJob: Job? = null

    fun onFilePicked(uri: Uri) {
        saveJob?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, result = null) }
            try {
                val resolver = getApplication<Application>().contentResolver
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                val mime = resolver.getType(uri)
                val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
                val isImage = mime?.startsWith("image/") == true

                when {
                    isPdf -> loadPdf(uri, name)
                    isImage -> loadImage(uri, name, mime ?: "image/jpeg")
                    else -> throw IllegalArgumentException("Please choose a PDF or an image file")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    private suspend fun loadPdf(uri: Uri, name: String) {
        val file = processor.copyToCache(uri)
        val newThumbnailer = withContext(Dispatchers.IO) { PdfPageThumbnailer(file) }
        if (newThumbnailer.pageCount == 0) {
            withContext(Dispatchers.IO) { newThumbnailer.close() }
            throw IllegalStateException("This PDF has no pages")
        }
        releaseCurrent()
        thumbnailer = newThumbnailer
        workingFile = file
        _state.update {
            it.copy(
                docId = it.docId + 1,
                kind = RotateKind.PDF,
                fileName = name,
                pageCount = newThumbnailer.pageCount,
                rotations = List(newThumbnailer.pageCount) { 0 },
                selected = emptySet(),
                image = null,
                imageRotation = 0,
                isLoading = false
            )
        }
    }

    private suspend fun loadImage(uri: Uri, name: String, mime: String) {
        val preview = processor.decodeImage(uri, maxSide = 1600)
        releaseCurrent()
        imageUri = uri
        imageMime = mime
        _state.update {
            it.copy(
                docId = it.docId + 1,
                kind = RotateKind.IMAGE,
                fileName = name,
                pageCount = 0,
                rotations = emptyList(),
                selected = emptySet(),
                image = preview,
                imageRotation = 0,
                isLoading = false
            )
        }
    }

    private suspend fun releaseCurrent() {
        val old = thumbnailer
        val oldFile = workingFile
        thumbnailer = null
        workingFile = null
        imageUri = null
        withContext(Dispatchers.IO) {
            old?.close()
            oldFile?.delete()
        }
    }

    suspend fun thumbnail(index: Int): Bitmap? =
        try { thumbnailer?.thumbnail(index) } catch (e: Exception) { null }

    // ───── selection (PDF) ─────
    fun toggleSelect(index: Int) = _state.update {
        it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
    }

    fun selectAll() = _state.update { it.copy(selected = it.rotations.indices.toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    // ───── rotation ─────
    /** Rotates selected pages (or ALL pages when nothing is selected) / the image. */
    fun rotate(degrees: Int) = _state.update { s ->
        when (s.kind) {
            RotateKind.PDF -> {
                val targets = if (s.selected.isEmpty()) s.rotations.indices.toSet() else s.selected
                s.copy(rotations = s.rotations.mapIndexed { i, r -> if (i in targets) (r + degrees) % 360 else r })
            }
            RotateKind.IMAGE -> s.copy(imageRotation = (s.imageRotation + degrees) % 360)
            null -> s
        }
    }

    fun resetRotation() = _state.update {
        it.copy(rotations = it.rotations.map { 0 }, imageRotation = 0)
    }

    // ───── export ─────
    fun save() {
        val s = _state.value
        if (s.isSaving || !s.isModified) return
        val cache = getApplication<Application>().cacheDir

        saveJob = viewModelScope.launch {
            val base = (s.fileName ?: "file").substringBeforeLast('.', s.fileName ?: "file")
            val isPdf = s.kind == RotateKind.PDF
            val mime = if (isPdf) "application/pdf" else if (imageMime == "image/png") "image/png" else "image/jpeg"
            val ext = if (isPdf) "pdf" else if (mime == "image/png") "png" else "jpg"
            val out = File(cache, "rotate_output.$ext")
            val displayName = "${base}_rotated.$ext"

            _state.update {
                it.copy(isSaving = true, progress = 0f, error = null,
                    progressLabel = if (isPdf) "Rotating pages…" else "Rotating image…")
            }
            try {
                if (isPdf) {
                    val source = workingFile ?: return@launch
                    processor.rotatePdf(source, s.rotations, out) { p ->
                        _state.update { it.copy(progress = p * 0.9f) }
                    }
                } else {
                    val uri = imageUri ?: return@launch
                    _state.update { it.copy(progress = 0.3f) }
                    processor.rotateImage(uri, s.imageRotation, out, mime)
                    _state.update { it.copy(progress = 0.85f) }
                }

                _state.update { it.copy(progress = 0.95f, progressLabel = "Saving file…") }
                val uri = sink.save(out, displayName, mime)
                _state.update {
                    it.copy(isSaving = false, progress = 1f, result = RotateResult(uri, displayName, mime))
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isSaving = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.toUserMessage()) }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(isSaving = false, error = "Image is too large to process on this device") }
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
            ?: "file"

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