package com.donsmak.ocrscanner.utils

/**
 * A simple data class to hold the result from the OCR engine.
 *
 * @param text The extracted text.
 * @param hasArabic Whether the text contains Arabic characters.
 * @param error An error message if the process failed, otherwise null.
 */
data class OcrResult(
    val text: String,
    val hasArabic: Boolean,
    val error: String? = null
)
