package com.example.engine

import android.net.Uri
import com.example.model.QrPayload
import com.example.model.QrType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

data class ParsedQrResult(
    val type: QrType,
    val title: String,
    val summary: String,
    val rawText: String,
    val actionLabel: String?,
    val actionIntentUri: String?,
    val details: Map<String, String> = emptyMap()
)

object QrParser {

    /**
     * Builds raw string payload from structured QrPayload
     */
    fun buildRawContent(payload: QrPayload): String {
        return when (payload.type) {
            QrType.TEXT -> payload.text.ifEmpty { payload.rawContent }
            QrType.URL -> {
                val u = payload.url.trim().ifEmpty { payload.rawContent.trim() }
                if (!u.startsWith("http://", ignoreCase = true) && !u.startsWith("https://", ignoreCase = true)) {
                    "https://$u"
                } else {
                    u
                }
            }
            QrType.PHONE -> {
                val p = payload.phoneNumber.trim().ifEmpty { payload.rawContent.trim() }
                if (!p.startsWith("tel:", ignoreCase = true)) "tel:$p" else p
            }
            QrType.EMAIL -> {
                val email = payload.emailAddress.trim().ifEmpty { payload.rawContent.trim() }
                val subject = Uri.encode(payload.emailSubject.trim())
                val body = Uri.encode(payload.emailBody.trim())
                buildString {
                    append("mailto:").append(email)
                    val params = mutableListOf<String>()
                    if (subject.isNotEmpty()) params.add("subject=$subject")
                    if (body.isNotEmpty()) params.add("body=$body")
                    if (params.isNotEmpty()) {
                        append("?").append(params.joinToString("&"))
                    }
                }
            }
            QrType.SMS -> {
                val phone = payload.smsPhone.trim().ifEmpty { payload.rawContent.trim() }
                val msg = payload.smsMessage.trim()
                if (msg.isNotEmpty()) "smsto:$phone:$msg" else "smsto:$phone"
            }
            QrType.WIFI -> {
                val ssid = escapeWifi(payload.wifiSsid.trim())
                val pass = escapeWifi(payload.wifiPassword)
                val type = payload.wifiType.ifEmpty { "WPA" }
                val hidden = if (payload.wifiHidden) "true" else "false"
                "WIFI:S:$ssid;T:$type;P:$pass;H:$hidden;;"
            }
            QrType.CONTACT -> {
                val name = payload.contactName.trim()
                val phone = payload.contactPhone.trim()
                val email = payload.contactEmail.trim()
                val org = payload.contactOrg.trim()
                val url = payload.contactUrl.trim()
                buildString {
                    appendLine("BEGIN:VCARD")
                    appendLine("VERSION:3.0")
                    if (name.isNotEmpty()) {
                        appendLine("FN:$name")
                        appendLine("N:$name;;;;")
                    }
                    if (org.isNotEmpty()) appendLine("ORG:$org")
                    if (phone.isNotEmpty()) appendLine("TEL;TYPE=CELL:$phone")
                    if (email.isNotEmpty()) appendLine("EMAIL:$email")
                    if (url.isNotEmpty()) appendLine("URL:$url")
                    append("END:VCARD")
                }
            }
            QrType.LOCATION -> {
                val lat = payload.locationLat.trim()
                val lng = payload.locationLng.trim()
                val query = payload.locationQuery.trim()
                if (query.isNotEmpty() && (lat.isEmpty() || lng.isEmpty())) {
                    "geo:0,0?q=${Uri.encode(query)}"
                } else if (query.isNotEmpty()) {
                    "geo:$lat,$lng?q=${Uri.encode(query)}"
                } else {
                    "geo:$lat,$lng"
                }
            }
            QrType.JSON_DATA -> payload.jsonData.trim().ifEmpty { payload.rawContent.trim() }
            QrType.CUSTOM -> payload.customData.ifEmpty { payload.rawContent }
        }
    }

    private fun escapeWifi(value: String): String {
        return value.replace("\\", "\\\\")
            .replace(";", "\\;")
            .replace(",", "\\,")
            .replace(":", "\\:")
    }

    /**
     * Parses any scanned string into structured semantic data
     */
    fun parse(rawText: String): ParsedQrResult {
        val trimmed = rawText.trim()

        // 1. Wi-Fi: WIFI:S:ssid;T:WPA;P:password;;
        if (trimmed.startsWith("WIFI:", ignoreCase = true)) {
            val ssid = extractWifiField(trimmed, "S")
            val pass = extractWifiField(trimmed, "P")
            val type = extractWifiField(trimmed, "T").ifEmpty { "WPA" }
            val hidden = extractWifiField(trimmed, "H") == "true"
            val details = mutableMapOf<String, String>()
            if (ssid.isNotEmpty()) details["Network (SSID)"] = ssid
            if (pass.isNotEmpty()) details["Password"] = pass
            details["Security"] = type
            if (hidden) details["Hidden Network"] = "Yes"

            return ParsedQrResult(
                type = QrType.WIFI,
                title = if (ssid.isNotEmpty()) "Wi-Fi: $ssid" else "Wi-Fi Network",
                summary = "Security: $type • Password: ${if (pass.isNotEmpty()) "••••••••" else "None"}",
                rawText = rawText,
                actionLabel = "Copy Password",
                actionIntentUri = null,
                details = details
            )
        }

        // 2. vCard / Contact Card
        if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true) || trimmed.startsWith("MECARD:", ignoreCase = true)) {
            val details = mutableMapOf<String, String>()
            var name = ""
            var phone = ""
            var email = ""
            var org = ""
            var url = ""

            if (trimmed.startsWith("BEGIN:VCARD", ignoreCase = true)) {
                val lines = trimmed.split("\n", "\r\n")
                for (line in lines) {
                    val upper = line.uppercase(Locale.ROOT)
                    when {
                        upper.startsWith("FN:") -> name = line.substring(3).trim()
                        upper.startsWith("N:") && name.isEmpty() -> name = line.substring(2).replace(";", " ").trim()
                        upper.startsWith("TEL") -> {
                            val colonIndex = line.indexOf(':')
                            if (colonIndex != -1) phone = line.substring(colonIndex + 1).trim()
                        }
                        upper.startsWith("EMAIL") -> {
                            val colonIndex = line.indexOf(':')
                            if (colonIndex != -1) email = line.substring(colonIndex + 1).trim()
                        }
                        upper.startsWith("ORG:") -> org = line.substring(4).trim()
                        upper.startsWith("URL:") -> url = line.substring(4).trim()
                    }
                }
            } else {
                // MECARD
                name = extractMecardField(trimmed, "N")
                phone = extractMecardField(trimmed, "TEL")
                email = extractMecardField(trimmed, "EMAIL")
                org = extractMecardField(trimmed, "ORG")
                url = extractMecardField(trimmed, "URL")
            }

            if (name.isNotEmpty()) details["Name"] = name
            if (phone.isNotEmpty()) details["Phone"] = phone
            if (email.isNotEmpty()) details["Email"] = email
            if (org.isNotEmpty()) details["Organization"] = org
            if (url.isNotEmpty()) details["Website"] = url

            return ParsedQrResult(
                type = QrType.CONTACT,
                title = name.ifEmpty { "Contact Card" },
                summary = listOfNotNull(phone.ifEmpty { null }, email.ifEmpty { null }).joinToString(" • ").ifEmpty { "vCard Information" },
                rawText = rawText,
                actionLabel = if (phone.isNotEmpty()) "Call $phone" else if (email.isNotEmpty()) "Email $email" else "Copy Contact",
                actionIntentUri = if (phone.isNotEmpty()) "tel:$phone" else if (email.isNotEmpty()) "mailto:$email" else null,
                details = details
            )
        }

        // 3. Email
        if (trimmed.startsWith("mailto:", ignoreCase = true) || trimmed.startsWith("matmsg:", ignoreCase = true)) {
            val email = if (trimmed.startsWith("mailto:", ignoreCase = true)) {
                val qIndex = trimmed.indexOf('?')
                if (qIndex != -1) trimmed.substring(7, qIndex) else trimmed.substring(7)
            } else {
                extractMatmsgField(trimmed, "TO")
            }
            return ParsedQrResult(
                type = QrType.EMAIL,
                title = "Email Address",
                summary = email,
                rawText = rawText,
                actionLabel = "Compose Email",
                actionIntentUri = trimmed,
                details = mapOf("Email" to email)
            )
        }

        // 4. Phone
        if (trimmed.startsWith("tel:", ignoreCase = true)) {
            val phone = trimmed.substring(4)
            return ParsedQrResult(
                type = QrType.PHONE,
                title = "Phone Number",
                summary = phone,
                rawText = rawText,
                actionLabel = "Call Number",
                actionIntentUri = "tel:$phone",
                details = mapOf("Number" to phone)
            )
        }

        // 5. URLs
        if (isLikelyUrl(trimmed)) {
            val url = if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
                "https://$trimmed"
            } else {
                trimmed
            }
            return ParsedQrResult(
                type = QrType.URL,
                title = "Website Link",
                summary = url,
                rawText = rawText,
                actionLabel = "Open Website",
                actionIntentUri = url,
                details = mapOf("URL" to url)
            )
        }

        // 6. SMS
        if (trimmed.startsWith("smsto:", ignoreCase = true) || trimmed.startsWith("sms:", ignoreCase = true)) {
            val colonAfterSms = trimmed.indexOf(':', 4)
            val phone = if (colonAfterSms != -1) trimmed.substring(colonAfterSms + 1).substringBefore(':') else trimmed.substringAfter(':')
            val msg = if (colonAfterSms != -1 && trimmed.indexOf(':', colonAfterSms + 1) != -1) trimmed.substringAfterLast(':') else ""
            val details = mutableMapOf<String, String>()
            details["Recipient"] = phone
            if (msg.isNotEmpty()) details["Message"] = msg

            return ParsedQrResult(
                type = QrType.SMS,
                title = "SMS Message",
                summary = if (msg.isNotEmpty()) "To: $phone - \"$msg\"" else "To: $phone",
                rawText = rawText,
                actionLabel = "Send SMS",
                actionIntentUri = "sms:$phone",
                details = details
            )
        }

        // 7. Geo Location
        if (trimmed.startsWith("geo:", ignoreCase = true)) {
            return ParsedQrResult(
                type = QrType.LOCATION,
                title = "Map Location",
                summary = trimmed.substring(4),
                rawText = rawText,
                actionLabel = "View on Maps",
                actionIntentUri = trimmed,
                details = mapOf("Coordinates" to trimmed.substring(4))
            )
        }

        // 8. JSON Data
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return try {
                if (trimmed.startsWith("{")) JSONObject(trimmed) else JSONArray(trimmed)
                ParsedQrResult(
                    type = QrType.JSON_DATA,
                    title = "JSON Document",
                    summary = "Structured JSON (${trimmed.length} chars)",
                    rawText = rawText,
                    actionLabel = "Copy JSON",
                    actionIntentUri = null,
                    details = mapOf("Format" to "JSON", "Length" to "${trimmed.length} characters")
                )
            } catch (e: Exception) {
                ParsedQrResult(
                    type = QrType.TEXT,
                    title = "Plain Text",
                    summary = if (trimmed.length > 50) trimmed.take(50) + "..." else trimmed,
                    rawText = rawText,
                    actionLabel = "Copy Text",
                    actionIntentUri = null,
                    details = mapOf("Length" to "${trimmed.length} characters")
                )
            }
        }

        // 9. Plain text fallback
        return ParsedQrResult(
            type = QrType.TEXT,
            title = "Text",
            summary = if (trimmed.length > 60) trimmed.take(60) + "..." else trimmed,
            rawText = rawText,
            actionLabel = "Copy Text",
            actionIntentUri = null,
            details = mapOf("Length" to "${trimmed.length} characters")
        )
    }

    private fun isLikelyUrl(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        if (lower.startsWith("http://") || lower.startsWith("https://")) return true
        if (lower.contains(" ") || lower.contains("\n")) return false
        val commonTlds = listOf(".com", ".org", ".net", ".io", ".app", ".dev", ".co", ".me", ".ai", ".xyz", ".gov", ".edu", ".info")
        return commonTlds.any { lower.contains(it) } && lower.contains(".")
    }

    private fun extractWifiField(wifiStr: String, tag: String): String {
        val pattern = Regex("$tag:([^;]*)(?<!\\\\);")
        val match = pattern.find(wifiStr) ?: return ""
        return match.groupValues[1].replace("\\;", ";").replace("\\:", ":").replace("\\,", ",").replace("\\\\", "\\")
    }

    private fun extractMecardField(mecardStr: String, tag: String): String {
        val pattern = Regex("$tag:([^;]*);", RegexOption.IGNORE_CASE)
        val match = pattern.find(mecardStr) ?: return ""
        return match.groupValues[1]
    }

    private fun extractMatmsgField(matmsgStr: String, tag: String): String {
        val pattern = Regex("$tag:([^;]*);", RegexOption.IGNORE_CASE)
        val match = pattern.find(matmsgStr) ?: return ""
        return match.groupValues[1]
    }
}
