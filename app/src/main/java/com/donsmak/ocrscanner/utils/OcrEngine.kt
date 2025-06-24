package com.donsmak.ocrscanner.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Base64
import android.util.Log
import com.donsmak.ocrscanner.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.min

data class OcrResult(
        val text: String,
        val confidence: Float,
        val method: String,
        val language: String,
        val processingTime: Long,
        val wordCount: Int,
        val hasArabic: Boolean,
        val hasLatin: Boolean
)

class OcrEngine {
    companion object {
        private const val TAG = "OcrEngine"
    }

    private val httpClient = OkHttpClient.Builder()
                    .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                                        .addHeader("Content-Type", "application/json")
                                        .build()
                        chain.proceed(request)
                    }
                    .build()

    private val cloudVisionApiKey = BuildConfig.GOOGLE_API_KEY

    suspend fun extractText(
            bitmap: Bitmap,
            enhanceImage: Boolean = true
    ): OcrResult {
        val startTime = System.currentTimeMillis()

        if (cloudVisionApiKey.isEmpty()) {
            return OcrResult(
                text = "Error: Google Cloud Vision API key not configured",
                confidence = 0f,
                method = "Error - No API Key",
                language = "Error",
                processingTime = System.currentTimeMillis() - startTime,
                wordCount = 0,
                hasArabic = false,
                hasLatin = false
            )
        }

        val processedBitmap = if (enhanceImage) {
                    enhanceImageForOcr(bitmap)
                } else {
                    bitmap
                }

        return extractWithCloudVision(processedBitmap, startTime)
    }

    private suspend fun extractWithCloudVision(bitmap: Bitmap, startTime: Long): OcrResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Calling Google Cloud Vision API...")
                val base64Image = bitmapToBase64(bitmap)
                val response = callCloudVisionAPI(base64Image)
                val jsonResponse = JSONObject(response)

                Log.d(TAG, "Cloud Vision response: $response")

                val annotations = jsonResponse
                                .optJSONArray("responses")
                                ?.optJSONObject(0)
                                ?.optJSONArray("textAnnotations")

                if (annotations != null && annotations.length() > 0) {
                    val fullText = annotations.getJSONObject(0).getString("description")
                    val confidence = calculateConfidenceFromCloud(annotations)

                    Log.d(TAG, "Extracted text: $fullText")
                    Log.d(TAG, "Confidence: $confidence")

                    OcrResult(
                            text = fullText,
                            confidence = confidence,
                            method = "Google Cloud Vision",
                            language = detectLanguage(fullText),
                            processingTime = System.currentTimeMillis() - startTime,
                            wordCount = fullText.split("\\s+".toRegex()).size,
                            hasArabic = containsArabic(fullText),
                            hasLatin = containsLatin(fullText)
                    )
                } else {
                    Log.w(TAG, "No text found in image")
                    OcrResult(
                        text = "No text detected in image",
                            confidence = 0f,
                        method = "Google Cloud Vision (No Text)",
                            language = "Unknown",
                            processingTime = System.currentTimeMillis() - startTime,
                            wordCount = 0,
                            hasArabic = false,
                            hasLatin = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cloud Vision API failed", e)
            OcrResult(
                    text = "Error: ${e.message}",
                    confidence = 0f,
                    method = "Error - Cloud Vision Failed",
                    language = "Error",
                    processingTime = System.currentTimeMillis() - startTime,
                    wordCount = 0,
                    hasArabic = false,
                    hasLatin = false
            )
            }
        }
    }

    private suspend fun callCloudVisionAPI(base64Image: String): String {
        return withContext(Dispatchers.IO) {
            val json = JSONObject().apply {
                put("requests", JSONArray().apply {
                    put(JSONObject().apply {
                        put("image", JSONObject().apply {
                                                            put("content", base64Image)
                        })
                        put("features", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "TEXT_DETECTION")
                                                                        put("maxResults", 50)
                            })
                        })
                        put("imageContext", JSONObject().apply {
                            put("languageHints", JSONArray().apply {
                                put("ar")
                                                                        put("en")
                            })
                        })
                    })
                })
            }

            Log.d(TAG, "Making request to Cloud Vision API...")
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url("https://vision.googleapis.com/v1/images:annotate?key=$cloudVisionApiKey")
                            .post(requestBody)
                            .build()

            val response = httpClient.newCall(request).execute()
            Log.d(TAG, "Cloud Vision API response code: ${response.code}")

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "Cloud Vision API error: ${response.code} - $errorBody")
                throw IOException("Cloud Vision API failed: ${response.code} - $errorBody")
            }

            response.body?.string() ?: throw IOException("Empty response from Cloud Vision API")
        }
    }

    private fun containsArabic(text: String): Boolean {
        return text.any { char ->
            char.code in 0x0600..0x06FF ||
            char.code in 0x0750..0x077F ||
            char.code in 0x08A0..0x08FF ||
            char.code in 0xFB50..0xFDFF ||
            char.code in 0xFE70..0xFEFF
        }
    }

    private fun containsLatin(text: String): Boolean {
        return text.any { char ->
            char.code in 0x0041..0x005A || char.code in 0x0061..0x007A
        }
    }

    private fun detectLanguage(text: String): String {
        return when {
            containsArabic(text) && containsLatin(text) -> "Arabic & Latin"
            containsArabic(text) -> "Arabic"
            containsLatin(text) -> "Latin"
            text.isBlank() -> "Unknown"
            else -> "Other"
        }
    }

    private fun enhanceImageForOcr(bitmap: Bitmap): Bitmap {
        val enhanced = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(enhanced)
        val paint = Paint()

        val colorMatrix = ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, 30f,
            0f, 1.5f, 0f, 0f, 30f,
            0f, 0f, 1.5f, 0f, 30f,
            0f, 0f, 0f, 1f, 0f
        ))

        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return enhanced
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()

        val scaledBitmap = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val scale = min(1024f / bitmap.width, 1024f / bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun calculateConfidenceFromCloud(annotations: JSONArray): Float {
        if (annotations.length() <= 1) return 0.85f

        var totalConfidence = 0f
        var count = 0

        for (i in 1 until annotations.length()) {
            val annotation = annotations.getJSONObject(i)
            if (annotation.has("confidence")) {
                totalConfidence += annotation.getDouble("confidence").toFloat()
                count++
            }
        }

        return if (count > 0) totalConfidence / count else 0.85f
    }
}
