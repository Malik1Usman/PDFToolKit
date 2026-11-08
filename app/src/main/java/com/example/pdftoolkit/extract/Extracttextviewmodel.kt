package com.example.pdftoolkit.extract

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

data class ExtractResult(val uri: Uri, val displayName: String)

data class ExtractUiState(
    val fileName: String? = null,
    val pageTexts: List<String> = emptyList(),   // one entry per page, blank when no text
    val wordCount: Int = 0,
    val charCount: Int = 0,
    val pagesWithoutText: Int = 0,
    val isExtracting: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
    val result: ExtractResult? = null
) {
    val hasFile get() = fileName != null && pageTexts.isNotEmpty()
    /** Every page is empty → almost certainly a scanned / image-only PDF (needs OCR). */
    val isScanned get() = hasFile && pagesWithoutText == pageTexts.size
    val hasText get() = hasFile && !isScanned
    val partiallyScanned get() = hasText && pagesWithoutText > 0
}

class ExtractTextViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = ExtractTextProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(ExtractUiState())
    val state: StateFlow<ExtractUiState> = _state.asStateFlow()

    private var extractJob: Job? = null
    private var saveJob: Job? = null

    fun onPdfPicked(uri: Uri) {
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            _state.update {
                it.copy(isExtracting = true, progress = 0f, progressLabel = "Opening PDF…", error = null, result = null)
            }
            var file: File? = null
            try {
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                file = processor.copyToCache(uri)
                val pages = processor.extract(file) { done, total ->
                    _state.update {
                        it.copy(progress = done.toFloat() / total, progressLabel = "Extracting page $done of $total")
                    }
                }

                val (words, chars, empty) = withContext(Dispatchers.Default) {
                    var w = 0
                    var c = 0
                    var e = 0
                    pages.forEach { p ->
                        if (p.hasRealText()) {
                            w += p.split(Regex("\\s+")).count { it.isNotBlank() }
                            c += p.length
                        } else e++
                    }
                    Triple(w, c, e)
                }

                _state.update {
                    it.copy(
                        fileName = name,
                        pageTexts = pages,
                        wordCount = words,
                        charCount = chars,
                        pagesWithoutText = empty,
                        isExtracting = false
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isExtracting = false) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isExtracting = false, error = e.toUserMessage()) }
            } finally {
                file?.delete()
            }
        }
    }

    fun cancelExtraction() {
        extractJob?.cancel()
    }

    /** Text for the clipboard. */
    fun fullText(): String = buildFullText(_state.value.pageTexts)

    fun saveTxt() {
        val s = _state.value
        if (!s.hasText || s.isSaving) return

        saveJob = viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "extract_output.txt")
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                processor.writeTxt(s.pageTexts, out)
                val base = (s.fileName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
                val name = "${base}_text.txt"
                val uri = sink.save(out, name, "text/plain")
                _state.update { it.copy(isSaving = false, result = ExtractResult(uri, name)) }
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
        extractJob?.cancel()
        saveJob?.cancel()
        super.onCleared()
    }
}