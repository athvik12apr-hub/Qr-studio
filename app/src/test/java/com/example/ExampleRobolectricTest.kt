package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.engine.QrGeneratorEngine
import com.example.engine.QrParser
import com.example.engine.QrReadabilityChecker
import com.example.model.QrCustomization
import com.example.model.QrPayload
import com.example.model.QrType
import com.example.model.ReadabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("QR Studio", appName)
  }

  @Test
  fun `test QR Generator Engine creates bitmap offline`() {
    val customization = QrCustomization()
    val bitmap = QrGeneratorEngine.generateQrBitmap("https://example.com", customization, size = 256)
    assertNotNull(bitmap)
    assertEquals(256, bitmap!!.width)
    assertEquals(256, bitmap.height)
  }

  @Test
  fun `test QR Parser parses wifi payload correctly`() {
    val wifiString = "WIFI:T:WPA;S:HomeNetwork;P:SuperSecret123;H:false;;"
    val parsed = QrParser.parse(wifiString)
    assertEquals(QrType.WIFI, parsed.type)
    assertEquals("HomeNetwork", parsed.details["Network (SSID)"])
    assertEquals("SuperSecret123", parsed.details["Password"])
  }

  @Test
  fun `test Readability Checker evaluates high contrast correctly`() {
    val custom = QrCustomization(
      foregroundColor = 0xFF000000.toInt(),
      backgroundColor = 0xFFFFFFFF.toInt()
    )
    val result = QrReadabilityChecker.evaluate(custom)
    assertEquals(ReadabilityStatus.EXCELLENT, result.status)
    assertTrue(result.contrastRatio > 15f)
  }
}
