package com.optiscan.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Service wrapper for on-device OCR using Google ML Kit Text Recognition.
 */
class OcrService(
    recognizer: TextRecognizer? = null
) {
    private val recognizer: TextRecognizer by lazy {
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * Recognize printed text from an in-memory Bitmap.
     *
     * @param bitmap Image to inspect for text.
     * @return Result containing extracted string or exception.
     */
    suspend fun recognizeText(bitmap: Bitmap): Result<String> = suspendCancellableCoroutine { continuation ->
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val text = visionText.text.trim()
                    if (text.isEmpty()) {
                        continuation.resume(Result.success("No printed text detected in the image."))
                    } else {
                        continuation.resume(Result.success(text))
                    }
                }
                .addOnFailureListener { exception ->
                    continuation.resume(Result.failure(exception))
                }
        } catch (e: Exception) {
            continuation.resume(Result.failure(e))
        }
    }

    /**
     * Release underlying ML Kit resources when no longer needed.
     */
    fun close() {
        recognizer.close()
    }
}
