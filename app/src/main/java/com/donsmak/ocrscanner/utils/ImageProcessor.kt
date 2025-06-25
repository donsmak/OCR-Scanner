package com.donsmak.ocrscanner.utils

import android.graphics.*
import android.util.Log
import androidx.core.graphics.ColorUtils
import kotlin.math.*

object ImageProcessor {

    /**
     * Processes an image for optimal OCR accuracy
     *
     * @param bitmap The input bitmap
     * @param enhanceContrast Whether to enhance contrast
     * @param sharpen Whether to apply sharpening
     * @param denoise Whether to apply denoising
     * @param binarize Whether to convert to black and white
     * @return Processed bitmap optimized for OCR
     */
        fun optimizeForOCR(
        bitmap: Bitmap,
        enhanceContrast: Boolean = true,
        sharpen: Boolean = true,
        denoise: Boolean = true,
        binarize: Boolean = false,
        maxResolution: Int = 2048
    ): Bitmap {
        if (bitmap.isRecycled) {
            throw IllegalArgumentException("Cannot process recycled bitmap")
        }

        var processed = bitmap
        var previousBitmap: Bitmap? = null

        try {
            Log.d("ImageProcessor", "Original size: ${bitmap.width}x${bitmap.height}")

            // 1. Scale to optimal resolution (higher than before)
            val scaled = scaleToOptimalSize(processed, maxResolution)
            if (scaled != processed) {
                previousBitmap = processed
                processed = scaled
            }
            Log.d("ImageProcessor", "Scaled size: ${processed.width}x${processed.height}")

                    // 2. Enhance contrast for better text visibility
            if (enhanceContrast) {
                val enhanced = enhanceContrast(processed)
                if (enhanced != processed && processed != bitmap) {
                    processed.recycle()
                }
                processed = enhanced
                Log.d("ImageProcessor", "Applied contrast enhancement")
            }

            // 3. Apply sharpening to make text edges crisp
            if (sharpen) {
                val sharpened = sharpenImage(processed)
                if (sharpened != processed && processed != bitmap) {
                    processed.recycle()
                }
                processed = sharpened
                Log.d("ImageProcessor", "Applied sharpening")
            }

            // 4. Denoise to remove grain that can interfere with OCR
            if (denoise) {
                val denoised = denoise(processed)
                if (denoised != processed && processed != bitmap) {
                    processed.recycle()
                }
                processed = denoised
                Log.d("ImageProcessor", "Applied denoising")
            }

            // 5. Optional: Convert to high-contrast black and white
            if (binarize) {
                val binarized = binarize(processed)
                if (binarized != processed && processed != bitmap) {
                    processed.recycle()
                }
                processed = binarized
                Log.d("ImageProcessor", "Applied binarization")
            }

            return processed

        } catch (e: Exception) {
            Log.e("ImageProcessor", "Error during image processing", e)
            // Clean up any intermediate bitmaps
            if (processed != bitmap && !processed.isRecycled) {
                processed.recycle()
            }
            previousBitmap?.let { if (!it.isRecycled) it.recycle() }
            throw e
        }
    }

    /**
     * Scale image to optimal size for OCR while maintaining aspect ratio
     */
    private fun scaleToOptimalSize(bitmap: Bitmap, maxResolution: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxResolution && height <= maxResolution) {
            return bitmap
        }

        val scale = minOf(
            maxResolution.toFloat() / width,
            maxResolution.toFloat() / height
        )

        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Enhance contrast using histogram equalization
     */
    private fun enhanceContrast(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        // Create contrast enhancement color matrix
        val contrastMatrix = ColorMatrix(floatArrayOf(
            1.2f, 0f, 0f, 0f, 10f,     // Red channel: increase contrast, slight brightness
            0f, 1.2f, 0f, 0f, 10f,     // Green channel
            0f, 0f, 1.2f, 0f, 10f,     // Blue channel
            0f, 0f, 0f, 1f, 0f         // Alpha channel
        ))

        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Apply sharpening filter to make text edges crisp
     */
    private fun sharpenImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Sharpening kernel
        val kernel = arrayOf(
            floatArrayOf(0f, -1f, 0f),
            floatArrayOf(-1f, 5f, -1f),
            floatArrayOf(0f, -1f, 0f)
        )

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val resultPixels = IntArray(width * height)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                var sumR = 0f
                var sumG = 0f
                var sumB = 0f

                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val pixelIndex = (y + ky) * width + (x + kx)
                        val pixel = pixels[pixelIndex]
                        val weight = kernel[ky + 1][kx + 1]

                        sumR += Color.red(pixel) * weight
                        sumG += Color.green(pixel) * weight
                        sumB += Color.blue(pixel) * weight
                    }
                }

                val resultIndex = y * width + x
                resultPixels[resultIndex] = Color.rgb(
                    sumR.coerceIn(0f, 255f).toInt(),
                    sumG.coerceIn(0f, 255f).toInt(),
                    sumB.coerceIn(0f, 255f).toInt()
                )
            }
        }

        result.setPixels(resultPixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Apply simple denoising
     */
    private fun denoise(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true

        // Apply slight blur to reduce noise
        paint.maskFilter = BlurMaskFilter(0.5f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * Convert to high-contrast black and white using adaptive thresholding
     */
    private fun binarize(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        // Calculate mean brightness
        var totalBrightness = 0f
        for (pixel in pixels) {
            totalBrightness += (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f
        }
        val meanBrightness = totalBrightness / pixels.size

        // Apply adaptive threshold
        val threshold = meanBrightness * 0.85f // Slightly lower than mean

        for (i in pixels.indices) {
            val pixel = pixels[i]
            val brightness = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3f

            pixels[i] = if (brightness > threshold) {
                Color.WHITE
            } else {
                Color.BLACK
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height)
        return result
    }

    /**
     * Get optimal compression settings for OCR
     */
    fun getOptimalCompressionSettings(): CompressionSettings {
        return CompressionSettings(
            format = Bitmap.CompressFormat.PNG, // Lossless for text
            quality = 100 // Max quality
        )
    }

    data class CompressionSettings(
        val format: Bitmap.CompressFormat,
        val quality: Int
    )
}
