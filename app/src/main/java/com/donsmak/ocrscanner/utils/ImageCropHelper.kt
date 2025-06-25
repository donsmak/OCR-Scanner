package com.donsmak.ocrscanner.utils

import android.content.Context
import android.net.Uri
import com.yalantis.ucrop.UCrop
import java.io.File
import java.util.UUID

object ImageCropHelper {

    /**
     * Creates UCrop options for cropping images for OCR
     */
    fun createCropIntent(
        context: Context,
        sourceUri: Uri,
        aspectRatioX: Float = 1f,
        aspectRatioY: Float = 1f,
        maxWidth: Int = 2048,
        maxHeight: Int = 2048
    ): UCrop {
        val destinationFileName = "cropped_${UUID.randomUUID()}.png"
        val destinationUri = Uri.fromFile(File(context.cacheDir, destinationFileName))

        return UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(aspectRatioX, aspectRatioY)
            .withMaxResultSize(maxWidth, maxHeight)
            .withOptions(createCropOptions())
    }

    /**
     * Creates UCrop options with optimized settings for OCR
     */
    private fun createCropOptions(): UCrop.Options {
        val options = UCrop.Options()

        // Image processing options - use PNG for lossless quality
        options.setCompressionFormat(android.graphics.Bitmap.CompressFormat.PNG)
        options.setCompressionQuality(100)

        // UI options
        options.setHideBottomControls(false)
        options.setFreeStyleCropEnabled(true)

        // Allow user to choose aspect ratio
        options.setAspectRatioOptions(
            1, // Default selected
            com.yalantis.ucrop.model.AspectRatio("Free", 0f, 0f),
            com.yalantis.ucrop.model.AspectRatio("Square", 1f, 1f),
            com.yalantis.ucrop.model.AspectRatio("Document", 3f, 4f),
            com.yalantis.ucrop.model.AspectRatio("Wide", 16f, 9f)
        )

        // Toolbar
        options.setToolbarTitle("Crop Image for OCR")

        return options
    }

    /**
     * Creates a temporary file URI for storing images
     */
    fun createTempImageUri(context: Context): Uri {
        val tempFile = File(context.cacheDir, "temp_${UUID.randomUUID()}.jpg")
        return Uri.fromFile(tempFile)
    }
}
