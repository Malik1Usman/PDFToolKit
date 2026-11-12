package com.example.pdftoolkit.ui.screens.preview

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.core.pdf.OutputActions
import com.example.pdftoolkit.core.pdf.OutputRecord
import com.example.pdftoolkit.core.pdf.ToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class DocumentPreviewState(
    val isLoading: Boolean = true,
    val filePath: String = "",
    val fileName: String = "",
    val fileSizeFormatted: String = "",
    val pageCount: Int = 0,
    val isTxtFile: Boolean = false,
    val txtContent: String = "",
    val error: String? = null
)

class DocumentPreviewViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(DocumentPreviewState())
    val state: StateFlow<DocumentPreviewState> = _state.asStateFlow()

    private var pfd: ParcelFileDescriptor? = null
    private var pdfRenderer: PdfRenderer? = null

    fun loadDocument(pathOrUri: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            withContext(Dispatchers.IO) {
                try {
                    val file = File(pathOrUri)
                    if (!file.exists()) {
                        // Check if path is Uri string
                        val uri = Uri.parse(pathOrUri)
                        val context = getApplication<Application>()
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        if (pfd != null) {
                            this@DocumentPreviewViewModel.pfd = pfd
                            val renderer = PdfRenderer(pfd)
                            this@DocumentPreviewViewModel.pdfRenderer = renderer
                            val sizeMb = (pfd.statSize / 1_048_576f).coerceAtLeast(0.01f)
                            _state.value = DocumentPreviewState(
                                isLoading = false,
                                filePath = pathOrUri,
                                fileName = uri.lastPathSegment ?: "Document.pdf",
                                fileSizeFormatted = String.format("%.2f MB", sizeMb),
                                pageCount = renderer.pageCount,
                                isTxtFile = false
                            )
                            return@withContext
                        }
                    }

                    if (file.exists()) {
                        val name = file.name
                        val sizeMb = (file.length() / 1_048_576f).coerceAtLeast(0.01f)

                        if (name.endsWith(".txt", ignoreCase = true)) {
                            val text = file.readText()
                            _state.value = DocumentPreviewState(
                                isLoading = false,
                                filePath = file.absolutePath,
                                fileName = name,
                                fileSizeFormatted = String.format("%.2f MB", sizeMb),
                                pageCount = 1,
                                isTxtFile = true,
                                txtContent = text
                            )
                        } else {
                            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                            this@DocumentPreviewViewModel.pfd = pfd
                            val renderer = PdfRenderer(pfd)
                            this@DocumentPreviewViewModel.pdfRenderer = renderer
                            _state.value = DocumentPreviewState(
                                isLoading = false,
                                filePath = file.absolutePath,
                                fileName = name,
                                fileSizeFormatted = String.format("%.2f MB", sizeMb),
                                pageCount = renderer.pageCount,
                                isTxtFile = false
                            )
                        }
                    } else {
                        _state.value = DocumentPreviewState(
                            isLoading = false,
                            error = "File not found or unreadable"
                        )
                    }
                } catch (e: Exception) {
                    _state.value = DocumentPreviewState(
                        isLoading = false,
                        error = e.message ?: "Failed to open document preview"
                    )
                }
            }
        }
    }

    suspend fun renderPage(pageIndex: Int, targetWidth: Int = 1080): Bitmap? = withContext(Dispatchers.IO) {
        val renderer = pdfRenderer ?: return@withContext null
        if (pageIndex !in 0 until renderer.pageCount) return@withContext null

        try {
            val page = renderer.openPage(pageIndex)
            val aspect = page.height.toFloat() / page.width.toFloat()
            val targetHeight = (targetWidth * aspect).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    fun shareFile() {
        val path = _state.value.filePath
        if (path.isEmpty()) return
        val file = File(path)
        if (!file.exists()) return
        val record = OutputRecord(
            id = file.name,
            name = file.name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            pages = _state.value.pageCount,
            mime = if (_state.value.isTxtFile) "text/plain" else "application/pdf",
            tool = ToolType.MERGE,
            createdAt = file.lastModified()
        )
        OutputActions.share(getApplication(), record)
    }

    fun openExternal() {
        val path = _state.value.filePath
        if (path.isEmpty()) return
        val file = File(path)
        if (!file.exists()) return
        val record = OutputRecord(
            id = file.name,
            name = file.name,
            path = file.absolutePath,
            sizeBytes = file.length(),
            pages = _state.value.pageCount,
            mime = if (_state.value.isTxtFile) "text/plain" else "application/pdf",
            tool = ToolType.MERGE,
            createdAt = file.lastModified()
        )
        OutputActions.open(getApplication(), record)
    }

    override fun onCleared() {
        super.onCleared()
        try {
            pdfRenderer?.close()
            pfd?.close()
        } catch (_: Exception) {}
    }
}
