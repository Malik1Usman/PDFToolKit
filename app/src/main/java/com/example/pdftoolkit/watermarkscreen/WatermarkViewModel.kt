package com.example.pdftoolkit.watermark

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.reorder.MediaStoreOutputSink
import com.example.pdftoolkit.reorder.PdfOutputSink
import com.example.pdftoolkit.reorder.PdfPageThumbnailer
import com.example.pdftoolkit.watermarkscreen.PageSize
import com.example.pdftoolkit.watermarkscreen.WatermarkParams
import com.example.pdftoolkit.watermarkscreen.WatermarkPosition
import com.example.pdftoolkit.watermarkscreen.WatermarkProcessor
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

enum class WatermarkScope { ALL, SELECTED }

data class WatermarkResult(val uri: Uri, val displayName: String, val pagesStamped: Int)

data class WatermarkUiState(
    val docId: Int = 0,                         // changes per opened PDF (thumbnail cache key)
    val fileName: String? = null,
    val pageCount: Int = 0,
    val pageSizes: List<PageSize> = emptyList(),
    // watermark settings
    val text: String = "",
    val fontSize: Float = 48f,                  // pt
    val colorArgb: Int = DEFAULT_COLOR,
    val opacity: Float = 0.3f,                  // 0.05 .. 1
    val angle: Float = 0f,                      // degrees, counter-clockwise
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val scope: WatermarkScope = WatermarkScope.ALL,
    val selected: Set<Int> = emptySet(),
    val submitted: Boolean = false,
    // status
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: WatermarkResult? = null
) {
    val hasFile get() = fileName != null && pageCount > 0

    val textIssue: String? get() = if (submitted && text.isBlank()) "Enter the watermark text" else null
    val pagesIssue: String?
        get() = if (submitted && scope == WatermarkScope.SELECTED && selected.isEmpty())
            "Choose at least one page" else null

    val targetCount get() = if (scope == WatermarkScope.ALL) pageCount else selected.size

    /** Page shown in the preview: first selected page, otherwise page 1. */
    val previewIndex get() =
        if (scope == WatermarkScope.SELECTED && selected.isNotEmpty()) (selected.minOrNull() ?: 0) else 0

    companion object {
        const val DEFAULT_COLOR = 0xFF808080.toInt()
        const val MAX_TEXT = 60
    }
}

class WatermarkViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = WatermarkProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(WatermarkUiState())
    val state: StateFlow<WatermarkUiState> = _state.asStateFlow()

    private var thumbnailer: PdfPageThumbnailer? = null
    private var workingFile: File? = null
    private var job: Job? = null

    fun onPdfPicked(uri: Uri) {
        job?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, result = null) }
            try {
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                val file = processor.copyToCache(uri)
                val sizes = processor.inspect(file)
                if (sizes.isEmpty()) throw IllegalStateException("This PDF has no pages")
                val newThumbnailer = withContext(Dispatchers.IO) { PdfPageThumbnailer(file) }

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
                        pageCount = sizes.size,
                        pageSizes = sizes,
                        selected = emptySet(),
                        scope = WatermarkScope.ALL,
                        submitted = false,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Could not open this PDF") }
            }
        }
    }

    suspend fun thumbnail(index: Int, widthPx: Int = 360): Bitmap? =
        try { thumbnailer?.thumbnail(index, widthPx) } catch (e: Exception) { null }

    // ───── settings ─────
    fun setText(value: String) = _state.update { it.copy(text = value.take(WatermarkUiState.MAX_TEXT)) }
    fun setFontSize(value: Float) = _state.update { it.copy(fontSize = value) }
    fun setColor(argb: Int) = _state.update { it.copy(colorArgb = argb) }
    fun setOpacity(value: Float) = _state.update { it.copy(opacity = value) }
    fun setAngle(value: Float) = _state.update { it.copy(angle = value) }

    /** Diagonal presets the angle to 45°; the other positions reset it to 0°. You can still adjust it afterwards. */
    fun setPosition(position: WatermarkPosition) = _state.update {
        it.copy(position = position, angle = if (position == WatermarkPosition.DIAGONAL) 45f else 0f)
    }

    fun setScope(scope: WatermarkScope) = _state.update { it.copy(scope = scope) }

    fun toggleSelect(index: Int) = _state.update {
        it.copy(selected = if (index in it.selected) it.selected - index else it.selected + index)
    }

    fun selectAll() = _state.update { it.copy(selected = (0 until it.pageCount).toSet()) }
    fun clearSelection() = _state.update { it.copy(selected = emptySet()) }

    // ───── apply ─────
    fun apply() {
        _state.update { it.copy(submitted = true) }
        val s = _state.value
        val source = workingFile ?: return
        if (s.isSaving || s.text.isBlank()) return
        if (s.scope == WatermarkScope.SELECTED && s.selected.isEmpty()) return

        job = viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "watermark_output.pdf")
            _state.update {
                it.copy(isSaving = true, progress = 0f, progressLabel = "Preparing…", error = null)
            }
            try {
                val params = WatermarkParams(
                    text = s.text.trim(),
                    fontSizePt = s.fontSize,
                    colorArgb = s.colorArgb,
                    opacity = s.opacity,
                    angleDeg = s.angle,
                    position = s.position
                )
                processor.addWatermark(
                    source, out, params,
                    pages = if (s.scope == WatermarkScope.ALL) null else s.selected
                ) { p, label -> _state.update { it.copy(progress = p, progressLabel = label) } }

                _state.update { it.copy(progress = 0.96f, progressLabel = "Saving file…") }
                val base = (s.fileName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
                val name = "${base}_watermarked.pdf"
                val uri = sink.save(out, name)

                _state.update {
                    it.copy(
                        isSaving = false,
                        progress = 1f,
                        result = WatermarkResult(uri, name, s.targetCount)
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isSaving = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = e.message ?: "Could not add the watermark") }
            } catch (e: OutOfMemoryError) {
                _state.update { it.copy(isSaving = false, error = "This PDF is too large to process on this device") }
            } finally {
                out.delete()
            }
        }
    }

    fun cancelApply() {
        job?.cancel()
    }

    /** Leave the success screen but keep the file and settings, so the user can tweak and apply again. */
    fun backToEditing() = _state.update { it.copy(result = null, submitted = false) }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun queryDisplayName(uri: Uri): String =
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "document.pdf"

    override fun onCleared() {
        job?.cancel()
        thumbnailer?.close()
        workingFile?.delete()
        super.onCleared()
    }
}