package com.example.pdftoolkit.toword

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.reorder.MediaStoreOutputSink
import com.example.pdftoolkit.reorder.PdfOutputSink
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

data class ToWordResult(val uri: Uri, val displayName: String, val stats: ConversionStats)

data class ToWordUiState(
    val fileName: String? = null,
    val pageCount: Int = 0,
    val isLoading: Boolean = false,
    val isConverting: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val unsupportedMessage: String? = null,   // scanned / image-only PDF
    val error: String? = null,
    val result: ToWordResult? = null
) {
    val hasFile get() = fileName != null
}

class PdfToWordViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = PdfToWordProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(ToWordUiState())
    val state: StateFlow<ToWordUiState> = _state.asStateFlow()

    private var workingFile: File? = null
    private var job: Job? = null

    fun onPdfPicked(uri: Uri) {
        job?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, unsupportedMessage = null, result = null) }
            try {
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                val file = processor.copyToCache(uri)
                val pages = processor.pageCount(file)
                if (pages == 0) throw IllegalStateException("This PDF has no pages")

                val old = workingFile
                workingFile = file
                withContext(Dispatchers.IO) { old?.delete() }

                _state.update {
                    it.copy(fileName = name, pageCount = pages, isLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun convert() {
        val source = workingFile ?: return
        val s = _state.value
        if (s.isConverting) return

        job = viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "pdf2word_output.docx")
            _state.update {
                it.copy(isConverting = true, progress = 0f, progressLabel = "Starting…",
                    error = null, unsupportedMessage = null, result = null)
            }
            try {
                val stats = processor.convert(source, out) { p, label ->
                    _state.update { it.copy(progress = p * 0.95f, progressLabel = label) }
                }
                _state.update { it.copy(progress = 0.97f, progressLabel = "Saving file…") }

                val base = (s.fileName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
                val name = "$base.docx"
                val uri = sink.save(out, name, DOCX_MIME)

                _state.update {
                    it.copy(isConverting = false, progress = 1f, result = ToWordResult(uri, name, stats))
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isConverting = false) }
                throw e
            } catch (e: NoTextException) {
                _state.update { it.copy(isConverting = false, unsupportedMessage = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(isConverting = false, error = e.toUserMessage()) }
            } catch (e: OutOfMemoryError) {
                _state.update {
                    it.copy(isConverting = false, error = "This PDF is too large to convert on this device")
                }
            } finally {
                out.delete()
            }
        }
    }

    fun cancelConversion() {
        job?.cancel()
    }

    /** Back to the "choose a PDF" state. */
    fun reset() {
        job?.cancel()
        val old = workingFile
        workingFile = null
        old?.delete()
        _state.value = ToWordUiState()
    }

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
        job?.cancel()
        workingFile?.delete()
        super.onCleared()
    }
}