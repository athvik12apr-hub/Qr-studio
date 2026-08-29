package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QrHistoryDao {

    @Query("SELECT * FROM qr_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<QrHistoryEntity>>

    @Query("SELECT * FROM qr_history WHERE historyType = :type ORDER BY timestamp DESC")
    fun getHistoryByType(type: String): Flow<List<QrHistoryEntity>>

    @Query("SELECT * FROM qr_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<QrHistoryEntity>>

    @Query("SELECT * FROM qr_history WHERE id = :id")
    suspend fun getHistoryById(id: Long): QrHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: QrHistoryEntity): Long

    @Update
    suspend fun updateHistory(item: QrHistoryEntity)

    @Query("UPDATE qr_history SET title = :newTitle WHERE id = :id")
    suspend fun updateTitle(id: Long, newTitle: String)

    @Query("UPDATE qr_history SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query("DELETE FROM qr_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM qr_history WHERE historyType = :type")
    suspend fun clearHistoryByType(type: String)

    @Query("DELETE FROM qr_history")
    suspend fun clearAll()
}
