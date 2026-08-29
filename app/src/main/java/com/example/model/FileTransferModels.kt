package com.example.model

import android.net.Uri

/**
 * Represents a single file selected for offline transfer.
 */
data class TransferFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val uriString: String? = null
)

/**
 * Session metadata encoded into the sender's QR code.
 * Keeps the QR payload minimal while containing all info required for receiver to connect and preview.
 */
data class TransferSessionInfo(
    val sessionId: String,
    val serverIp: String,
    val serverPort: Int,
    val authToken: String,
    val fileCount: Int,
    val totalSizeBytes: Long,
    val files: List<TransferFileInfo>,
    val protocolVersion: Int = 1
)

enum class TransferRole {
    NONE,
    SENDER,
    RECEIVER
}

enum class TransferState {
    IDLE,
    PICKING_FILES,
    PREPARING,
    WAITING_FOR_RECEIVER,
    SCANNING_QR,
    CONFIRMING_RECEIVE,
    CONNECTING,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Real-time progress update for the active transfer session.
 */
data class TransferProgress(
    val currentFileIndex: Int = 0,
    val totalFiles: Int = 0,
    val currentFileName: String = "",
    val currentFileBytes: Long = 0L,
    val currentFileTotalBytes: Long = 0L,
    val overallBytes: Long = 0L,
    val overallTotalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val remainingSeconds: Long = 0L,
    val statusMessage: String = ""
) {
    val currentFileProgress: Float
        get() = if (currentFileTotalBytes > 0) (currentFileBytes.toFloat() / currentFileTotalBytes).coerceIn(0f, 1f) else 0f

    val overallProgress: Float
        get() = if (overallTotalBytes > 0) (overallBytes.toFloat() / overallTotalBytes).coerceIn(0f, 1f) else 0f
}

/**
 * Represents a successfully received file stored on disk.
 */
data class ReceivedFileInfo(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val mimeType: String,
    val localUriString: String,
    val absolutePath: String,
    val receivedTimestamp: Long = System.currentTimeMillis()
)
