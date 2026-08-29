package com.example

import com.example.engine.FileTransferProtocol
import com.example.engine.NetworkUtils
import com.example.model.TransferFileInfo
import com.example.model.TransferSessionInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferUnitTest {

    @Test
    fun testEncodeAndDecodeTransferSession() {
        val files = listOf(
            TransferFileInfo(id = "f1", name = "presentation.pdf", sizeBytes = 1048576L, mimeType = "application/pdf"),
            TransferFileInfo(id = "f2", name = "vacation video.mp4", sizeBytes = 52428800L, mimeType = "video/mp4")
        )
        val session = TransferSessionInfo(
            sessionId = "sess-12345",
            authToken = "tok-secret-abc",
            serverIp = "192.168.1.150",
            serverPort = 8989,
            files = files,
            fileCount = files.size,
            totalSizeBytes = 53477376L
        )

        val qrPayload = FileTransferProtocol.encodeToQrPayload(session)
        assertTrue("QR payload must start with protocol prefix", qrPayload.startsWith("QRFTS:1"))

        val decoded = FileTransferProtocol.decodeFromQrPayload(qrPayload)
        assertNotNull("Decoded session must not be null", decoded)
        assertEquals("Session ID matches", session.sessionId, decoded?.sessionId)
        assertEquals("Auth Token matches", session.authToken, decoded?.authToken)
        assertEquals("Server IP matches", session.serverIp, decoded?.serverIp)
        assertEquals("Server Port matches", session.serverPort, decoded?.serverPort)
        assertEquals("File count matches", 2, decoded?.files?.size)
        assertEquals("First file name matches", "presentation.pdf", decoded?.files?.get(0)?.name)
        assertEquals("First file size matches", 1048576L, decoded?.files?.get(0)?.sizeBytes)
        assertEquals("Second file name matches", "vacation video.mp4", decoded?.files?.get(1)?.name)
        assertEquals("Total bytes matches", 53477376L, decoded?.totalSizeBytes)
    }

    @Test
    fun testInvalidQrPayloadDecoding() {
        val invalidPayload = "https://google.com"
        val result = FileTransferProtocol.decodeFromQrPayload(invalidPayload)
        assertNull("Invalid payload should return null", result)

        val malformedPayload = "QRFTS:1:INVALID_BASE64_#*@"
        val malformedResult = FileTransferProtocol.decodeFromQrPayload(malformedPayload)
        assertNull("Malformed payload should return null", malformedResult)
    }

    @Test
    fun testNetworkUtilsFormatting() {
        assertEquals("0 B", NetworkUtils.formatBytes(0L))
        assertEquals("512 B", NetworkUtils.formatBytes(512L))
        assertEquals("1.0 KB", NetworkUtils.formatBytes(1024L))
        assertEquals("1.5 MB", NetworkUtils.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.0 GB", NetworkUtils.formatBytes((2.0 * 1024 * 1024 * 1024).toLong()))

        assertEquals("5.0 MB/s", NetworkUtils.formatSpeed((5.0 * 1024 * 1024).toLong()))
        assertEquals("0s", NetworkUtils.formatDuration(0L))
        assertEquals("45s", NetworkUtils.formatDuration(45L))
        assertEquals("2m 15s", NetworkUtils.formatDuration(135L))
        assertEquals("1h 10m", NetworkUtils.formatDuration(4200L))
    }
}
