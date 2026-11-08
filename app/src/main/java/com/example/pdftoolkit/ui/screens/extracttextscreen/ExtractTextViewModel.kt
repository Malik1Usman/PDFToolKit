package com.example.pdftoolkit.ui.screens.extracttextscreen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.OutputStore
import com.example.pdftoolkit.core.pdf.SourceInfo
import com.example.pdftoolkit.core.pdf.SourceReader
import com.example.pdftoolkit.core.pdf.ToolType
import com.example.pdftoolkit.ui.screens.components.ToolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExtractTextViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<SourceInfo?>(null)
    val source: StateFlow<SourceInfo?> = _source.asStateFlow()

    fun setSource(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { SourceReader.read(getApplication(), uri) }
            if (info.pageCount <= 0) fail(str(R.string.err_unreadable)) else _source.value = info
        }
    }

    fun extract() {
        val src = _source.value ?: return
        launchTool(str(R.string.tool_working_scan)) { report ->
            report(0.3f)
            val extracted = engine.extractText(src.uri, onProgress = { report(0.3f + it * 0.4f) })
            report(0.7f)
            val result = engine.saveText(extracted.fullText)
            report(1f)
            store.commit(
                temp = result.file,
                displayName = OutputStore.fileName(src.displayName.removeSuffix(".pdf") + "_extracted", "txt"),
                tool = ToolType.EXTRACT_TEXT,
                pages = src.pageCount,
                mime = result.mimeType
            )
        }
    }
}
