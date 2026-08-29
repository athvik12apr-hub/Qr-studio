package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "qr_history")
data class QrHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val historyType: String, // "CREATED" or "SCANNED"
    val qrType: String,      // "TEXT", "URL", "WIFI", "CONTACT", etc.
    val title: String,
    val content: String,
    val summary: String,
    val customizationJson: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false
)
