package com.donsmak.ocrscanner.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview as CameraPreview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview as ComposePreview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.donsmak.ocrscanner.R
import com.donsmak.ocrscanner.ui.theme.OCRScannerTheme
import com.donsmak.ocrscanner.utils.ImageCropHelper
import com.donsmak.ocrscanner.utils.OcrEngine
import com.donsmak.ocrscanner.utils.OcrResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateBack: () -> Unit,
    onTextExtracted: (OcrResult) -> Unit
) {
    val context = LocalContext.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var isProcessing by remember { mutableStateOf(false) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showProcessingOptions by remember { mutableStateOf(false) }
    var currentCameraMode by remember { mutableStateOf(true) } // true = camera, false = gallery
    var enablePreprocessing by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val ocrEngine = remember { OcrEngine(context) }

    // Activity result launchers
    val cropLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val croppedUri = UCrop.getOutput(result.data!!)
            croppedUri?.let { uri ->
                scope.launch {
                    try {
                        isProcessing = true
                        Log.d("CameraScreen", "Processing cropped image: $uri")

                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream == null) {
                            Log.e("CameraScreen", "Cannot open input stream for cropped image")
                            onTextExtracted(OcrResult("", false, "Cannot read cropped image"))
                            return@launch
                        }

                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()

                        if (bitmap == null) {
                            Log.e("CameraScreen", "Failed to decode cropped image")
                            onTextExtracted(OcrResult("", false, "Failed to decode cropped image"))
                            return@launch
                        }

                        Log.d("CameraScreen", "Cropped bitmap size: ${bitmap.width}x${bitmap.height}")
                        val ocrResult = ocrEngine.process(bitmap = bitmap, enablePreprocessing = enablePreprocessing)
                        onTextExtracted(ocrResult)

                        // Clean up bitmap
                        if (!bitmap.isRecycled) {
                            bitmap.recycle()
                        }

                        // Clean up temp files
                        try {
                            val file = File(uri.path ?: "")
                            if (file.exists()) {
                                file.delete()
                                Log.d("CameraScreen", "Cleaned up temp file: ${file.name}")
                            }
                        } catch (e: Exception) {
                            Log.w("CameraScreen", "Could not clean up temp file", e)
                        }

                    } catch (e: Exception) {
                        Log.e("CameraScreen", "Error processing cropped image", e)
                        onTextExtracted(OcrResult("", false, "Error processing cropped image: ${e.localizedMessage}"))
                    } finally {
                        isProcessing = false
                    }
                }
            }
        } else {
            Log.d("CameraScreen", "Crop cancelled or failed")
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            Log.d("CameraScreen", "Gallery image selected: $it")
            try {
                val cropIntent = ImageCropHelper.createCropIntent(context, it)
                cropLauncher.launch(cropIntent.getIntent(context))
            } catch (e: Exception) {
                Log.e("CameraScreen", "Error launching crop for gallery image", e)
                onTextExtracted(OcrResult("", false, "Error opening gallery image: ${e.localizedMessage}"))
            }
        }
    }

    if (cameraPermissionState.status.isGranted) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.camera_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showImageSourceDialog = true }) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = stringResource(R.string.camera_image_source))
                        }
                        IconButton(onClick = { showProcessingOptions = true }) {
                            Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.camera_processing_options))
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (currentCameraMode) {
                    CameraPreview(
                        onImageCaptured = { bitmap ->
                            // Save bitmap to temp file and then crop
                            scope.launch {
                                try {
                                    Log.d("CameraScreen", "Camera captured bitmap: ${bitmap.width}x${bitmap.height}")

                                    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.png")

                                    // Ensure the bitmap is not too large to prevent memory issues
                                    val scaledBitmap = if (bitmap.width > 2048 || bitmap.height > 2048) {
                                        val scale = minOf(2048f / bitmap.width, 2048f / bitmap.height)
                                        val scaledWidth = (bitmap.width * scale).toInt()
                                        val scaledHeight = (bitmap.height * scale).toInt()
                                        Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                                    } else {
                                        bitmap
                                    }

                                    tempFile.outputStream().use { out ->
                                        scaledBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }

                                    // Clean up scaled bitmap if it's different from original
                                    if (scaledBitmap != bitmap && !scaledBitmap.isRecycled) {
                                        scaledBitmap.recycle()
                                    }

                                    val tempUri = Uri.fromFile(tempFile)
                                    Log.d("CameraScreen", "Launching crop for: $tempUri")
                                    val cropIntent = ImageCropHelper.createCropIntent(context, tempUri)
                                    cropLauncher.launch(cropIntent.getIntent(context))
                                } catch (e: Exception) {
                                    Log.e("CameraScreen", "Error saving captured image", e)
                                    onTextExtracted(OcrResult("", false, "Error processing camera image: ${e.localizedMessage}"))
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Gallery mode placeholder
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                modifier = Modifier.size(120.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.camera_select_from_gallery),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    currentCameraMode = true // Switch back to camera mode
                                    galleryLauncher.launch("image/*")
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(56.dp)
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.camera_choose_image))
                            }
                        }
                    }
                }

                // Processing overlay
                if (isProcessing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.camera_processing_status),
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }

                // Image source selection dialog
                if (showImageSourceDialog) {
                    AlertDialog(
                        onDismissRequest = { showImageSourceDialog = false },
                        title = { Text(stringResource(R.string.camera_select_source_title)) },
                        text = { Text(stringResource(R.string.camera_select_source_desc)) },
                        confirmButton = {
                            Row {
                                TextButton(
                                    onClick = {
                                        currentCameraMode = true
                                        showImageSourceDialog = false
                                    }
                                ) {
                                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.camera_source_camera))
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                TextButton(
                                    onClick = {
                                        showImageSourceDialog = false
                                        galleryLauncher.launch("image/*")
                                    }
                                ) {
                                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.camera_source_gallery))
                                }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showImageSourceDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    )
                }

                // Processing options dialog
                if (showProcessingOptions) {
                    AlertDialog(
                        onDismissRequest = { showProcessingOptions = false },
                        title = { Text(stringResource(R.string.camera_processing_options)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.camera_enhance_image))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = enablePreprocessing,
                                        onCheckedChange = { enablePreprocessing = it }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        stringResource(R.string.camera_enable_preprocessing),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                if (enablePreprocessing) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "✓ Contrast enhancement\n✓ Sharpening\n✓ Noise reduction\n✓ Higher resolution (2048px)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showProcessingOptions = false }) {
                                Text("OK")
                            }
                        }
                    )
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(R.string.camera_permission_required),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.camera_permission_description),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { cameraPermissionState.launchPermissionRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    stringResource(R.string.camera_grant_permission),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val preview = remember {
        CameraPreview.Builder().build()
    }
    val imageCapture = remember {
        androidx.camera.core.ImageCapture.Builder().build()
    }
    val cameraSelector = remember {
        CameraSelector.DEFAULT_BACK_CAMERA
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx: Context ->
                val previewView = PreviewView(ctx)

                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        preview.setSurfaceProvider(previewView.surfaceProvider)

                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageCapture
                        )
                    },
                    ContextCompat.getMainExecutor(ctx)
                )

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        FloatingActionButton(
            onClick = { captureImage(imageCapture, context, onImageCaptured) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.Camera,
                contentDescription = stringResource(R.string.camera_capture_button_desc),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun captureImage(
    imageCapture: androidx.camera.core.ImageCapture,
    context: Context,
    onImageCaptured: (Bitmap) -> Unit
) {
    val outputFile = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
    val outputFileOptions = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(outputFile).build()

    imageCapture.takePicture(
        outputFileOptions,
        Executors.newSingleThreadExecutor(),
        object : androidx.camera.core.ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: androidx.camera.core.ImageCapture.OutputFileResults) {
                try {
                    val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                    if (bitmap != null) {
                        Log.d("CameraScreen", "Captured bitmap: ${bitmap.width}x${bitmap.height}")
                        onImageCaptured(bitmap)
                    }
                    outputFile.delete()
                } catch (e: Exception) {
                    // Handle image loading error
                }
            }

            override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                // Handle capture error
            }
        }
    )
}

@ComposePreview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    OCRScannerTheme {
        CameraScreen(onNavigateBack = {}, onTextExtracted = {})
    }
}
