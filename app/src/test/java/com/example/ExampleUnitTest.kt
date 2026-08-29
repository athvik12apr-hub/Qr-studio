package com.example

import com.example.engine.QrGeneratorEngine
import com.example.engine.QrParser
import com.example.engine.QrReadabilityChecker
import com.example.model.QrCustomization
import com.example.model.QrErrorCorrection
import com.example.model.QrEyeStyle
import com.example.model.QrGradientMode
import com.example.model.QrLogo
import com.example.model.QrPattern
import com.example.model.QrType
import com.example.model.ReadabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun testAllQrTypesParsing() {
    // 1. URL
    val urlResult = QrParser.parse("https://github.com/developer/qr-studio")
    assertEquals(QrType.URL, urlResult.type)
    assertEquals("https://github.com/developer/qr-studio", urlResult.actionIntentUri)

    // 2. Phone
    val phoneResult = QrParser.parse("tel:+15551234567")
    assertEquals(QrType.PHONE, phoneResult.type)
    assertEquals("tel:+15551234567", phoneResult.actionIntentUri)

    // 3. Email (mailto)
    val mailtoResult = QrParser.parse("mailto:support@example.com?subject=Help&body=Hello")
    assertEquals(QrType.EMAIL, mailtoResult.type)
    assertEquals("support@example.com", mailtoResult.summary)
    assertTrue(mailtoResult.actionIntentUri?.startsWith("mailto:") == true)

    // 4. SMS
    val smsResult = QrParser.parse("smsto:+15559876543:Hello world SMS")
    assertEquals(QrType.SMS, smsResult.type)
    assertEquals("sms:+15559876543", smsResult.actionIntentUri)

    // 5. Wi-Fi
    val wifiResult = QrParser.parse("WIFI:T:WPA;S:OfficeNet;P:SecurePass999;H:false;;")
    assertEquals(QrType.WIFI, wifiResult.type)
    assertEquals("OfficeNet", wifiResult.details["Network (SSID)"])
    assertEquals("SecurePass999", wifiResult.details["Password"])

    // 6. Contact / vCard
    val vcardResult = QrParser.parse("BEGIN:VCARD\nVERSION:3.0\nN:Doe;John\nFN:John Doe\nTEL:+15550001111\nEMAIL:john@example.com\nEND:VCARD")
    assertEquals(QrType.CONTACT, vcardResult.type)
    assertEquals("John Doe", vcardResult.details["Name"])
    assertEquals("+15550001111", vcardResult.details["Phone"])

    // 7. Geo Location
    val geoResult = QrParser.parse("geo:37.7749,-122.4194?q=San+Francisco")
    assertEquals(QrType.LOCATION, geoResult.type)
    assertTrue(geoResult.details["Coordinates"]!!.contains("37.7749"))

    // 8. JSON Data
    val jsonResult = QrParser.parse("""{"id": 42, "user": "alice", "active": true}""")
    assertEquals(QrType.JSON_DATA, jsonResult.type)

    // 9. Plain Text
    val textResult = QrParser.parse("This is a simple plain text note stored in a QR code.")
    assertEquals(QrType.TEXT, textResult.type)
  }

  @Test
  fun testReadabilityEvaluation() {
    // High contrast black on white
    val excellentCustom = QrCustomization(
      foregroundColor = 0xFF000000.toInt(),
      backgroundColor = 0xFFFFFFFF.toInt(),
      errorCorrection = QrErrorCorrection.M
    )
    val result1 = QrReadabilityChecker.evaluate(excellentCustom)
    assertEquals(ReadabilityStatus.EXCELLENT, result1.status)

    // Very low contrast (light grey on white)
    val criticalCustom = QrCustomization(
      foregroundColor = 0xFFEEEEEE.toInt(),
      backgroundColor = 0xFFFFFFFF.toInt()
    )
    val result2 = QrReadabilityChecker.evaluate(criticalCustom)
    assertEquals(ReadabilityStatus.CRITICAL, result2.status)
    assertTrue(result2.warnings.isNotEmpty())

    // Low error correction with logo warning
    val lowEcWithLogo = QrCustomization(
      foregroundColor = 0xFF000000.toInt(),
      backgroundColor = 0xFFFFFFFF.toInt(),
      errorCorrection = QrErrorCorrection.L,
      logo = QrLogo.LOCK
    )
    val result3 = QrReadabilityChecker.evaluate(lowEcWithLogo)
    assertTrue(result3.warnings.any { it.contains("Error Correction is set to Low") })
  }
}
