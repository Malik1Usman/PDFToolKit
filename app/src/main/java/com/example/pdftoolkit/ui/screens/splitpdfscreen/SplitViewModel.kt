package com.example.pdftoolkit.ui.screens.splitpdfscreen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.SourceInfo
import com.example.pdftoolkit.core.pdf.SourceReader
import com.example.pdftoolkit.core.pdf.OutputRecord
import com.example.pdftoolkit.core.pdf.OutputStore
import com.example.pdftoolkit.core.pdf.ToolType
import com.example.pdftoolkit.ui.screens.components.ToolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitViewModel(app: Application) : ToolViewModel(app) {

    private val _source = MutableStateFlow<SourceInfo?>(null)
    val source: StateFlow<SourceInfo?> = _source.asStateFlow()

    fun setSource(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { SourceReader.read(getApplication(), uri) }
            if (info.pageCount <= 0) {
                fail(str(R.string.err_unreadable))
            } else {
                _source.value = info
            }
        }
    }

    fun split(options: SplitOptions) {
        val src = _source.value ?: return
        launchTool(str(R.string.tool_working_split)) { report ->
            // Har group = ek output PDF
            val groups: List<List<Int>> = when {
                options.mode == SplitMode.EVERY -> options.selectedPages.map { listOf(it) }
                options.mergeIntoOnePdf -> listOf(options.selectedPages)
                options.mode == SplitMode.RANGE -> options.ranges.map { it.toList() }
                else -> options.selectedPages.map { listOf(it) }
            }
            val base = OutputStore.fileName(options.outputName, "pdf").removeSuffix(".pdf")
            val result = engine.split(src.uri, groups, base, report)
            val ext = if (groups.size == 1) "pdf" else "zip"
            store.commit(
                temp = result.file,
                displayName = "$base.$ext",
                tool = ToolType.SPLIT,
                pages = result.pageCount,
                mime = result.mimeType
            )
        }
    }
}