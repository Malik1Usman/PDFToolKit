package com.example.pdftoolkit.ui.screens.mergepdfscreen

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.MergeInput
import com.example.pdftoolkit.core.pdf.MergeSettings
import com.example.pdftoolkit.core.pdf.SourceReader
import com.example.pdftoolkit.core.pdf.OutputRecord
import com.example.pdftoolkit.core.pdf.OutputStore
import com.example.pdftoolkit.core.pdf.ToolType
import com.example.pdftoolkit.ui.screens.components.ToolViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MergeViewModel(app: Application) : ToolViewModel(app) {

    /** MergePdfScreen(files = vm.files) - screen isi list ko drag / sort / remove karti hai. */
    val files = mutableStateListOf<MergeFile>()
    private var nextId = 1

    fun addFiles(uris: List<Uri>) {
        viewModelScope.launch {
            val infos = withContext(Dispatchers.IO) {
                uris.map { SourceReader.read(getApplication(), it) }
            }
            infos.forEach { info ->
                if (info.pageCount <= 0) {
                    fail(getApplication<Application>().getString(R.string.err_unreadable_named, info.displayName))
                } else {
                    files.add(
                        MergeFile(
                            id = nextId++,
                            name = info.displayName,
                            sizeMb = info.sizeBytes / 1_048_576f,
                            totalPages = info.pageCount,
                            tag = MergeDocTag.PDF,
                            modifiedMillis = System.currentTimeMillis(),
                            uri = info.uri
                        )
                    )
                }
            }
        }
    }

    fun merge(options: MergeOptions) {
        launchTool(str(R.string.tool_working_merge)) { report ->
            val inputs = options.files.mapNotNull { f ->
                f.uri?.let { uri ->
                    MergeInput(
                        uri = uri,
                        pageIndices = f.pageRange?.map { it - 1 },     // 1-based range -> 0-based
                        title = f.name.removeSuffix(".pdf")
                    )
                }
            }
            val result = engine.merge(
                inputs = inputs,
                settings = MergeSettings(
                    removeBlankPages = options.removeBlankPages,
                    normalizeToA4 = options.normalizeToA4,
                    generateBookmarks = options.generateBookmarks
                ),
                onProgress = report
            )
            store.commit(
                temp = result.file,
                displayName = OutputStore.fileName(options.outputName, "pdf"),
                tool = ToolType.MERGE,
                pages = result.pageCount,
                mime = result.mimeType
            )
        }
    }
}