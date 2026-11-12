package com.example.pdftoolkit.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history_records")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val timeFormatted: String,
    val status: String, // "COMPLETED" or "FAILED"
    val groupName: String, // "TODAY", "YESTERDAY", "EARLIER"
    val toolType: String, // "COMPRESS", "MERGE", "SPLIT", "WATERMARK", "EXTRACT_TEXT", etc.
    val fileName: String,
    val filePath: String,
    val fileSize: String,
    val pageCount: Int,
    val savedPercent: Int = 0,
    val isError: Boolean = false,
    val errorMessage: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)
