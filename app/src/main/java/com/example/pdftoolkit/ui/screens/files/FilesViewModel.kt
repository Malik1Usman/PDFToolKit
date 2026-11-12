package com.example.pdftoolkit.ui.screens.files

import android.app.Application
import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.core.db.HistoryEntity
import com.example.pdftoolkit.core.db.HistoryRepository
import com.example.pdftoolkit.core.pdf.OutputActions
import com.example.pdftoolkit.core.pdf.OutputStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FileSort {
    MODIFIED_NEWEST,
    MODIFIED_OLDEST,
    NAME_AZ,
    NAME_ZA,
    SIZE_LARGEST,
    SIZE_SMALLEST
}

data class AppFileItem(
    val id: String,
    val name: String,
    val size: String,
    val sizeBytes: Long,
    val pages: Int,
    val date: String,
    val path: String,
    val isScan: Boolean,
    val tag: String? = null,
    val isStarred: Boolean = false,
    val createdAtMillis: Long = System.currentTimeMillis()
)

data class FilesUiState(
    val files: List<AppFileItem> = emptyList(),
    val filteredFiles: List<AppFileItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: FileFilter = FileFilter.ALL,
    val selectedSort: FileSort = FileSort.MODIFIED_NEWEST,
    val isListView: Boolean = true,
    val isSelectionMode: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val totalFilesCount: Int = 0,
    val pdfsCount: Int = 0,
    val scansCount: Int = 0,
    val processedCount: Int = 0,
    val starredCount: Int = 0,
    val storagePath: String = "PDFToolKit/outputs",
    val freeStorageFormatted: String = "0 GB",
    val totalOutputsSizeMb: Float = 0f
)

class FilesViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = HistoryRepository(app)
    private val store = OutputStore(app)
    private val prefs = app.getSharedPreferences("pdf_toolkit_files_prefs", Context.MODE_PRIVATE)

    private val searchQuery = MutableStateFlow("")
    private val selectedFilter = MutableStateFlow(FileFilter.ALL)
    private val selectedSort = MutableStateFlow(FileSort.MODIFIED_NEWEST)
    private val isListView = MutableStateFlow(true)
    private val isSelectionMode = MutableStateFlow(false)
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())
    private val starredIds = MutableStateFlow(loadStarredIds())

    private val rawFilesFlow = combine(repository.allHistory, starredIds) { historyList, starredSet ->
        mapHistoryToFiles(historyList, starredSet)
    }

    private val filterAndSortFlow = combine(
        rawFilesFlow,
        searchQuery,
        selectedFilter,
        selectedSort
    ) { files, query, filter, sort ->
        val filtered = files
            .filter { item ->
                when (filter) {
                    FileFilter.ALL -> true
                    FileFilter.PDFS -> !item.isScan
                    FileFilter.IMAGES -> item.isScan
                    FileFilter.PROCESSED -> item.tag != null
                    FileFilter.STARRED -> item.isStarred
                }
            }
            .filter { item ->
                if (query.isBlank()) true
                else item.name.contains(query, ignoreCase = true)
            }

        val sorted = when (sort) {
            FileSort.MODIFIED_NEWEST -> filtered.sortedByDescending { it.createdAtMillis }
            FileSort.MODIFIED_OLDEST -> filtered.sortedBy { it.createdAtMillis }
            FileSort.NAME_AZ -> filtered.sortedBy { it.name.lowercase(Locale.getDefault()) }
            FileSort.NAME_ZA -> filtered.sortedByDescending { it.name.lowercase(Locale.getDefault()) }
            FileSort.SIZE_LARGEST -> filtered.sortedByDescending { it.sizeBytes }
            FileSort.SIZE_SMALLEST -> filtered.sortedBy { it.sizeBytes }
        }

        Triple(files, filtered, sorted)
    }

    val uiState: StateFlow<FilesUiState> = combine(
        filterAndSortFlow,
        searchQuery,
        selectedFilter,
        selectedSort,
        isListView
    ) { (allFiles, _, sorted), query, filter, sort, listMode ->
        val pdfsCount = allFiles.count { !it.isScan }
        val scansCount = allFiles.count { it.isScan }
        val processedCount = allFiles.count { it.tag != null }
        val starredCount = allFiles.count { it.isStarred }

        val freeStorage = getFreeStorageFormatted()
        val totalOutputsSize = allFiles.sumOf { it.sizeBytes } / (1024f * 1024f)

        FilesUiState(
            files = allFiles,
            filteredFiles = sorted,
            searchQuery = query,
            selectedFilter = filter,
            selectedSort = sort,
            isListView = listMode,
            isSelectionMode = isSelectionMode.value,
            selectedIds = selectedIds.value,
            totalFilesCount = allFiles.size,
            pdfsCount = pdfsCount,
            scansCount = scansCount,
            processedCount = processedCount,
            starredCount = starredCount,
            storagePath = getStoragePathDisplay(),
            freeStorageFormatted = freeStorage,
            totalOutputsSizeMb = totalOutputsSize
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FilesUiState()
    )

    init {
        viewModelScope.launch {
            repository.syncOutputStoreRecords()
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    fun onFilterSelected(filter: FileFilter) {
        selectedFilter.value = filter
    }

    fun onSortSelected(sort: FileSort) {
        selectedSort.value = sort
    }

    fun toggleViewMode(isList: Boolean) {
        isListView.value = isList
    }

    fun toggleSelectionMode() {
        isSelectionMode.value = !isSelectionMode.value
        if (!isSelectionMode.value) {
            selectedIds.value = emptySet()
        }
    }

    fun toggleFileSelection(fileId: String) {
        val current = selectedIds.value.toMutableSet()
        if (current.contains(fileId)) current.remove(fileId)
        else current.add(fileId)
        selectedIds.value = current
    }

    fun toggleStar(fileId: String) {
        val current = starredIds.value.toMutableSet()
        if (current.contains(fileId)) current.remove(fileId)
        else current.add(fileId)
        starredIds.value = current
        saveStarredIds(current)
    }

    fun deleteFile(fileItem: AppFileItem) {
        viewModelScope.launch {
            val file = File(fileItem.path)
            if (file.exists()) {
                file.delete()
            }
            val historyItem = repository.allHistory.stateIn(viewModelScope).value
                .find { it.filePath == fileItem.path || it.id.toString() == fileItem.id }
            if (historyItem != null) {
                repository.deleteHistory(historyItem)
            }
            store.delete(fileItem.id)
        }
    }

    fun deleteSelectedFiles() {
        val selected = selectedIds.value
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val allItems = uiState.value.files.filter { it.id in selected }
            allItems.forEach { deleteFile(it) }
            selectedIds.value = emptySet()
            isSelectionMode.value = false
        }
    }

    fun shareFile(context: Context, fileItem: AppFileItem) {
        if (fileItem.path.isNotEmpty()) {
            OutputActions.sharePath(context, fileItem.path)
        }
    }

    private fun mapHistoryToFiles(historyList: List<HistoryEntity>, starredSet: Set<String>): List<AppFileItem> {
        val outputsDir = File(getApplication<Application>().filesDir, "outputs")
        val diskFiles = outputsDir.listFiles()?.toList() ?: emptyList()

        val result = mutableListOf<AppFileItem>()

        historyList.filter { !it.isError && it.filePath.isNotEmpty() }.forEach { entity ->
            val file = File(entity.filePath)
            val isScan = entity.toolType == "SCAN" || entity.fileName.endsWith(".jpg", ignoreCase = true) || entity.fileName.endsWith(".png", ignoreCase = true)
            val tag = mapToolTypeToTag(entity.toolType)
            val fileId = entity.id.toString()

            result.add(
                AppFileItem(
                    id = fileId,
                    name = entity.fileName,
                    size = entity.fileSize,
                    sizeBytes = if (file.exists()) file.length() else 0L,
                    pages = entity.pageCount.coerceAtLeast(1),
                    date = formatDate(entity.createdAtMillis),
                    path = entity.filePath,
                    isScan = isScan,
                    tag = tag,
                    isStarred = starredSet.contains(fileId) || starredSet.contains(entity.filePath),
                    createdAtMillis = entity.createdAtMillis
                )
            )
        }

        diskFiles.forEach { file ->
            if (result.none { it.path == file.absolutePath }) {
                val isScan = file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".png", ignoreCase = true)
                val fileId = file.name
                val sizeMb = file.length() / (1024f * 1024f)
                val sizeFormatted = if (sizeMb < 0.1f) "${file.length() / 1024} KB" else String.format(Locale.getDefault(), "%.1f MB", sizeMb)
                result.add(
                    AppFileItem(
                        id = fileId,
                        name = file.name,
                        size = sizeFormatted,
                        sizeBytes = file.length(),
                        pages = 1,
                        date = formatDate(file.lastModified()),
                        path = file.absolutePath,
                        isScan = isScan,
                        tag = if (isScan) "SCAN" else null,
                        isStarred = starredSet.contains(fileId) || starredSet.contains(file.absolutePath),
                        createdAtMillis = file.lastModified()
                    )
                )
            }
        }

        return result
    }

    private fun mapToolTypeToTag(toolType: String): String? {
        return when (toolType) {
            "COMPRESS" -> "COMPRESSED"
            "MERGE" -> "MERGED"
            "SPLIT" -> "SPLIT"
            "PHOTOS_TO_PDF", "SCAN" -> "FROM_IMAGES"
            "WATERMARK" -> "WATERMARKED"
            "PROTECT" -> "PROTECTED"
            "TO_WORD" -> "WORD"
            "EXTRACT_TEXT" -> "TEXT"
            else -> null
        }
    }

    private fun formatDate(timeMillis: Long): String {
        val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
        return sdf.format(Date(timeMillis))
    }

    private fun getStoragePathDisplay(): String {
        val filesDir = getApplication<Application>().filesDir
        return filesDir.name + "/outputs"
    }

    private fun getFreeStorageFormatted(): String {
        return try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            val freeGb = freeBytes / (1024f * 1024f * 1024f)
            String.format(Locale.getDefault(), "%.1f GB", freeGb)
        } catch (_: Exception) {
            "42.6 GB"
        }
    }

    private fun loadStarredIds(): Set<String> {
        return prefs.getStringSet("starred_files", emptySet()) ?: emptySet()
    }

    private fun saveStarredIds(ids: Set<String>) {
        prefs.edit().putStringSet("starred_files", ids).apply()
    }
}
