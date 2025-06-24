package com.donsmak.ocrscanner

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.donsmak.ocrscanner.screens.CameraScreen
import com.donsmak.ocrscanner.screens.HomeScreen
import com.donsmak.ocrscanner.screens.ResultScreen
import com.donsmak.ocrscanner.ui.theme.OCRScannerTheme
import com.donsmak.ocrscanner.utils.OcrResult

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OCRScannerTheme {
                val navController = rememberNavController()
                var ocrResult by remember { mutableStateOf<OcrResult?>(null) }

                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(
                            onNavigateToCamera = { navController.navigate("camera") }
                        )
                    }
                    composable("camera") {
                        CameraScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onTextExtracted = { result ->
                                ocrResult = result
                                navController.navigate("result")
                            }
                        )
                    }
                    composable("result") {
                        ocrResult?.let { result ->
                            ResultScreen(
                                result = result,
                                onTryAgain = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
