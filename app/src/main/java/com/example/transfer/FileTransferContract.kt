package com.example.transfer

/**
 * Architecture contract for V2 Offline Phone-to-Phone File Transfer.
 * Modular architecture prepared for Wi-Fi Direct / Local Hotspot socket transfer.
 */
interface FileTransferSessionManager {
    fun createTransferSession(fileName: String, fileSizeBytes: Long, mimeType: String): TransferSessionPayload
    fun parseTransferSession(qrContent: String): TransferSessionPayload?
}

data class TransferSessionPayload(
    val sessionId: String,
    val serverIp: String,
    val serverPort: Int,
    val fileName: String,
    val fileSizeBytes: Long,
    val checksum: String,
    val protocolVersion: Int = 1
)
