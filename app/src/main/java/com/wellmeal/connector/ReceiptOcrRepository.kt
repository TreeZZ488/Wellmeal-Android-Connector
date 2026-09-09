package com.wellmeal.connector

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ReceiptOcrRepository(
    private val context: Context
) {
    /**
     * Processes an image at the given Uri using both Japanese and Latin ML Kit text recognizers.
     * Returns a list of recognized raw text lines with exact duplicates between the two recognizers removed.
     */
    suspend fun recognizeText(imageUri: Uri): List<String> = withContext(Dispatchers.IO) {
        val inputImage = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create InputImage from URI: ${e.message}")
            throw e
        }

        val japaneseRecognizer = TextRecognition.getClient(
            JapaneseTextRecognizerOptions.Builder().build()
        )
        val latinRecognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

        try {
            // Run both Japanese and Latin text recognizers concurrently
            val (japaneseResult, latinResult) = coroutineScope {
                val japaneseDeferred = async { japaneseRecognizer.processImage(inputImage) }
                val latinDeferred = async { latinRecognizer.processImage(inputImage) }
                Pair(japaneseDeferred.await(), latinDeferred.await())
            }

            val japaneseLines = extractLines(japaneseResult)
            val latinLines = extractLines(latinResult)

            val combinedLines = combineRecognizedLines(japaneseLines, latinLines)

            // Note: Do not log recognized text to protect private receipt contents
            Log.d(TAG, "Receipt OCR completed successfully. Recognized line count: ${combinedLines.size}")
            combinedLines
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Receipt OCR processing failed: ${e.message}")
            throw e
        } finally {
            try {
                japaneseRecognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close Japanese recognizer: ${e.message}")
            }
            try {
                latinRecognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close Latin recognizer: ${e.message}")
            }
        }
    }

    private fun extractLines(text: Text): List<String> {
        val lines = mutableListOf<String>()
        for (block in text.textBlocks) {
            if (block.lines.isNotEmpty()) {
                for (line in block.lines) {
                    val trimmed = line.text.trim()
                    if (trimmed.isNotEmpty()) {
                        lines.add(trimmed)
                    }
                }
            } else {
                for (line in block.text.lines()) {
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        lines.add(trimmed)
                    }
                }
            }
        }
        if (lines.isEmpty() && text.text.isNotBlank()) {
            for (line in text.text.lines()) {
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    lines.add(trimmed)
                }
            }
        }
        return lines
    }

    private suspend fun TextRecognizer.processImage(image: InputImage): Text =
        suspendCancellableCoroutine { continuation ->
            process(image)
                .addOnSuccessListener { text ->
                    if (continuation.isActive) {
                        continuation.resume(text)
                    }
                }
                .addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }
        }

    companion object {
        private const val TAG = "ReceiptOcrRepository"

        /**
         * Combines lines recognized by Japanese and Latin recognizers.
         * Preserves Japanese order and appends lines from Latin that are not exact duplicates.
         */
        internal fun combineRecognizedLines(
            japaneseLines: List<String>,
            latinLines: List<String>
        ): List<String> {
            val combinedLines = mutableListOf<String>()
            combinedLines.addAll(japaneseLines)

            val japaneseExactLines = japaneseLines.toSet()
            for (line in latinLines) {
                if (line !in japaneseExactLines) {
                    combinedLines.add(line)
                }
            }
            return combinedLines
        }
    }
}
