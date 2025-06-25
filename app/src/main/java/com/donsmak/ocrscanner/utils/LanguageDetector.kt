package com.donsmak.ocrscanner.utils

object LanguageDetector {
    /**
     * Checks if a given string contains any Arabic characters.
     */
    fun containsArabic(text: String): Boolean {
        for (char in text) {
            // Check if the character belongs to the Arabic Unicode block
            if (Character.UnicodeBlock.of(char) == Character.UnicodeBlock.ARABIC) {
                return true
            }
        }
        return false
    }
}
