package com.example.pdftoolkit.core.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history_records ORDER BY createdAtMillis DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history_records WHERE id = :id LIMIT 1")
    suspend fun getHistoryById(id: Long): HistoryEntity?

    @Query("SELECT * FROM history_records WHERE filePath = :path LIMIT 1")
    suspend fun getHistoryByPath(path: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: HistoryEntity): Long

    @Delete
    suspend fun deleteHistory(item: HistoryEntity)

    @Query("DELETE FROM history_records WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history_records")
    suspend fun clearAllHistory()
}
