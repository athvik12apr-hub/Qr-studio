package com.example.data.local

import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val dao: QrHistoryDao) {

    val allHistory: Flow<List<QrHistoryEntity>> = dao.getAllHistory()
    val createdHistory: Flow<List<QrHistoryEntity>> = dao.getHistoryByType("CREATED")
    val scannedHistory: Flow<List<QrHistoryEntity>> = dao.getHistoryByType("SCANNED")

    fun getRecentHistory(limit: Int = 5): Flow<List<QrHistoryEntity>> = dao.getRecentHistory(limit)

    suspend fun insertHistory(item: QrHistoryEntity): Long = dao.insertHistory(item)

    suspend fun getById(id: Long): QrHistoryEntity? = dao.getHistoryById(id)

    suspend fun updateTitle(id: Long, newTitle: String) = dao.updateTitle(id, newTitle)

    suspend fun updateFavorite(id: Long, isFavorite: Boolean) = dao.updateFavorite(id, isFavorite)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun clearCreated() = dao.clearHistoryByType("CREATED")

    suspend fun clearScanned() = dao.clearHistoryByType("SCANNED")

    suspend fun clearAll() = dao.clearAll()
}
