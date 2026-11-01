package com.example.pdftoolkit.ui.screens.compresspdfscreen

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.CompressSettings
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

class CompressViewModel(app: Application) : ToolViewModel(app) {

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

    fun compress(options: CompressOptions) {
        val src = _source.value ?: return
        launchTool(str(R.string.tool_working_compress)) { report ->
            val quality = when (options.preset) {
                CompressionPreset.EXTREME -> 0.40f
                CompressionPreset.RECOMMENDED -> 0.65f
                CompressionPreset.BASIC -> 0.82f
            }
            val result = engine.compress(
                uri = src.uri,
                settings = CompressSettings(
                    targetDpi = options.imageDpi,
                    jpegQuality = quality,
                    stripMetadata = options.stripMetadata,
                    grayscale = options.convertToGrayscale
                ),
                originalSizeBytes = src.sizeBytes,
                onProgress = report
            )
            store.commit(
                temp = result.file,
                displayName = OutputStore.fileName(options.outputName, "pdf"),
                tool = ToolType.COMPRESS,
                pages = result.pageCount,
                mime = result.mimeType
            )
        }
    }
}