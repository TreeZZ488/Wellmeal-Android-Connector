package com.wellmeal.connector

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

private const val TAG = "PantryScreen"

@Composable
fun PantryScreen(
    ocrRepository: ReceiptOcrRepository? = null,
    documentScanner: GmsDocumentScanner? = null
) {
    val context = LocalContext.current
    val repository = remember(ocrRepository) {
        ocrRepository ?: ReceiptOcrRepository(context)
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var recognizedLines by remember { mutableStateOf<List<String>?>(null) }
    var ocrMode by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()

    // Baseline: Android system photo picker launcher (no broad storage/camera permissions required)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                isLoading = true
                errorMessage = null
                ocrMode = ReceiptOcrRepository.MODE_BASELINE
                try {
                    recognizedLines = repository.recognizeText(uri)
                } catch (e: Exception) {
                    Log.e(TAG, "Receipt baseline OCR processing failed: ${e.message}")
                    errorMessage = e.message ?: "Failed to process receipt image"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    // Enhanced: ML Kit Document Scanner result launcher
    val scannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                val pageUri = scanningResult?.pages?.firstOrNull()?.imageUri
                if (pageUri != null) {
                    scope.launch {
                        isLoading = true
                        errorMessage = null
                        ocrMode = ReceiptOcrRepository.MODE_ENHANCED_JAPANESE
                        try {
                            recognizedLines = repository.recognizeTextJapaneseOnly(pageUri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Receipt enhanced Japanese OCR processing failed: ${e.message}")
                            errorMessage = e.message ?: "Failed to process receipt image"
                        } finally {
                            isLoading = false
                        }
                    }
                } else {
                    Log.e(TAG, "No JPEG page returned from document scanner")
                    errorMessage = "No scanned page returned by document scanner"
                }
            }
            Activity.RESULT_CANCELED -> {
                Log.d(TAG, "Document scanner was cancelled by user")
            }
            else -> {
                Log.e(TAG, "Document scanner failed with result code: ${result.resultCode}")
                errorMessage = "Document scanner failed with result code: ${result.resultCode}"
            }
        }
    }

    val scanner = remember(documentScanner) {
        documentScanner ?: run {
            val options = GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(1)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
            GmsDocumentScanning.getClient(options)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Pantry",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Read Receipt")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val activity = context.findActivity()
                if (activity == null) {
                    Log.e(TAG, "Cannot start document scanner: Activity context not available")
                    errorMessage = "Cannot start document scanner: Activity not available"
                    return@Button
                }

                try {
                    scanner.getStartScanIntent(activity)
                        .addOnSuccessListener { intentSender ->
                            try {
                                scannerLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to launch document scanner: ${e.message}")
                                errorMessage = e.message ?: "Failed to launch document scanner"
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "Failed to start document scanner: ${e.message}")
                            errorMessage = e.message ?: "Failed to start document scanner"
                        }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start document scanner: ${e.message}")
                    errorMessage = e.message ?: "Failed to start document scanner"
                }
            },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Scan Receipt (Enhanced)")
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Processing receipt...",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (errorMessage != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Error",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        if (!isLoading && recognizedLines != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Recognized Receipt Text",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (ocrMode != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "OCR Mode: $ocrMode",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val lines = recognizedLines
                    if (lines.isNullOrEmpty()) {
                        Text(
                            text = "No text detected in receipt.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        lines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is Activity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}

