package com.donsmak.ocrscanner.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.donsmak.ocrscanner.R
import com.donsmak.ocrscanner.ui.theme.OCRScannerTheme
import com.donsmak.ocrscanner.utils.FileUtils
import com.donsmak.ocrscanner.utils.OcrResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(result: OcrResult, onTryAgain: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = onTryAgain) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = stringResource(id = R.string.result_try_again)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(id = R.string.result_extracted_text),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = if (result.text.isNotBlank()) result.text else stringResource(
                            id = R.string.result_no_text_detected
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = if (result.hasArabic) FontFamily.Serif else FontFamily.Default
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The new "Share / Save" button
            Button(
                onClick = {
                    // Create the .docx file and get its URI
                    val fileUri = FileUtils.createAndSaveDocx(context, result.text)

                    if (fileUri != null) {
                        // Create the Share Intent
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_STREAM, fileUri)
                            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        // Launch the system's share sheet
                        val chooser = Intent.createChooser(shareIntent, "Share Document via...")
                        context.startActivity(chooser)
                    } else {
                        // Show an error message if the file creation failed
                        Toast.makeText(context, "Error: Could not create document.", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = result.text.isNotBlank() // Disable button if there's no text
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(id = R.string.result_share_button))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onTryAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = stringResource(id = R.string.result_try_again))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ResultScreenPreview() {
    OCRScannerTheme {
        ResultScreen(
            result = OcrResult(
                text = "Sample Arabic Text\nمرحبا بالعالم",
                confidence = 0.9f,
                method = "Google Cloud Vision",
                language = "Arabic & Latin",
                processingTime = 500,
                wordCount = 4,
                hasArabic = true,
                hasLatin = true
            ),
            onTryAgain = {}
        )
    }
}
