package com.example.engine

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.ReceivedFileInfo
import com.example.model.TransferProgress
import com.example.model.TransferSessionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale

class FileTransferClient(
    private val context: Context,
    private val sessionInfo: TransferSessionInfo,
    private val onConnected: () -> Unit,
    private val onProgress: (TransferProgress) -> Unit,
    private val onFileReceived: (ReceivedFileInfo) -> Unit,
    private val onComplete: (List<ReceivedFileInfo>) -> Unit,
    private val onError: (String) -> Unit
) {
    private var socket: Socket? = null
    private var isCancelled = false
    private var clientJob: Job? = null
    private var currentPartialFile: File? = null

    fun start(scope: CoroutineScope) {
        clientJob = scope.launch(Dispatchers.IO) {
            val receivedFilesList = mutableListOf<ReceivedFileInfo>()
            try {
                val s = Socket()
                socket = s
                // 15 seconds connect timeout
                s.connect(InetSocketAddress(sessionInfo.serverIp, sessionInfo.serverPort), 15_000)
                s.soTimeout = 30_000 // 30 seconds I/O timeout

                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)
                val rawIn: InputStream = s.getInputStream()

                // Step 1: Handshake
                writer.println("AUTH ${sessionInfo.authToken}")
                val authResponse = reader.readLine()
                if (authResponse != "AUTH_OK") {
                    onError("Authentication with sender failed ($authResponse)")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    onConnected()
                }

                val totalBytes = sessionInfo.totalSizeBytes
                var cumulativeBytesReceived = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesReceivedSinceLastCalc = 0L
                var currentSpeed = 0L

                val receivedDir = getOrCreateReceivedDir()
                val buffer = ByteArray(64 * 1024)

                for (index in sessionInfo.files.indices) {
                    if (isCancelled || !isActive) break

                    val expectedFile = sessionInfo.files[index]

                    // Step 2: Expect FILE <id> <size>
                    val fileHeader = reader.readLine() ?: throw IllegalStateException("Connection closed before receiving file header")
                    val headerParts = fileHeader.split(" ")
                    if (headerParts.size < 3 || headerParts[0] != "FILE") {
                        throw IllegalStateException("Invalid header: $fileHeader")
                    }
                    val fileSize = headerParts[2].toLongOrNull() ?: expectedFile.sizeBytes

                    // Prepare local destination file
                    val safeFileName = getUniqueFileName(receivedDir, expectedFile.name)
                    val targetFile = File(receivedDir, safeFileName)
                    currentPartialFile = targetFile

                    // Acknowledge ready to receive stream
                    writer.println("READY")

                    var bytesReceivedForThisFile = 0L
                    val fileOutputStream = FileOutputStream(targetFile)

                    try {
                        while (bytesReceivedForThisFile < fileSize && isActive && !isCancelled) {
                            val bytesToRead = minOf(buffer.size.toLong(), fileSize - bytesReceivedForThisFile).toInt()
                            val bytesRead = rawIn.read(buffer, 0, bytesToRead)
                            if (bytesRead == -1) {
                                throw IllegalStateException("Stream ended prematurely after $bytesReceivedForThisFile bytes")
                            }

                            fileOutputStream.write(buffer, 0, bytesRead)
                            bytesReceivedForThisFile += bytesRead
                            cumulativeBytesReceived += bytesRead
                            bytesReceivedSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val delta = now - lastSpeedCalcTime
                            if (delta >= 500) {
                                currentSpeed = (bytesReceivedSinceLastCalc * 1000L) / delta
                                bytesReceivedSinceLastCalc = 0L
                                lastSpeedCalcTime = now
                            }

                            val remainingBytes = (totalBytes - cumulativeBytesReceived).coerceAtLeast(0L)
                            val remainingSecs = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                            val progress = TransferProgress(
                                currentFileIndex = index + 1,
                                totalFiles = sessionInfo.fileCount,
                                currentFileName = expectedFile.name,
                                currentFileBytes = bytesReceivedForThisFile,
                                currentFileTotalBytes = fileSize,
                                overallBytes = cumulativeBytesReceived,
                                overallTotalBytes = totalBytes,
                                speedBytesPerSec = currentSpeed,
                                remainingSeconds = remainingSecs,
                                statusMessage = "Receiving ${expectedFile.name}..."
                            )
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                        fileOutputStream.flush()
                    } finally {
                        fileOutputStream.close()
                    }

                    if (isCancelled || !isActive) {
                        targetFile.delete()
                        break
                    }

                    currentPartialFile = null

                    // Step 3: Send file ACK
                    writer.println("FILE_ACK")

                    val localUri = try {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            targetFile
                        )
                    } catch (e: Exception) {
                        Uri.fromFile(targetFile)
                    }

                    val receivedInfo = ReceivedFileInfo(
                        id = expectedFile.id,
                        name = safeFileName,
                        sizeBytes = targetFile.length(),
                        mimeType = expectedFile.mimeType,
                        localUriString = localUri.toString(),
                        absolutePath = targetFile.absolutePath,
                        receivedTimestamp = System.currentTimeMillis()
                    )
                    receivedFilesList.add(receivedInfo)

                    withContext(Dispatchers.Main) {
                        onFileReceived(receivedInfo)
                    }
                }

                if (!isCancelled && isActive) {
                    val finalStatus = reader.readLine()
                    withContext(Dispatchers.Main) {
                        onComplete(receivedFilesList)
                    }
                }

            } catch (e: Exception) {
                if (!isCancelled && e !is CancellationException) {
                    e.printStackTrace()
                    currentPartialFile?.delete()
                    withContext(Dispatchers.Main) {
                        onError(e.message ?: "Failed to connect to sender.")
                    }
                }
            } finally {
                close()
            }
        }
    }

    private fun getOrCreateReceivedDir(): File {
        val dir = context.getExternalFilesDir("received") ?: File(context.filesDir, "received")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getUniqueFileName(dir: File, desiredName: String): String {
        var file = File(dir, desiredName)
        if (!file.exists()) return desiredName

        val dotIndex = desiredName.lastIndexOf('.')
        val baseName = if (dotIndex != -1) desiredName.substring(0, dotIndex) else desiredName
        val extension = if (dotIndex != -1) desiredName.substring(dotIndex) else ""

        var count = 1
        while (file.exists()) {
            val candidate = "$baseName ($count)$extension"
            file = File(dir, candidate)
            count++
        }
        return file.name
    }

    fun cancel() {
        isCancelled = true
        clientJob?.cancel()
        currentPartialFile?.delete()
        close()
    }

    private fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {}
    }
}
