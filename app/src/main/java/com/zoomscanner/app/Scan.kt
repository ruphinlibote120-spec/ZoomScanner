package com.zoomscanner.app

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

/**
 * Prépare le scanner de documents Google (caméra en direct, détection des bords, recadrage, filtres,
 * import depuis la galerie). Retourne une fonction à appeler pour l'ouvrir.
 * onResult reçoit l'adresse du PDF et celles des pages en images JPEG.
 */
@Composable
fun rememberScanLauncher(pageLimit: Int, onResult: (Uri?, List<Uri>) -> Unit): () -> Unit {
    val ctx = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val r = GmsDocumentScanningResult.fromActivityResultIntent(res.data)
            if (r != null) {
                val pages = r.pages?.map { it.imageUri } ?: emptyList()
                callback(r.pdf?.uri, pages)
            }
        }
    }
    return {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(
                GmsDocumentScannerOptions.RESULT_FORMAT_JPEG,
                GmsDocumentScannerOptions.RESULT_FORMAT_PDF
            )
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(ctx as Activity)
            .addOnSuccessListener { sender ->
                launcher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    ctx,
                    "Scanner indisponible (Google Play Services requis) : ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        Unit
    }
}
