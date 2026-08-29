package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.QrDatabase
import com.example.data.local.QrHistoryEntity
import com.example.data.local.HistoryRepository
import com.example.engine.ParsedQrResult
import com.example.engine.QrGeneratorEngine
import com.example.engine.QrParser
import com.example.engine.QrReadabilityChecker
import com.example.engine.QrScannerAnalyzer
import com.example.engine.QrStorageHelper
import com.example.model.QrCustomization
import com.example.model.QrErrorCorrection
import com.example.model.QrEyeStyle
import com.example.model.QrGradientMode
import com.example.model.QrLogo
import com.example.model.QrPattern
import com.example.model.QrPayload
import com.example.model.QrType
import com.example.model.ReadabilityResult
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiMessage(
    val message: String,
    val isError: Boolean = false,
    val id: Long = System.currentTimeMillis()
)

class QrViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: HistoryRepository

    init {
        val db = QrDatabase.getInstance(application)
        repository = HistoryRepository(db.qrHistoryDao())
    }

    // Theme Mode
    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    // Notification / Toast message
    private val _userMessage = MutableStateFlow<UiMessage?>(null)
    val userMessage: StateFlow<UiMessage?> = _userMessage.asStateFlow()

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(message: String, isError: Boolean = false) {
        _userMessage.value = UiMessage(message, isError)
    }

    // Generator Form State
    private val _selectedQrType = MutableStateFlow(QrType.URL)
    val selectedQrType: StateFlow<QrType> = _selectedQrType.asStateFlow()

    private val _qrPayload = MutableStateFlow(
        QrPayload(
            type = QrType.URL,
            title = "Website Link",
            rawContent = "https://",
            summary = "https://google.com",
            url = "https://google.com"
        )
    )
    val qrPayload: StateFlow<QrPayload> = _qrPayload.asStateFlow()

    // Customization State
    private val _customization = MutableStateFlow(QrCustomization())
    val customization: StateFlow<QrCustomization> = _customization.asStateFlow()

    // Live Generated Bitmap
    private val _generatedBitmap = MutableStateFlow<Bitmap?>(null)
    val generatedBitmap: StateFlow<Bitmap?> = _generatedBitmap.asStateFlow()

    // Readability & Scannability Contrast Result
    private val _readabilityResult = MutableStateFlow(QrReadabilityChecker.evaluate(QrCustomization()))
    val readabilityResult: StateFlow<ReadabilityResult> = _readabilityResult.asStateFlow()

    // Scanning State
    private val _activeScanResult = MutableStateFlow<ParsedQrResult?>(null)
    val activeScanResult: StateFlow<ParsedQrResult?> = _activeScanResult.asStateFlow()

    private val _isFlashlightOn = MutableStateFlow(false)
    val isFlashlightOn: StateFlow<Boolean> = _isFlashlightOn.asStateFlow()

    // History Database Flow
    val allHistory = repository.allHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val createdHistory = repository.createdHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val scannedHistory = repository.scannedHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recentHistory = repository.getRecentHistory(5).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Active item being viewed/edited in details
    private val _selectedHistoryItem = MutableStateFlow<QrHistoryEntity?>(null)
    val selectedHistoryItem: StateFlow<QrHistoryEntity?> = _selectedHistoryItem.asStateFlow()

    private var generateJob: Job? = null

    init {
        regenerateQrBitmap()
    }

    fun selectQrType(type: QrType) {
        _selectedQrType.value = type
        val current = _qrPayload.value
        val defaultTitle = when (type) {
            QrType.TEXT -> "Plain Text"
            QrType.URL -> "Website Link"
            QrType.PHONE -> "Phone Contact"
            QrType.EMAIL -> "Email Contact"
            QrType.SMS -> "SMS Message"
            QrType.WIFI -> "Wi-Fi Network"
            QrType.CONTACT -> "vCard Contact"
            QrType.LOCATION -> "Geo Location"
            QrType.JSON_DATA -> "JSON Payload"
            QrType.CUSTOM -> "Custom Data"
        }
        val defaultContent = when (type) {
            QrType.TEXT -> current.text.ifEmpty { "Welcome to QR Studio!" }
            QrType.URL -> current.url.ifEmpty { "https://github.com" }
            QrType.PHONE -> current.phoneNumber.ifEmpty { "+1234567890" }
            QrType.EMAIL -> current.emailAddress.ifEmpty { "hello@example.com" }
            QrType.SMS -> current.smsPhone.ifEmpty { "+1234567890" }
            QrType.WIFI -> current.wifiSsid.ifEmpty { "MyHomeWiFi" }
            QrType.CONTACT -> current.contactName.ifEmpty { "Alex Morgan" }
            QrType.LOCATION -> current.locationQuery.ifEmpty { "San Francisco, CA" }
            QrType.JSON_DATA -> current.jsonData.ifEmpty { "{\"app\":\"QR Studio\",\"version\":\"1.0\"}" }
            QrType.CUSTOM -> current.customData.ifEmpty { "Custom QR Content" }
        }

        _qrPayload.value = current.copy(
            type = type,
            title = defaultTitle,
            rawContent = defaultContent
        )
        regenerateQrBitmap()
    }

    fun updatePayload(updated: QrPayload) {
        _qrPayload.value = updated
        regenerateQrBitmap()
    }

    fun updateCustomization(transform: (QrCustomization) -> QrCustomization) {
        val newCustomization = transform(_customization.value)
        _customization.value = newCustomization
        _readabilityResult.value = QrReadabilityChecker.evaluate(newCustomization)
        regenerateQrBitmap()
    }

    fun applyPreset(presetName: String) {
        when (presetName) {
            "Classic Black" -> updateCustomization {
                it.copy(
                    foregroundColor = 0xFF000000.toInt(),
                    backgroundColor = 0xFFFFFFFF.toInt(),
                    gradientMode = QrGradientMode.NONE,
                    pattern = QrPattern.SQUARE,
                    eyeStyle = QrEyeStyle.SQUARE
                )
            }
            "Electric Indigo" -> updateCustomization {
                it.copy(
                    foregroundColor = 0xFF4F46E5.toInt(),
                    gradientColor2 = 0xFF9333EA.toInt(),
                    gradientMode = QrGradientMode.LINEAR_DIAGONAL,
                    backgroundColor = 0xFFF8FAFC.toInt(),
                    pattern = QrPattern.ROUNDED,
                    eyeStyle = QrEyeStyle.ROUNDED
                )
            }
            "Emerald Minimal" -> updateCustomization {
                it.copy(
                    foregroundColor = 0xFF059669.toInt(),
                    gradientColor2 = 0xFF0D9488.toInt(),
                    gradientMode = QrGradientMode.LINEAR_HORIZONTAL,
                    backgroundColor = 0xFFFFFFFF.toInt(),
                    pattern = QrPattern.DOTS,
                    eyeStyle = QrEyeStyle.CIRCLE
                )
            }
            "Cyber Sunset" -> updateCustomization {
                it.copy(
                    foregroundColor = 0xFFEA580C.toInt(),
                    gradientColor2 = 0xFFE11D48.toInt(),
                    gradientMode = QrGradientMode.LINEAR_DIAGONAL,
                    backgroundColor = 0xFFFFFBEB.toInt(),
                    pattern = QrPattern.SQUIRCLE,
                    eyeStyle = QrEyeStyle.ROUNDED
                )
            }
            "Dark Tech" -> updateCustomization {
                it.copy(
                    foregroundColor = 0xFF38BDF8.toInt(),
                    gradientColor2 = 0xFF818CF8.toInt(),
                    gradientMode = QrGradientMode.LINEAR_DIAGONAL,
                    backgroundColor = 0xFF0B0F19.toInt(),
                    pattern = QrPattern.ROUNDED,
                    eyeStyle = QrEyeStyle.CIRCLE
                )
            }
        }
    }

    private fun regenerateQrBitmap() {
        generateJob?.cancel()
        generateJob = viewModelScope.launch(Dispatchers.Default) {
            delay(150) // Small debounce for smooth UI typing
            val payload = _qrPayload.value
            val custom = _customization.value
            val raw = QrParser.buildRawContent(payload)
            if (raw.isNotBlank()) {
                val bitmap = QrGeneratorEngine.generateQrBitmap(raw, custom, size = 1024)
                withContext(Dispatchers.Main) {
                    _generatedBitmap.value = bitmap
                }
            }
        }
    }

    // Save current generated QR to History
    fun saveGeneratedQrToHistory(customTitle: String? = null) {
        viewModelScope.launch {
            val payload = _qrPayload.value
            val raw = QrParser.buildRawContent(payload)
            val title = customTitle?.ifBlank { null } ?: payload.title.ifBlank { payload.type.displayName }
            val summary = when (payload.type) {
                QrType.TEXT -> payload.text.take(60)
                QrType.URL -> payload.url
                QrType.PHONE -> payload.phoneNumber
                QrType.EMAIL -> payload.emailAddress
                QrType.SMS -> "To: ${payload.smsPhone}"
                QrType.WIFI -> "SSID: ${payload.wifiSsid} (${payload.wifiType})"
                QrType.CONTACT -> payload.contactName
                QrType.LOCATION -> payload.locationQuery.ifEmpty { "${payload.locationLat}, ${payload.locationLng}" }
                QrType.JSON_DATA -> "JSON (${raw.length} chars)"
                QrType.CUSTOM -> raw.take(50)
            }.ifEmpty { raw.take(50) }

            val entity = QrHistoryEntity(
                historyType = "CREATED",
                qrType = payload.type.name,
                title = title,
                content = raw,
                summary = summary,
                timestamp = System.currentTimeMillis()
            )
            repository.insertHistory(entity)
            showMessage("Saved to Created History!")
        }
    }

    // Save Bitmap image to Device Gallery
    fun saveQrToGallery(context: Context, customTitle: String? = null) {
        val bitmap = _generatedBitmap.value
        if (bitmap == null) {
            showMessage("QR code not generated yet.", isError = true)
            return
        }
        val title = customTitle ?: _qrPayload.value.title
        val uri = QrStorageHelper.saveBitmapToGallery(context, bitmap, title)
        if (uri != null) {
            showMessage("Saved QR image to Pictures/QR Studio")
            saveGeneratedQrToHistory(title)
        } else {
            showMessage("Failed to save image.", isError = true)
        }
    }

    // Share QR Code Image
    fun shareQrCode(context: Context) {
        val bitmap = _generatedBitmap.value
        val payload = _qrPayload.value
        val raw = QrParser.buildRawContent(payload)
        if (bitmap != null) {
            QrStorageHelper.shareQrBitmap(context, bitmap, payload.title, raw)
        } else {
            QrStorageHelper.shareText(context, raw, payload.title)
        }
    }

    // Copy QR Raw Content
    fun copyCurrentContent(context: Context) {
        val raw = QrParser.buildRawContent(_qrPayload.value)
        QrStorageHelper.copyToClipboard(context, "QR Content", raw)
        showMessage("Copied to clipboard")
    }

    // Scanner actions
    fun onQrScanned(rawText: String) {
        val parsed = QrParser.parse(rawText)
        _activeScanResult.value = parsed

        // Save scan automatically to Scanned history
        viewModelScope.launch {
            val entity = QrHistoryEntity(
                historyType = "SCANNED",
                qrType = parsed.type.name,
                title = parsed.title,
                content = parsed.rawText,
                summary = parsed.summary,
                timestamp = System.currentTimeMillis()
            )
            repository.insertHistory(entity)
        }
    }

    fun scanGalleryBitmap(bitmap: Bitmap) {
        val scannedText = QrScannerAnalyzer.scanBitmap(bitmap)
        if (!scannedText.isNullOrBlank()) {
            onQrScanned(scannedText)
        } else {
            showMessage("No QR code detected in this image.", isError = true)
        }
    }

    fun clearActiveScanResult() {
        _activeScanResult.value = null
    }

    fun toggleFlashlight() {
        _isFlashlightOn.value = !_isFlashlightOn.value
    }

    // History Item interactions
    fun selectHistoryItem(item: QrHistoryEntity?) {
        _selectedHistoryItem.value = item
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            repository.deleteById(id)
            if (_selectedHistoryItem.value?.id == id) {
                _selectedHistoryItem.value = null
            }
            showMessage("History item deleted")
        }
    }

    fun renameHistoryItem(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateTitle(id, newTitle)
            val current = _selectedHistoryItem.value
            if (current?.id == id) {
                _selectedHistoryItem.value = current.copy(title = newTitle)
            }
            showMessage("Renamed to \"$newTitle\"")
        }
    }

    fun toggleFavoriteHistoryItem(item: QrHistoryEntity) {
        viewModelScope.launch {
            repository.updateFavorite(item.id, !item.isFavorite)
            val current = _selectedHistoryItem.value
            if (current?.id == item.id) {
                _selectedHistoryItem.value = current.copy(isFavorite = !item.isFavorite)
            }
        }
    }

    fun clearCreatedHistory() {
        viewModelScope.launch {
            repository.clearCreated()
            showMessage("Created history cleared")
        }
    }

    fun clearScannedHistory() {
        viewModelScope.launch {
            repository.clearScanned()
            showMessage("Scanned history cleared")
        }
    }

    fun loadHistoryItemIntoGenerator(item: QrHistoryEntity) {
        val parsed = QrParser.parse(item.content)
        _selectedQrType.value = parsed.type
        _qrPayload.value = QrPayload(
            type = parsed.type,
            title = item.title,
            rawContent = item.content,
            summary = item.summary,
            url = if (parsed.type == QrType.URL) item.content else "",
            text = if (parsed.type == QrType.TEXT) item.content else "",
            phoneNumber = if (parsed.type == QrType.PHONE) parsed.details["Number"] ?: "" else "",
            emailAddress = if (parsed.type == QrType.EMAIL) parsed.details["Email"] ?: "" else "",
            wifiSsid = if (parsed.type == QrType.WIFI) parsed.details["Network (SSID)"] ?: "" else "",
            wifiPassword = if (parsed.type == QrType.WIFI) parsed.details["Password"] ?: "" else "",
            contactName = if (parsed.type == QrType.CONTACT) parsed.details["Name"] ?: "" else "",
            jsonData = if (parsed.type == QrType.JSON_DATA) item.content else "",
            customData = if (parsed.type == QrType.CUSTOM) item.content else ""
        )
        regenerateQrBitmap()
    }
}
