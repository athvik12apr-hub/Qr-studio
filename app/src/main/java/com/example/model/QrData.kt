package com.example.model

enum class QrType(val displayName: String, val iconName: String) {
    TEXT("Plain Text", "Notes"),
    URL("Website URL", "Link"),
    PHONE("Phone", "Phone"),
    EMAIL("Email", "Email"),
    SMS("SMS Message", "Sms"),
    WIFI("Wi-Fi Network", "Wifi"),
    CONTACT("Contact Card", "Person"),
    LOCATION("Location", "LocationOn"),
    JSON_DATA("JSON Data", "Code"),
    CUSTOM("Custom Data", "DataObject")
}

enum class QrPattern(val displayName: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    DOTS("Dots"),
    SQUIRCLE("Squircle")
}

enum class QrEyeStyle(val displayName: String) {
    SQUARE("Square"),
    ROUNDED("Rounded"),
    CIRCLE("Circle")
}

enum class QrGradientMode(val displayName: String) {
    NONE("Solid Color"),
    LINEAR_DIAGONAL("Diagonal Gradient"),
    LINEAR_HORIZONTAL("Horizontal Gradient"),
    LINEAR_VERTICAL("Vertical Gradient"),
    RADIAL("Radial Gradient")
}

enum class QrErrorCorrection(val displayName: String, val recoveryPercent: String) {
    L("Low (7%)", "7%"),
    M("Medium (15%)", "15%"),
    Q("Quartile (25%)", "25%"),
    H("High (30%)", "30%")
}

enum class QrLogo(val displayName: String) {
    NONE("None"),
    LINK("Link"),
    WIFI("Wi-Fi"),
    PHONE("Phone"),
    EMAIL("Email"),
    SHIELD("Shield"),
    STAR("Star"),
    HEART("Heart"),
    LOCK("Security"),
    CUSTOM_TEXT("Initial / Text")
}

data class QrCustomization(
    val foregroundColor: Int = 0xFF000000.toInt(), // Default Black
    val backgroundColor: Int = 0xFFFFFFFF.toInt(), // Default White
    val gradientMode: QrGradientMode = QrGradientMode.NONE,
    val gradientColor2: Int = 0xFF4F46E5.toInt(), // Indigo
    val pattern: QrPattern = QrPattern.SQUARE,
    val eyeStyle: QrEyeStyle = QrEyeStyle.SQUARE,
    val quietZone: Int = 2, // Margins in modules (0, 1, 2, 4)
    val errorCorrection: QrErrorCorrection = QrErrorCorrection.M,
    val logo: QrLogo = QrLogo.NONE,
    val logoCustomText: String = "QR",
    val logoBackgroundColor: Int = 0xFFFFFFFF.toInt()
)

data class QrPayload(
    val type: QrType,
    val title: String,
    val rawContent: String,
    val summary: String,
    // Fields for specific types for easy editing
    val url: String = "",
    val text: String = "",
    val phoneNumber: String = "",
    val emailAddress: String = "",
    val emailSubject: String = "",
    val emailBody: String = "",
    val smsPhone: String = "",
    val smsMessage: String = "",
    val wifiSsid: String = "",
    val wifiPassword: String = "",
    val wifiType: String = "WPA", // WPA, WEP, nopass
    val wifiHidden: Boolean = false,
    val contactName: String = "",
    val contactPhone: String = "",
    val contactEmail: String = "",
    val contactOrg: String = "",
    val contactUrl: String = "",
    val locationLat: String = "",
    val locationLng: String = "",
    val locationQuery: String = "",
    val jsonData: String = "",
    val customData: String = ""
)

enum class ReadabilityStatus {
    EXCELLENT,
    GOOD,
    WARNING,
    CRITICAL
}

data class ReadabilityResult(
    val status: ReadabilityStatus,
    val contrastRatio: Float,
    val warnings: List<String> = emptyList(),
    val suggestions: List<String> = emptyList()
)
