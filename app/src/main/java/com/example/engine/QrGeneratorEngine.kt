package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.model.QrCustomization
import com.example.model.QrErrorCorrection
import com.example.model.QrEyeStyle
import com.example.model.QrGradientMode
import com.example.model.QrLogo
import com.example.model.QrPattern
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder
import com.google.zxing.qrcode.encoder.QRCode
import java.util.EnumMap
import kotlin.math.max
import kotlin.math.min

object QrGeneratorEngine {

    private fun getZxingErrorCorrection(level: QrErrorCorrection): ErrorCorrectionLevel {
        return when (level) {
            QrErrorCorrection.L -> ErrorCorrectionLevel.L
            QrErrorCorrection.M -> ErrorCorrectionLevel.M
            QrErrorCorrection.Q -> ErrorCorrectionLevel.Q
            QrErrorCorrection.H -> ErrorCorrectionLevel.H
        }
    }

    /**
     * Generates a high-quality stylized QR Code Bitmap fully offline.
     */
    fun generateQrBitmap(
        content: String,
        customization: QrCustomization,
        size: Int = 1024
    ): Bitmap? {
        if (content.isEmpty()) return null

        return try {
            val hints = EnumMap<EncodeHintType, Any>(EncodeHintType::class.java).apply {
                put(EncodeHintType.CHARACTER_SET, "UTF-8")
                put(EncodeHintType.ERROR_CORRECTION, getZxingErrorCorrection(customization.errorCorrection))
            }

            // Encode to QR Code matrix using ZXing Encoder
            val qrCode: QRCode = Encoder.encode(content, getZxingErrorCorrection(customization.errorCorrection), hints)
            val byteMatrix = qrCode.matrix ?: return null

            val matrixWidth = byteMatrix.width
            val matrixHeight = byteMatrix.height
            val quietZone = customization.quietZone

            val totalModulesX = matrixWidth + quietZone * 2
            val totalModulesY = matrixHeight + quietZone * 2

            val moduleSize = size.toFloat() / max(totalModulesX, totalModulesY)
            val outputWidth = (totalModulesX * moduleSize).toInt()
            val outputHeight = (totalModulesY * moduleSize).toInt()

            val bitmap = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Draw Background
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = customization.backgroundColor
            }
            canvas.drawRect(0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(), bgPaint)

            // 2. Setup Foreground Shader & Paint
            val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

            when (customization.gradientMode) {
                QrGradientMode.NONE -> {
                    fgPaint.color = customization.foregroundColor
                }
                QrGradientMode.LINEAR_DIAGONAL -> {
                    fgPaint.shader = LinearGradient(
                        0f, 0f, outputWidth.toFloat(), outputHeight.toFloat(),
                        customization.foregroundColor, customization.gradientColor2,
                        Shader.TileMode.CLAMP
                    )
                }
                QrGradientMode.LINEAR_HORIZONTAL -> {
                    fgPaint.shader = LinearGradient(
                        0f, 0f, outputWidth.toFloat(), 0f,
                        customization.foregroundColor, customization.gradientColor2,
                        Shader.TileMode.CLAMP
                    )
                }
                QrGradientMode.LINEAR_VERTICAL -> {
                    fgPaint.shader = LinearGradient(
                        0f, 0f, 0f, outputHeight.toFloat(),
                        customization.foregroundColor, customization.gradientColor2,
                        Shader.TileMode.CLAMP
                    )
                }
                QrGradientMode.RADIAL -> {
                    val cx = outputWidth / 2f
                    val cy = outputHeight / 2f
                    val radius = max(outputWidth, outputHeight) / 1.5f
                    fgPaint.shader = RadialGradient(
                        cx, cy, radius,
                        customization.foregroundColor, customization.gradientColor2,
                        Shader.TileMode.CLAMP
                    )
                }
            }

            // Finder Pattern zones (7x7 modules at top-left, top-right, bottom-left)
            val isFinderPattern = { x: Int, y: Int ->
                (x < 7 && y < 7) ||
                (x >= matrixWidth - 7 && y < 7) ||
                (x < 7 && y >= matrixHeight - 7)
            }

            val isEyeOuterFrame = { x: Int, y: Int ->
                val inTopLeft = (x in 0..6 && (y == 0 || y == 6)) || (y in 0..6 && (x == 0 || x == 6))
                val inTopRight = (x in (matrixWidth - 7) until matrixWidth && (y == 0 || y == 6)) ||
                        (y in 0..6 && (x == matrixWidth - 7 || x == matrixWidth - 1))
                val inBottomLeft = (x in 0..6 && (y == matrixHeight - 7 || y == matrixHeight - 1)) ||
                        (y in (matrixHeight - 7) until matrixHeight && (x == 0 || x == 6))
                inTopLeft || inTopRight || inBottomLeft
            }

            val isEyeCenterDot = { x: Int, y: Int ->
                val inTopLeft = x in 2..4 && y in 2..4
                val inTopRight = x in (matrixWidth - 5)..(matrixWidth - 3) && y in 2..4
                val inBottomLeft = x in 2..4 && y in (matrixHeight - 5)..(matrixHeight - 3)
                inTopLeft || inTopRight || inBottomLeft
            }

            // Center logo area exclusion to avoid rendering stray pixels under badge
            val centerMinX = (matrixWidth * 0.38f).toInt()
            val centerMaxX = (matrixWidth * 0.62f).toInt()
            val centerMinY = (matrixHeight * 0.38f).toInt()
            val centerMaxY = (matrixHeight * 0.62f).toInt()
            val hasLogo = customization.logo != QrLogo.NONE

            // 3. Draw Data Modules (Non-Finder)
            for (my in 0 until matrixHeight) {
                for (mx in 0 until matrixWidth) {
                    if (isFinderPattern(mx, my)) continue

                    // If center logo, skip modules directly in center center
                    if (hasLogo && mx in centerMinX..centerMaxX && my in centerMinY..centerMaxY) {
                        continue
                    }

                    val isDark = byteMatrix.get(mx, my).toInt() == 1
                    if (isDark) {
                        val px = (mx + quietZone) * moduleSize
                        val py = (my + quietZone) * moduleSize
                        drawModule(canvas, px, py, moduleSize, customization.pattern, fgPaint)
                    }
                }
            }

            // 4. Draw Custom Finder Pattern Eyes (Top-Left, Top-Right, Bottom-Left)
            drawFinderEye(canvas, quietZone, quietZone, moduleSize, customization.eyeStyle, fgPaint, bgPaint)
            drawFinderEye(canvas, (matrixWidth - 7 + quietZone), quietZone, moduleSize, customization.eyeStyle, fgPaint, bgPaint)
            drawFinderEye(canvas, quietZone, (matrixHeight - 7 + quietZone), moduleSize, customization.eyeStyle, fgPaint, bgPaint)

            // 5. Draw Center Logo / Emblem if requested
            if (hasLogo) {
                drawCenterBadge(canvas, outputWidth, outputHeight, customization, fgPaint)
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawModule(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        pattern: QrPattern,
        paint: Paint
    ) {
        val rect = RectF(x, y, x + size, y + size)
        when (pattern) {
            QrPattern.SQUARE -> {
                canvas.drawRect(rect, paint)
            }
            QrPattern.ROUNDED -> {
                val r = size * 0.35f
                canvas.drawRoundRect(rect, r, r, paint)
            }
            QrPattern.DOTS -> {
                val cx = x + size / 2f
                val cy = y + size / 2f
                val radius = size * 0.45f
                canvas.drawCircle(cx, cy, radius, paint)
            }
            QrPattern.SQUIRCLE -> {
                val r = size * 0.48f
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
    }

    private fun drawFinderEye(
        canvas: Canvas,
        moduleStartX: Int,
        moduleStartY: Int,
        moduleSize: Float,
        eyeStyle: QrEyeStyle,
        fgPaint: Paint,
        bgPaint: Paint
    ) {
        val eyePxX = moduleStartX * moduleSize
        val eyePxY = moduleStartY * moduleSize
        val eyeSize = 7 * moduleSize

        val outerRect = RectF(eyePxX, eyePxY, eyePxX + eyeSize, eyePxY + eyeSize)
        val middleRect = RectF(
            eyePxX + moduleSize,
            eyePxY + moduleSize,
            eyePxX + eyeSize - moduleSize,
            eyePxY + eyeSize - moduleSize
        )
        val innerRect = RectF(
            eyePxX + 2 * moduleSize,
            eyePxY + 2 * moduleSize,
            eyePxX + eyeSize - 2 * moduleSize,
            eyePxY + eyeSize - 2 * moduleSize
        )

        when (eyeStyle) {
            QrEyeStyle.SQUARE -> {
                canvas.drawRect(outerRect, fgPaint)
                canvas.drawRect(middleRect, bgPaint)
                canvas.drawRect(innerRect, fgPaint)
            }
            QrEyeStyle.ROUNDED -> {
                val outerRadius = moduleSize * 2.2f
                val middleRadius = moduleSize * 1.5f
                val innerRadius = moduleSize * 1.0f

                canvas.drawRoundRect(outerRect, outerRadius, outerRadius, fgPaint)
                canvas.drawRoundRect(middleRect, middleRadius, middleRadius, bgPaint)
                canvas.drawRoundRect(innerRect, innerRadius, innerRadius, fgPaint)
            }
            QrEyeStyle.CIRCLE -> {
                val cx = outerRect.centerX()
                val cy = outerRect.centerY()

                canvas.drawCircle(cx, cy, eyeSize / 2f, fgPaint)
                canvas.drawCircle(cx, cy, (eyeSize - 2 * moduleSize) / 2f, bgPaint)
                canvas.drawCircle(cx, cy, (eyeSize - 4 * moduleSize) / 2f, fgPaint)
            }
        }
    }

    private fun drawCenterBadge(
        canvas: Canvas,
        width: Int,
        height: Int,
        customization: QrCustomization,
        fgPaint: Paint
    ) {
        val badgeSize = width * 0.22f
        val cx = width / 2f
        val cy = height / 2f
        val badgeRect = RectF(cx - badgeSize / 2f, cy - badgeSize / 2f, cx + badgeSize / 2f, cy + badgeSize / 2f)

        // Draw protective background badge
        val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = customization.logoBackgroundColor
        }
        val cornerR = badgeSize * 0.28f
        canvas.drawRoundRect(badgeRect, cornerR, cornerR, badgeBgPaint)

        // Draw badge outline
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = badgeSize * 0.04f
            color = customization.foregroundColor
        }
        canvas.drawRoundRect(badgeRect, cornerR, cornerR, borderPaint)

        // Draw Icon / Emblem inside badge
        val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = customization.foregroundColor
            textAlign = Paint.Align.CENTER
        }

        when (customization.logo) {
            QrLogo.NONE -> {}
            QrLogo.CUSTOM_TEXT -> {
                iconPaint.typeface = Typeface.DEFAULT_BOLD
                iconPaint.textSize = badgeSize * 0.44f
                val fontMetrics = iconPaint.fontMetrics
                val textY = cy - (fontMetrics.ascent + fontMetrics.descent) / 2f
                val text = customization.logoCustomText.take(3)
                canvas.drawText(text, cx, textY, iconPaint)
            }
            QrLogo.LINK -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.LINK)
            QrLogo.WIFI -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.WIFI)
            QrLogo.PHONE -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.PHONE)
            QrLogo.EMAIL -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.EMAIL)
            QrLogo.SHIELD -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.SHIELD)
            QrLogo.STAR -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.STAR)
            QrLogo.HEART -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.HEART)
            QrLogo.LOCK -> drawVectorIcon(canvas, cx, cy, badgeSize * 0.52f, iconPaint, IconType.LOCK)
        }
    }

    private enum class IconType {
        LINK, WIFI, PHONE, EMAIL, SHIELD, STAR, HEART, LOCK
    }

    private fun drawVectorIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, paint: Paint, type: IconType) {
        val half = size / 2f
        val path = Path()

        when (type) {
            IconType.STAR -> {
                val spikes = 5
                val outerRadius = half
                val innerRadius = half * 0.45f
                var rot = Math.PI / 2 * 3
                val step = Math.PI / spikes
                path.moveTo(cx, (cy - outerRadius))
                for (i in 0 until spikes) {
                    var x = cx + kotlin.math.cos(rot).toFloat() * outerRadius
                    var y = cy + kotlin.math.sin(rot).toFloat() * outerRadius
                    path.lineTo(x, y)
                    rot += step

                    x = cx + kotlin.math.cos(rot).toFloat() * innerRadius
                    y = cy + kotlin.math.sin(rot).toFloat() * innerRadius
                    path.lineTo(x, y)
                    rot += step
                }
                path.close()
                canvas.drawPath(path, paint)
            }
            IconType.HEART -> {
                path.moveTo(cx, cy + half * 0.7f)
                path.cubicTo(cx - half * 0.9f, cy + half * 0.1f, cx - half * 0.9f, cy - half * 0.7f, cx - half * 0.35f, cy - half * 0.7f)
                path.cubicTo(cx - half * 0.05f, cy - half * 0.7f, cx, cy - half * 0.3f, cx, cy - half * 0.3f)
                path.cubicTo(cx, cy - half * 0.3f, cx + half * 0.05f, cy - half * 0.7f, cx + half * 0.35f, cy - half * 0.7f)
                path.cubicTo(cx + half * 0.9f, cy - half * 0.7f, cx + half * 0.9f, cy + half * 0.1f, cx, cy + half * 0.7f)
                path.close()
                canvas.drawPath(path, paint)
            }
            IconType.SHIELD -> {
                path.moveTo(cx, cy - half * 0.85f)
                path.lineTo(cx + half * 0.75f, cy - half * 0.55f)
                path.lineTo(cx + half * 0.75f, cy)
                path.cubicTo(cx + half * 0.75f, cy + half * 0.6f, cx, cy + half * 0.9f, cx, cy + half * 0.9f)
                path.cubicTo(cx, cy + half * 0.9f, cx - half * 0.75f, cy + half * 0.6f, cx - half * 0.75f, cy)
                path.lineTo(cx - half * 0.75f, cy - half * 0.55f)
                path.close()
                canvas.drawPath(path, paint)
            }
            IconType.LOCK -> {
                val bodyRect = RectF(cx - half * 0.65f, cy - half * 0.1f, cx + half * 0.65f, cy + half * 0.8f)
                canvas.drawRoundRect(bodyRect, half * 0.15f, half * 0.15f, paint)

                val shacklePaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = half * 0.22f
                }
                val shackleRect = RectF(cx - half * 0.38f, cy - half * 0.8f, cx + half * 0.38f, cy - half * 0.1f)
                canvas.drawArc(shackleRect, 180f, 180f, false, shacklePaint)
            }
            IconType.WIFI -> {
                val wifiPaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = half * 0.2f
                    strokeCap = Paint.Cap.ROUND
                }
                // Dot
                canvas.drawCircle(cx, cy + half * 0.55f, half * 0.14f, paint)
                // Arcs
                val arc1 = RectF(cx - half * 0.45f, cy + half * 0.05f, cx + half * 0.45f, cy + half * 0.95f)
                canvas.drawArc(arc1, 210f, 120f, false, wifiPaint)
                val arc2 = RectF(cx - half * 0.85f, cy - half * 0.35f, cx + half * 0.85f, cy + half * 1.35f)
                canvas.drawArc(arc2, 210f, 120f, false, wifiPaint)
            }
            IconType.PHONE -> {
                val bodyRect = RectF(cx - half * 0.45f, cy - half * 0.85f, cx + half * 0.45f, cy + half * 0.85f)
                val phonePaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = half * 0.16f
                }
                canvas.drawRoundRect(bodyRect, half * 0.18f, half * 0.18f, phonePaint)
                canvas.drawCircle(cx, cy + half * 0.6f, half * 0.08f, paint)
            }
            IconType.EMAIL -> {
                val envelopeRect = RectF(cx - half * 0.8f, cy - half * 0.55f, cx + half * 0.8f, cy + half * 0.55f)
                val envPaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = half * 0.16f
                    strokeJoin = Paint.Join.ROUND
                }
                canvas.drawRoundRect(envelopeRect, half * 0.12f, half * 0.12f, envPaint)
                val flapPath = Path().apply {
                    moveTo(cx - half * 0.8f, cy - half * 0.55f)
                    lineTo(cx, cy + half * 0.05f)
                    lineTo(cx + half * 0.8f, cy - half * 0.55f)
                }
                canvas.drawPath(flapPath, envPaint)
            }
            IconType.LINK -> {
                val linkPaint = Paint(paint).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = half * 0.18f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(cx - half * 0.4f, cy + half * 0.4f, cx + half * 0.4f, cy - half * 0.4f, linkPaint)
                val rect1 = RectF(cx - half * 0.8f, cy - half * 0.1f, cx - half * 0.1f, cy + half * 0.6f)
                canvas.drawArc(rect1, 90f, 180f, false, linkPaint)
                val rect2 = RectF(cx + half * 0.1f, cy - half * 0.6f, cx + half * 0.8f, cy + half * 0.1f)
                canvas.drawArc(rect2, 270f, 180f, false, linkPaint)
            }
        }
    }
}
