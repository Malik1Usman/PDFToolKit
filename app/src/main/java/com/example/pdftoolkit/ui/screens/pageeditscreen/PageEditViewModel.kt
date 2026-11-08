package com.example.pdftoolkit.ui.screens.pageeditscreen

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

class PageEditViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<SourceInfo?>(null)
    val source: StateFlow<SourceInfo?> = _source.asStateFlow()

    fun setSource(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { SourceReader.read(getApplication(), uri) }
            if (info.pageCount <= 0) fail(str(R.string.err_unreadable)) else _source.value = info
        }
    }

    fun rotate(outputName: String) {
        val src = _source.value ?: return
        launchTool(str(R.string.tool_working_scan)) { report ->
            report(0.5f)
            val rotations = (0 until src.pageCount).associateWith { 90 }
            val result = engine.rotatePages(src.uri, rotations)
            report(1f)
            store.commit(result.file, OutputStore.fileName(outputName, "pdf"), ToolType.SCAN, result.pageCount, result.mimeType)
        }
    }
}
