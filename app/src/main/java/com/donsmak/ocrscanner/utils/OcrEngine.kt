package com.donsmak.ocrscanner.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.donsmak.ocrscanner.BuildConfig
import com.google.auth.oauth2.GoogleCredentials
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.InputStream

class OcrEngine(private val context: Context) {

    private val client by lazy { OkHttpClient.Builder().build() }
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun process(bitmap: Bitmap, enablePreprocessing: Boolean = true): OcrResult = withContext(Dispatchers.IO) {
        try {
            /* 1️⃣ Get access token */
            val accessToken = getAccessToken()

            /* 2️⃣ Optimize image for OCR accuracy */
            val optimizedBitmap = if (enablePreprocessing) {
                Log.d("OcrEngine", "Applying image preprocessing for optimal OCR")
                try {
                    ImageProcessor.optimizeForOCR(bitmap)
                } catch (e: Exception) {
                    Log.w("OcrEngine", "Image preprocessing failed, using original bitmap", e)
                    bitmap
                }
            } else {
                bitmap
            }

            /* 3️⃣ encode the image in highest quality */
            val stream = ByteArrayOutputStream()
            val compressionSettings = ImageProcessor.getOptimalCompressionSettings()

            optimizedBitmap.compress(compressionSettings.format, compressionSettings.quality, stream)
            val base64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)

            val mimeType = when (compressionSettings.format) {
                Bitmap.CompressFormat.PNG -> "image/png"
                Bitmap.CompressFormat.JPEG -> "image/jpeg"
                else -> "image/png"
            }

            Log.d("OcrEngine", "Image size: ${stream.size()} bytes, format: $mimeType")

            /* 4️⃣ build the JSON request body */
            val jsonBody = """
                {
                  "rawDocument": {
                    "content": "$base64",
                    "mimeType": "$mimeType"
                  }
                }
            """.trimIndent()

            /* 5️⃣ call the REST endpoint */
            val url =
                "https://${BuildConfig.DOCAI_LOCATION}-documentai.googleapis.com/v1/projects/" +
                        "${BuildConfig.DOCAI_PROJECT_ID}/locations/${BuildConfig.DOCAI_LOCATION}/" +
                        "processors/${BuildConfig.DOCAI_PROCESSOR_ID}:process"

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(jsonMedia))
                .addHeader("Authorization", "Bearer $accessToken")
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d("OcrEngine", "Making request to: $url")

            client.newCall(request).execute().use { resp ->
                Log.d("OcrEngine", "Response code: ${resp.code}")
                if (!resp.isSuccessful) {
                    val errorBody = resp.body?.string() ?: "No error details"
                    Log.e("OcrEngine", "DocAI HTTP ${resp.code}: $errorBody")
                    throw RuntimeException("DocAI HTTP ${resp.code}: $errorBody")
                }

                val body = resp.body?.string() ?: throw RuntimeException("Empty response")
                val text = JsonParser.parseString(body)
                    .asJsonObject["document"].asJsonObject["text"].asString

                val result = OcrResult(
                    text = text,
                    hasArabic = LanguageDetector.containsArabic(text)
                )

                // Clean up optimized bitmap if it's different from original
                if (optimizedBitmap != bitmap && !optimizedBitmap.isRecycled) {
                    optimizedBitmap.recycle()
                }

                result
            }
        } catch (e: Exception) {
            Log.e("OcrEngine", "OCR processing failed", e)
            e.printStackTrace()
            OcrResult("", false, e.localizedMessage)
        }
    }

    private suspend fun getAccessToken(): String = withContext(Dispatchers.IO) {
        try {
            // Try to load service account credentials from assets
            val credentialsStream: InputStream = try {
                context.assets.open("service-account-key.json")
            } catch (e: Exception) {
                Log.e("OcrEngine", "Service account key not found in assets. Using default credentials.")
                // Fall back to default credentials (works on GCE, Cloud Run, etc.)
                throw RuntimeException("Service account key file not found. Please add service-account-key.json to assets folder.")
            }

            val credentials = GoogleCredentials.fromStream(credentialsStream)
                .createScoped(listOf("https://www.googleapis.com/auth/cloud-platform"))

            credentials.refreshIfExpired()
            credentials.accessToken.tokenValue
        } catch (e: Exception) {
            Log.e("OcrEngine", "Failed to get access token", e)
            throw RuntimeException("Authentication failed: ${e.message}")
        }
    }
}
