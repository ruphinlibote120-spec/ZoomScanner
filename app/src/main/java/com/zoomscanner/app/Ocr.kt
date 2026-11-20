package com.zoomscanner.app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

/** Reconnaissance de texte (alphabet latin : français, anglais…), directement sur le téléphone. */
object Ocr {
    suspend fun read(bmp: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            return recognizer.process(InputImage.fromBitmap(bmp, 0)).await().text
        } finally {
            recognizer.close()
        }
    }
}
