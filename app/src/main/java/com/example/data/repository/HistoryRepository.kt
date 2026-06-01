package com.example.data.repository

import com.example.data.database.HistoryDao
import com.example.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val historyDao: HistoryDao) {
    val allHistory: Flow<List<HistoryItem>> = historyDao.getAllHistory()
    val favorites: Flow<List<HistoryItem>> = historyDao.getFavorites()

    suspend fun insert(item: HistoryItem) = historyDao.insertHistoryItem(item)

    suspend fun deleteById(id: Int) = historyDao.deleteHistoryItemById(id)

    suspend fun updateFavorite(id: Int, isFavorite: Boolean) = historyDao.updateFavoriteStatus(id, isFavorite)

    suspend fun clearAll() = historyDao.clearAllHistory()
}
