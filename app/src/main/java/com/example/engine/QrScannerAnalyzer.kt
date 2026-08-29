package com.example.engine

import android.graphics.Bitmap
import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.common.HybridBinarizer
import java.nio.ByteBuffer
import java.util.EnumMap

class QrScannerAnalyzer(
    private val onQrCodeScanned: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
            put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC))
            put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
            put(DecodeHintType.CHARACTER_SET, "UTF-8")
        }
        setHints(hints)
    }

    private var isScanningEnabled = true
    private var lastScannedTime = 0L

    fun setScanningEnabled(enabled: Boolean) {
        isScanningEnabled = enabled
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (!isScanningEnabled || currentTime - lastScannedTime < 1000L) {
            imageProxy.close()
            return
        }

        if (imageProxy.format == ImageFormat.YUV_420_888 || imageProxy.format == ImageFormat.YUV_422_888 || imageProxy.format == ImageFormat.YUV_444_888) {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val width = imageProxy.width
            val height = imageProxy.height

            // Rotate bytes if needed based on imageProxy.imageInfo.rotationDegrees
            val rotation = imageProxy.imageInfo.rotationDegrees
            val (rotatedBytes, rotW, rotH) = rotateYuv(bytes, width, height, rotation)

            val source = PlanarYUVLuminanceSource(
                rotatedBytes, rotW, rotH, 0, 0, rotW, rotH, false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decodeWithState(binaryBitmap)
                val text = result.text
                if (!text.isNullOrBlank()) {
                    lastScannedTime = currentTime
                    onQrCodeScanned(text)
                }
            } catch (e: NotFoundException) {
                // Try GlobalHistogramBinarizer for uneven lighting
                try {
                    val globalBinarizerBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                    val result = reader.decodeWithState(globalBinarizerBitmap)
                    val text = result.text
                    if (!text.isNullOrBlank()) {
                        lastScannedTime = currentTime
                        onQrCodeScanned(text)
                    }
                } catch (ignored: Exception) {
                }
            } catch (e: Exception) {
                // Ignore decoding failures
            } finally {
                reader.reset()
            }
        }

        imageProxy.close()
    }

    private fun rotateYuv(data: ByteArray, width: Int, height: Int, rotation: Int): Triple<ByteArray, Int, Int> {
        if (rotation == 0) return Triple(data, width, height)

        val rotated = ByteArray(data.size)
        when (rotation) {
            90 -> {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        rotated[x * height + (height - y - 1)] = data[y * width + x]
                    }
                }
                return Triple(rotated, height, width)
            }
            180 -> {
                for (i in 0 until width * height) {
                    rotated[width * height - 1 - i] = data[i]
                }
                return Triple(rotated, width, height)
            }
            270 -> {
                for (x in 0 until width) {
                    for (y in 0 until height) {
                        rotated[(width - x - 1) * height + y] = data[y * width + x]
                    }
                }
                return Triple(rotated, height, width)
            }
            else -> return Triple(data, width, height)
        }
    }

    companion object {
        /**
         * Scans static Bitmap from device gallery
         */
        fun scanBitmap(bitmap: Bitmap): String? {
            return try {
                val width = bitmap.width
                val height = bitmap.height
                val pixels = IntArray(width * height)
                bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

                val source = RGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(HybridBinarizer(source))
                val reader = MultiFormatReader().apply {
                    val hints = EnumMap<DecodeHintType, Any>(DecodeHintType::class.java).apply {
                        put(DecodeHintType.POSSIBLE_FORMATS, listOf(BarcodeFormat.QR_CODE, BarcodeFormat.DATA_MATRIX, BarcodeFormat.AZTEC))
                        put(DecodeHintType.TRY_HARDER, java.lang.Boolean.TRUE)
                    }
                    setHints(hints)
                }

                try {
                    reader.decode(binaryBitmap).text
                } catch (e: Exception) {
                    // Try global binarizer
                    val globalBinarizerBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                    reader.decode(globalBinarizerBitmap).text
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
