package com.example.pdftoolkit.core.pdf

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Ek recognized text line + bitmap pixels mein uska box (searchable PDF ke liye). */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

/** On-device OCR (ML Kit, offline, Latin script). */
object OcrEngine {

    suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
        }
    }

    suspend fun recognizeLines(bitmap: Bitmap): List<OcrLine> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            return result.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { OcrLine(line.text, it.left, it.top, it.right, it.bottom) }
            }
        } finally {
            recognizer.close()
        }
    }
}