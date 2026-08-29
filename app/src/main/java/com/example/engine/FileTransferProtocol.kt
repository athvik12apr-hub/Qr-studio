package com.example.engine

import android.util.Base64
import com.example.model.TransferFileInfo
import com.example.model.TransferSessionInfo
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object FileTransferProtocol {
    private const val PROTOCOL_PREFIX = "QRFTS:1"
    private const val SEPARATOR = "|"
    private const val FILE_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = "*"

    /**
     * Encodes session information into a compact QR payload string.
     */
    fun encodeToQrPayload(session: TransferSessionInfo): String {
        val encodedFiles = session.files.joinToString(FILE_SEPARATOR) { file ->
            val safeName = URLEncoder.encode(file.name, StandardCharsets.UTF_8.name())
            val safeMime = URLEncoder.encode(file.mimeType, StandardCharsets.UTF_8.name())
            "${file.id}$FIELD_SEPARATOR$safeName$FIELD_SEPARATOR${file.sizeBytes}$FIELD_SEPARATOR$safeMime"
        }

        return listOf(
            PROTOCOL_PREFIX,
            session.sessionId,
            session.serverIp,
            session.serverPort.toString(),
            session.authToken,
            session.fileCount.toString(),
            session.totalSizeBytes.toString(),
            encodedFiles
        ).joinToString(SEPARATOR)
    }

    /**
     * Decodes QR code content back into TransferSessionInfo, or null if invalid format.
     */
    fun decodeFromQrPayload(qrContent: String): TransferSessionInfo? {
        try {
            if (!qrContent.startsWith(PROTOCOL_PREFIX)) return null
            val parts = qrContent.split(SEPARATOR)
            if (parts.size < 8) return null

            val sessionId = parts[1]
            val serverIp = parts[2]
            val serverPort = parts[3].toIntOrNull() ?: return null
            val authToken = parts[4]
            val fileCount = parts[5].toIntOrNull() ?: return null
            val totalSizeBytes = parts[6].toLongOrNull() ?: return null
            val rawFiles = parts[7]

            val files = if (rawFiles.isNotEmpty()) {
                rawFiles.split(FILE_SEPARATOR).mapIndexedNotNull { index, fileStr ->
                    val fileFields = fileStr.split(FIELD_SEPARATOR)
                    if (fileFields.size >= 4) {
                        val id = fileFields[0]
                        val name = URLDecoder.decode(fileFields[1], StandardCharsets.UTF_8.name())
                        val size = fileFields[2].toLongOrNull() ?: 0L
                        val mime = URLDecoder.decode(fileFields[3], StandardCharsets.UTF_8.name())
                        TransferFileInfo(id = id, name = name, sizeBytes = size, mimeType = mime)
                    } else if (fileFields.size >= 3) {
                        val name = URLDecoder.decode(fileFields[0], StandardCharsets.UTF_8.name())
                        val size = fileFields[1].toLongOrNull() ?: 0L
                        val mime = URLDecoder.decode(fileFields[2], StandardCharsets.UTF_8.name())
                        TransferFileInfo(id = index.toString(), name = name, sizeBytes = size, mimeType = mime)
                    } else {
                        null
                    }
                }
            } else {
                emptyList()
            }

            return TransferSessionInfo(
                sessionId = sessionId,
                serverIp = serverIp,
                serverPort = serverPort,
                authToken = authToken,
                fileCount = fileCount,
                totalSizeBytes = totalSizeBytes,
                files = files,
                protocolVersion = 1
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Helper to verify if scanned QR string represents a file transfer payload.
     */
    fun isTransferPayload(qrContent: String): Boolean {
        return qrContent.startsWith("QRFTS:")
    }
}
