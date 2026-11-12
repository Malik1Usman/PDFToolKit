package com.example.pdftoolkit.core.db

import android.content.Context
import com.example.pdftoolkit.core.pdf.OutputRecord
import com.example.pdftoolkit.core.pdf.OutputStore
import com.example.pdftoolkit.core.pdf.ToolType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val dao = db.historyDao()
    private val store = OutputStore(context)

    val allHistory: Flow<List<HistoryEntity>> = dao.getAllHistory()

    suspend fun getHistoryById(id: Long): HistoryEntity? = withContext(Dispatchers.IO) {
        dao.getHistoryById(id)
    }

    suspend fun recordSuccess(
        record: OutputRecord
    ): Long = withContext(Dispatchers.IO) {
        val title = when (record.tool) {
            ToolType.MERGE -> "Merged PDF"
            ToolType.COMPRESS -> "Compressed PDF"
            ToolType.SPLIT -> "Split PDF"
            ToolType.ROTATE -> "Rotated PDF"
            ToolType.REORDER -> "Reordered PDF"
            ToolType.DELETE_PAGES -> "Deleted Pages PDF"
            ToolType.WATERMARK -> "Watermarked PDF"
            ToolType.PROTECT -> "Password Protected PDF"
            ToolType.EXTRACT_TEXT -> "Extracted Text"
            ToolType.TO_WORD -> "PDF to Word"
            ToolType.SCAN -> "Scanned Document"
            else -> "PDF Processed"
        }

        val sizeMb = (record.sizeBytes / 1_048_576f).let {
            if (it < 0.1f) String.format(Locale.getDefault(), "%.1f KB", record.sizeBytes / 1024f)
            else String.format(Locale.getDefault(), "%.1f MB", it)
        }

        val entity = HistoryEntity(
            title = title,
            timeFormatted = formatTime(record.createdAt),
            status = "COMPLETED",
            groupName = calculateGroup(record.createdAt),
            toolType = record.tool.name,
            fileName = record.name,
            filePath = record.path,
            fileSize = sizeMb,
            pageCount = record.pages,
            isError = false,
            createdAtMillis = record.createdAt
        )
        dao.insertHistory(entity)
    }

    suspend fun recordFailure(
        tool: ToolType,
        fileName: String,
        errorMessage: String
    ): Long = withContext(Dispatchers.IO) {
        val title = when (tool) {
            ToolType.MERGE -> "Merge Failed"
            ToolType.COMPRESS -> "Compression Failed"
            ToolType.SPLIT -> "Split Failed"
            ToolType.PROTECT -> "Password Failed"
            else -> "${tool.name} Failed"
        }

        val entity = HistoryEntity(
            title = title,
            timeFormatted = formatTime(System.currentTimeMillis()),
            status = "FAILED",
            groupName = "TODAY",
            toolType = tool.name,
            fileName = fileName,
            filePath = "",
            fileSize = "0 KB",
            pageCount = 0,
            isError = true,
            errorMessage = errorMessage,
            createdAtMillis = System.currentTimeMillis()
        )
        dao.insertHistory(entity)
    }

    suspend fun deleteHistory(entity: HistoryEntity) = withContext(Dispatchers.IO) {
        dao.deleteHistory(entity)
        if (entity.filePath.isNotEmpty()) {
            val file = File(entity.filePath)
            if (file.exists()) file.delete()
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAllHistory()
        store.clearAll()
    }

    /** Sync existing OutputStore records into Room DB if not already present. */
    suspend fun syncOutputStoreRecords() = withContext(Dispatchers.IO) {
        val records = store.records()
        records.forEach { record ->
            val existing = dao.getHistoryByPath(record.path)
            if (existing == null) {
                recordSuccess(record)
            }
        }
    }

    private fun formatTime(timeMillis: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return "Today at ${sdf.format(Date(timeMillis))}"
    }

    private fun calculateGroup(timeMillis: Long): String {
        val now = System.currentTimeMillis()
        val diffHours = (now - timeMillis) / (1000 * 60 * 60)
        return when {
            diffHours < 24 -> "TODAY"
            diffHours < 48 -> "YESTERDAY"
            else -> "EARLIER"
        }
    }
}
