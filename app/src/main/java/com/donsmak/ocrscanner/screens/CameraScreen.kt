package com.donsmak.ocrscanner.screens

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.donsmak.ocrscanner.ui.theme.OCRScannerTheme
import com.donsmak.ocrscanner.utils.OcrEngine
import com.donsmak.ocrscanner.utils.OcrResult
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.launch
import android.util.Log
import androidx.compose.ui.res.stringResource
import com.donsmak.ocrscanner.R
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    onNavigateBack: () -> Unit,
    onTextExtracted: (OcrResult) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val ocrEngine = remember { OcrEngine() }

    if (cameraPermissionState.status.isGranted) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.camera_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                CameraPreview(
                    onImageCaptured = { bitmap ->
                        scope.launch {
                            isProcessing = true
                            val result = ocrEngine.extractText(
                                bitmap = bitmap,
                                enhanceImage = true
                            )
                            isProcessing = false
                            onTextExtracted(result)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

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
        androidx.camera.core.Preview.Builder().build()
    }
    val imageCapture = remember {
        androidx.camera.core.ImageCapture.Builder().build()
    }
    val cameraSelector = remember {
        androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
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

@Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    OCRScannerTheme {
        CameraScreen(onNavigateBack = {}, onTextExtracted = {})
    }
}
