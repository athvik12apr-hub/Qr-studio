package com.example.engine

import android.graphics.Color
import com.example.model.QrCustomization
import com.example.model.QrErrorCorrection
import com.example.model.QrGradientMode
import com.example.model.QrLogo
import com.example.model.ReadabilityResult
import com.example.model.ReadabilityStatus
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

object QrReadabilityChecker {

    /**
     * Calculates the relative luminance of an sRGB color according to WCAG specifications:
     * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
     */
    fun calculateLuminance(color: Int): Float {
        val r = ((color shr 16) and 0xFF) / 255.0f
        val g = ((color shr 8) and 0xFF) / 255.0f
        val b = (color and 0xFF) / 255.0f

        val rLinear = if (r <= 0.03928f) r / 12.92f else Math.pow(((r + 0.055) / 1.055), 2.4).toFloat()
        val gLinear = if (g <= 0.03928f) g / 12.92f else Math.pow(((g + 0.055) / 1.055), 2.4).toFloat()
        val bLinear = if (b <= 0.03928f) b / 12.92f else Math.pow(((b + 0.055) / 1.055), 2.4).toFloat()

        return 0.2126f * rLinear + 0.7152f * gLinear + 0.0722f * bLinear
    }

    /**
     * Calculates the contrast ratio between two colors:
     * (L1 + 0.05) / (L2 + 0.05) where L1 is the lighter color.
     */
    fun calculateContrastRatio(colorA: Int, colorB: Int): Float {
        val lumA = calculateLuminance(colorA)
        val lumB = calculateLuminance(colorB)
        val lighter = max(lumA, lumB)
        val darker = min(lumA, lumB)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    fun evaluate(customization: QrCustomization): ReadabilityResult {
        val warnings = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        val fgContrast = calculateContrastRatio(customization.foregroundColor, customization.backgroundColor)
        val effectiveContrast = if (customization.gradientMode != QrGradientMode.NONE) {
            val gradContrast = calculateContrastRatio(customization.gradientColor2, customization.backgroundColor)
            min(fgContrast, gradContrast)
        } else {
            fgContrast
        }

        val contrastStr = String.format(Locale.US, "%.1f", effectiveContrast)

        // 1. Contrast checks
        if (effectiveContrast < 2.5f) {
            warnings.add("Very low contrast ($contrastStr:1). Cameras will struggle to scan this.")
            suggestions.add("Choose a darker foreground or lighter background.")
        } else if (effectiveContrast < 4.0f) {
            warnings.add("Moderate contrast ($contrastStr:1). May fail in dim lighting.")
            suggestions.add("Increase contrast for faster camera detection.")
        }

        // Check inverted colors (light foreground on dark background)
        val fgLum = calculateLuminance(customization.foregroundColor)
        val bgLum = calculateLuminance(customization.backgroundColor)
        if (fgLum > bgLum) {
            warnings.add("Inverted QR code (light pattern on dark background). Some basic camera apps cannot scan inverted codes.")
            suggestions.add("Standard QR codes use a dark pattern on a light background.")
        }

        // 2. Logo / Center icon safety with Error Correction
        if (customization.logo != QrLogo.NONE) {
            when (customization.errorCorrection) {
                QrErrorCorrection.L -> {
                    warnings.add("Center logo covers QR data while Error Correction is set to Low (7%). Code may be unreadable!")
                    suggestions.add("Change Error Correction to High (30%) or Quartile (25%).")
                }
                QrErrorCorrection.M -> {
                    warnings.add("Center logo with Medium Error Correction (15%) is borderline.")
                    suggestions.add("Recommend High (30%) Error Correction when embedding center logos.")
                }
                QrErrorCorrection.Q, QrErrorCorrection.H -> {
                    // Safe!
                }
            }
        }

        // 3. Margin / Quiet zone
        if (customization.quietZone == 0) {
            warnings.add("No quiet-zone margin. Scanner may fail if placed against textured surfaces.")
            suggestions.add("Add at least 1 or 2 module margin for reliable scanning.")
        }

        val status = when {
            effectiveContrast < 2.2f || (customization.logo != QrLogo.NONE && customization.errorCorrection == QrErrorCorrection.L) -> {
                ReadabilityStatus.CRITICAL
            }
            warnings.size >= 2 || effectiveContrast < 3.5f -> {
                ReadabilityStatus.WARNING
            }
            warnings.isNotEmpty() -> {
                ReadabilityStatus.GOOD
            }
            else -> {
                ReadabilityStatus.EXCELLENT
            }
        }

        return ReadabilityResult(
            status = status,
            contrastRatio = effectiveContrast,
            warnings = warnings,
            suggestions = suggestions
        )
    }
}
