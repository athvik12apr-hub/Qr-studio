package com.example.engine

import android.content.ContentResolver
import android.net.Uri
import com.example.model.TransferFileInfo
import com.example.model.TransferProgress
import com.example.model.TransferSessionInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

class FileTransferServer(
    private val contentResolver: ContentResolver,
    private val sessionInfo: TransferSessionInfo,
    private val onReceiverConnected: () -> Unit,
    private val onProgress: (TransferProgress) -> Unit,
    private val onFileSent: (fileIndex: Int, fileName: String) -> Unit,
    private val onComplete: () -> Unit,
    private val onError: (String) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var clientSocket: Socket? = null
    private var isCancelled = false
    private var serverJob: Job? = null

    val boundPort: Int
        get() = serverSocket?.localPort ?: sessionInfo.serverPort

    /**
     * Pre-binds the ServerSocket so we have an exact port allocated before starting the background coroutine.
     */
    fun bind(): Int {
        val ss = ServerSocket(0)
        serverSocket = ss
        return ss.localPort
    }

    fun start(scope: CoroutineScope) {
        serverJob = scope.launch(Dispatchers.IO) {
            try {
                if (serverSocket == null || serverSocket?.isClosed == true) {
                    serverSocket = ServerSocket(0)
                }
                val ss = serverSocket ?: throw IllegalStateException("Failed to initialize ServerSocket")
                ss.soTimeout = 180_000 // 3 minutes timeout waiting for connection

                // Wait for receiver connection
                val socket = try {
                    ss.accept()
                } catch (e: SocketTimeoutException) {
                    if (!isCancelled) onError("Timed out waiting for receiver connection.")
                    return@launch
                }
                clientSocket = socket
                socket.soTimeout = 30_000 // 30 seconds I/O timeout

                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val writer = PrintWriter(socket.getOutputStream(), true)
                val rawOut = socket.getOutputStream()

                // Step 1: Handshake & Auth check
                val authLine = reader.readLine()
                if (authLine == null || !authLine.startsWith("AUTH ") || authLine.substring(5).trim() != sessionInfo.authToken) {
                    writer.println("ERR_AUTH_FAILED")
                    onError("Unauthorized connection rejected.")
                    return@launch
                }

                writer.println("AUTH_OK")
                withContext(Dispatchers.Main) {
                    onReceiverConnected()
                }

                // Step 2: Stream all files sequentially
                val totalBytes = sessionInfo.totalSizeBytes
                var cumulativeBytesSent = 0L
                var lastSpeedCalcTime = System.currentTimeMillis()
                var bytesSentSinceLastCalc = 0L
                var currentSpeed = 0L

                val buffer = ByteArray(64 * 1024) // 64 KB buffer

                for (index in sessionInfo.files.indices) {
                    if (isCancelled || !isActive) break

                    val file = sessionInfo.files[index]
                    val uri = file.uriString?.let { Uri.parse(it) }
                    
                    if (uri == null) {
                        onError("File URI unavailable for ${file.name}")
                        return@launch
                    }

                    // Send file start header: FILE <index> <size>
                    writer.println("FILE ${file.id} ${file.sizeBytes}")

                    // Wait for receiver ACK: READY
                    val readyLine = reader.readLine()
                    if (readyLine != "READY") {
                        onError("Receiver sync failed for ${file.name}")
                        return@launch
                    }

                    var fileBytesSent = 0L
                    var inputStream: InputStream? = null
                    try {
                        inputStream = contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            onError("Cannot open stream for ${file.name}")
                            return@launch
                        }

                        while (isActive && !isCancelled) {
                            val bytesRead = inputStream.read(buffer)
                            if (bytesRead == -1) break

                            rawOut.write(buffer, 0, bytesRead)
                            rawOut.flush()

                            fileBytesSent += bytesRead
                            cumulativeBytesSent += bytesRead
                            bytesSentSinceLastCalc += bytesRead

                            val now = System.currentTimeMillis()
                            val timeDelta = now - lastSpeedCalcTime
                            if (timeDelta >= 500) {
                                currentSpeed = ((bytesSentSinceLastCalc * 1000L) / timeDelta)
                                bytesSentSinceLastCalc = 0L
                                lastSpeedCalcTime = now
                            }

                            val remainingBytes = (totalBytes - cumulativeBytesSent).coerceAtLeast(0L)
                            val remainingSecs = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L

                            val progress = TransferProgress(
                                currentFileIndex = index + 1,
                                totalFiles = sessionInfo.fileCount,
                                currentFileName = file.name,
                                currentFileBytes = fileBytesSent,
                                currentFileTotalBytes = file.sizeBytes,
                                overallBytes = cumulativeBytesSent,
                                overallTotalBytes = totalBytes,
                                speedBytesPerSec = currentSpeed,
                                remainingSeconds = remainingSecs,
                                statusMessage = "Sending ${file.name}..."
                            )
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                        }
                    } finally {
                        inputStream?.close()
                    }

                    if (isCancelled || !isActive) break

                    // Read ACK for file completion
                    val fileDoneAck = reader.readLine()
                    if (fileDoneAck != "FILE_ACK") {
                        onError("Receiver failed to confirm ${file.name}")
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        onFileSent(index + 1, file.name)
                    }
                }

                if (!isCancelled && isActive) {
                    writer.println("COMPLETE")
                    withContext(Dispatchers.Main) {
                        onComplete()
                    }
                }

            } catch (e: Exception) {
                if (!isCancelled && e !is CancellationException) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        onError(e.message ?: "Transfer connection failed.")
                    }
                }
            } finally {
                close()
            }
        }
    }

    fun cancel() {
        isCancelled = true
        serverJob?.cancel()
        close()
    }

    private fun close() {
        try {
            clientSocket?.close()
        } catch (_: Exception) {}
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
    }
}
