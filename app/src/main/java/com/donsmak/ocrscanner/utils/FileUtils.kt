package com.donsmak.ocrscanner.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {
    /**
     * Create a .docx file in a temporary cache directory and returns a shareable URI.
     * @param context The application context.
     * @param text The text content to write into the document.
     * @return A content:// URI for the created file, or null if an error occurred.
     */
    fun createAndSaveDocx(context: Context, text: String): Uri? {
        return try {
            // Create a new document in memory
            val document = XWPFDocument()

            // Preserve line-breaks & mark the paragraph RTL so Arabic renders correctly
            val paragraph = document.createParagraph().apply {
                alignment = ParagraphAlignment.RIGHT
            }
            val run = paragraph.createRun()

            text.split("\n").forEachIndexed { i, line ->
                run.setText(line)
                if (i < text.lines().size - 1) run.addBreak()
            }

            // Define the directory and filename
            // This creates a file in a "docs" subdirectory of our app's private cache
            val docsDir = File(context.cacheDir, "docs")
            if (!docsDir.exists()) {
                docsDir.mkdirs()
            }
            val timeStamp =
                SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.getDefault()).format(Date())
            val file = File(docsDir, "Scan_${timeStamp}.docx")

            // Write the document to the file
            FileOutputStream(file).use { fileOutputStream ->
                document.write(fileOutputStream)
            }
            document.close()

            // Get the secure, shareable URI for the file using our FileProvider
            val authority = "${context.packageName}.provider"
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
