package com.example.transfer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.engine.FileTransferClient
import com.example.engine.FileTransferProtocol
import com.example.engine.FileTransferServer
import com.example.engine.NetworkUtils
import com.example.model.ReceivedFileInfo
import com.example.model.TransferFileInfo
import com.example.model.TransferProgress
import com.example.model.TransferRole
import com.example.model.TransferSessionInfo
import com.example.model.TransferState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FileTransferViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context get() = getApplication<Application>().applicationContext

    private val _role = MutableStateFlow(TransferRole.NONE)
    val role: StateFlow<TransferRole> = _role.asStateFlow()

    private val _state = MutableStateFlow(TransferState.IDLE)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private val _selectedFiles = MutableStateFlow<List<TransferFileInfo>>(emptyList())
    val selectedFiles: StateFlow<List<TransferFileInfo>> = _selectedFiles.asStateFlow()

    private val _sessionInfo = MutableStateFlow<TransferSessionInfo?>(null)
    val sessionInfo: StateFlow<TransferSessionInfo?> = _sessionInfo.asStateFlow()

    private val _qrPayload = MutableStateFlow<String?>(null)
    val qrPayload: StateFlow<String?> = _qrPayload.asStateFlow()

    private val _progress = MutableStateFlow(TransferProgress())
    val progress: StateFlow<TransferProgress> = _progress.asStateFlow()

    private val _receivedFiles = MutableStateFlow<List<ReceivedFileInfo>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFileInfo>> = _receivedFiles.asStateFlow()

    private val _savedTransfersHistory = MutableStateFlow<List<ReceivedFileInfo>>(emptyList())
    val savedTransfersHistory: StateFlow<List<ReceivedFileInfo>> = _savedTransfersHistory.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _localIpAddress = MutableStateFlow<String?>(null)
    val localIpAddress: StateFlow<String?> = _localIpAddress.asStateFlow()

    private var server: FileTransferServer? = null
    private var client: FileTransferClient? = null

    init {
        refreshNetworkInfo()
        loadSavedTransfers()
    }

    fun refreshNetworkInfo() {
        _localIpAddress.value = NetworkUtils.getLocalIpAddress()
    }

    fun selectRole(newRole: TransferRole) {
        cancelActiveSession()
        _role.value = newRole
        _state.value = when (newRole) {
            TransferRole.SENDER -> TransferState.PICKING_FILES
            TransferRole.RECEIVER -> TransferState.SCANNING_QR
            TransferRole.NONE -> TransferState.IDLE
        }
        refreshNetworkInfo()
    }

    fun addSelectedFiles(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentList = _selectedFiles.value.toMutableList()
            val contentResolver = context.contentResolver

            for (uri in uris) {
                var fileName = "file_${System.currentTimeMillis()}"
                var fileSize = 0L
                var mimeType = contentResolver.getType(uri) ?: "application/octet-stream"

                try {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1) {
                                val name = cursor.getString(nameIndex)
                                if (!name.isNullOrBlank()) fileName = name
                            }
                            if (sizeIndex != -1) {
                                fileSize = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // If fileSize is still 0, try descriptor
                if (fileSize <= 0) {
                    try {
                        contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            fileSize = pfd.statSize
                        }
                    } catch (_: Exception) {}
                }

                val id = UUID.randomUUID().toString().take(8)
                currentList.add(
                    TransferFileInfo(
                        id = id,
                        name = fileName,
                        sizeBytes = fileSize,
                        mimeType = mimeType,
                        uriString = uri.toString()
                    )
                )
            }

            _selectedFiles.value = currentList
        }
    }

    fun removeSelectedFile(fileId: String) {
        _selectedFiles.value = _selectedFiles.value.filter { it.id != fileId }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    /**
     * Sender starts local transfer session: creates ServerSocket, binds, generates QR payload.
     */
    fun startSenderSession() {
        val files = _selectedFiles.value
        if (files.isEmpty()) {
            _errorMessage.value = "Please select at least one file to transfer."
            return
        }

        refreshNetworkInfo()
        val localIp = _localIpAddress.value
        if (localIp == null) {
            _errorMessage.value = "No active local Wi-Fi or Hotspot connection detected. Connect to Wi-Fi or enable Personal Hotspot."
            return
        }

        _state.value = TransferState.PREPARING
        _errorMessage.value = null

        val sessionId = UUID.randomUUID().toString().take(8)
        val authToken = UUID.randomUUID().toString().take(12)
        val totalBytes = files.sumOf { it.sizeBytes }

        val dummySession = TransferSessionInfo(
            sessionId = sessionId,
            serverIp = localIp,
            serverPort = 0,
            authToken = authToken,
            fileCount = files.size,
            totalSizeBytes = totalBytes,
            files = files
        )

        val newServer = FileTransferServer(
            contentResolver = context.contentResolver,
            sessionInfo = dummySession,
            onReceiverConnected = {
                _state.value = TransferState.TRANSFERRING
            },
            onProgress = { prog ->
                _progress.value = prog
            },
            onFileSent = { _, _ -> },
            onComplete = {
                _state.value = TransferState.COMPLETED
            },
            onError = { err ->
                _errorMessage.value = err
                _state.value = TransferState.FAILED
            }
        )

        val port = newServer.bind()
        val finalSession = dummySession.copy(serverPort = port)

        _sessionInfo.value = finalSession
        _qrPayload.value = FileTransferProtocol.encodeToQrPayload(finalSession)
        server = newServer

        newServer.start(viewModelScope)
        _state.value = TransferState.WAITING_FOR_RECEIVER
    }

    /**
     * Receiver scans QR code from sender.
     */
    fun onQrScanned(qrContent: String) {
        if (_state.value != TransferState.SCANNING_QR) return

        if (!FileTransferProtocol.isTransferPayload(qrContent)) {
            _errorMessage.value = "Not a valid QR Studio File Transfer QR code."
            return
        }

        val parsedSession = FileTransferProtocol.decodeFromQrPayload(qrContent)
        if (parsedSession == null) {
            _errorMessage.value = "Could not parse transfer session details."
            return
        }

        _sessionInfo.value = parsedSession
        _state.value = TransferState.CONFIRMING_RECEIVE
    }

    /**
     * Receiver confirms acceptance of incoming files.
     */
    fun confirmReceiveTransfer() {
        val session = _sessionInfo.value ?: return
        _state.value = TransferState.CONNECTING
        _errorMessage.value = null
        _receivedFiles.value = emptyList()

        val newClient = FileTransferClient(
            context = context,
            sessionInfo = session,
            onConnected = {
                _state.value = TransferState.TRANSFERRING
            },
            onProgress = { prog ->
                _progress.value = prog
            },
            onFileReceived = { fileInfo ->
                _receivedFiles.value = _receivedFiles.value + fileInfo
            },
            onComplete = { allReceived ->
                _receivedFiles.value = allReceived
                _state.value = TransferState.COMPLETED
                loadSavedTransfers()
            },
            onError = { err ->
                _errorMessage.value = err
                _state.value = TransferState.FAILED
            }
        )

        client = newClient
        newClient.start(viewModelScope)
    }

    fun declineReceiveTransfer() {
        _sessionInfo.value = null
        _state.value = TransferState.SCANNING_QR
    }

    fun restartScanning() {
        _sessionInfo.value = null
        _errorMessage.value = null
        _state.value = TransferState.SCANNING_QR
    }

    fun cancelActiveSession() {
        server?.cancel()
        server = null
        client?.cancel()
        client = null
        _state.value = TransferState.IDLE
        _sessionInfo.value = null
        _qrPayload.value = null
        _progress.value = TransferProgress()
    }

    fun resetToHub() {
        cancelActiveSession()
        _role.value = TransferRole.NONE
        _state.value = TransferState.IDLE
        _selectedFiles.value = emptyList()
        _receivedFiles.value = emptyList()
        _errorMessage.value = null
        loadSavedTransfers()
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }

    private fun loadSavedTransfers() {
        viewModelScope.launch(Dispatchers.IO) {
            val dir = context.getExternalFilesDir("received") ?: File(context.filesDir, "received")
            if (dir.exists()) {
                val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()
                val list = files.map { file ->
                    val uri = try {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    } catch (e: Exception) {
                        Uri.fromFile(file)
                    }
                    ReceivedFileInfo(
                        id = file.name,
                        name = file.name,
                        sizeBytes = file.length(),
                        mimeType = getMimeTypeFromExtension(file.extension),
                        localUriString = uri.toString(),
                        absolutePath = file.absolutePath,
                        receivedTimestamp = file.lastModified()
                    )
                }
                _savedTransfersHistory.value = list
            }
        }
    }

    fun openReceivedFile(context: Context, fileInfo: ReceivedFileInfo) {
        try {
            val file = File(fileInfo.absolutePath)
            if (!file.exists()) {
                Toast.makeText(context, "File no longer exists.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, fileInfo.mimeType.ifBlank { "*/*" })
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(Intent.createChooser(intent, "Open ${fileInfo.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "No app found to open this file format.", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareReceivedFile(context: Context, fileInfo: ReceivedFileInfo) {
        try {
            val file = File(fileInfo.absolutePath)
            if (!file.exists()) {
                Toast.makeText(context, "File not found.", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = fileInfo.mimeType.ifBlank { "*/*" }
                putExtra(Intent.EXTRA_STREAM, uri)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }

            context.startActivity(Intent.createChooser(intent, "Share ${fileInfo.name}"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share file.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getMimeTypeFromExtension(ext: String): String {
        return when (ext.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            "json" -> "application/json"
            else -> "application/octet-stream"
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelActiveSession()
    }
}
