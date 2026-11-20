package com.zoomscanner.app

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private class ScanMode(val label: String, val icon: String, val color: Long, val desc: String)

private val MODES = listOf(
    ScanMode("Document", "Scanner", 0xFF2563EB, "Une ou plusieurs pages, bords détectés"),
    ScanMode("Lot", "Scan en lot", 0xFF3B82F6, "Plusieurs documents à la suite"),
    ScanMode("Carte d'identité", "Cartes d'identité", 0xFF4F46E5, "Recto et verso sur une page"),
    ScanMode("Livre", "Livre", 0xFF0369A1, "Une double page devient deux pages"),
    ScanMode("Diapositives", "Diapositives", 0xFF1D4ED8, "Présentations et écrans"),
    ScanMode("Tableau blanc", "Tableau blanc", 0xFF4338CA, "Fond éclairci, texte net")
)

/** Choix du mode de numérisation, puis ouverture du scanner Google (caméra, bords, recadrage, galerie). */
@Composable
fun ScanModesScreen(app: AppState) {
    val ctx = app.ctx

    // Document
    val doc = rememberScanLauncher(30) { pdf, _ ->
        if (pdf != null) {
            app.task("Enregistrement…") {
                val f = withContext(Dispatchers.IO) { Lib.importUri(ctx, pdf, "Scan", false) }
                app.showResult(f)
            }
        }
    }

    // Lot : chaque scan est enregistré, puis le scanner se rouvre pour le suivant (jusqu'à ce que vous fermiez)
    val batchRef = remember { mutableStateOf<() -> Unit>({}) }
    var batchCount by remember { mutableIntStateOf(0) }
    val batch = rememberScanLauncher(30) { pdf, _ ->
        if (pdf != null) {
            app.task("Enregistrement…") {
                withContext(Dispatchers.IO) { Lib.importUri(ctx, pdf, "Scan", false) }
                batchCount++
                app.refresh++
                app.toast("Document $batchCount enregistré. Scannez le suivant, ou fermez le scanner pour terminer.")
                batchRef.value()
            }
        }
    }
    batchRef.value = batch

    // Carte d'identité
    val idScan = rememberScanLauncher(2) { _, pages ->
        if (pages.isNotEmpty()) {
            app.task("Création de la carte…") {
                val f = withContext(Dispatchers.IO) {
                    val front = Img.decode(ctx, pages[0], 1800)
                    val back = if (pages.size > 1) Img.decode(ctx, pages[1], 1800) else null
                    val page = Img.idCardPage(front, back)
                    front.recycle()
                    back?.recycle()
                    val out = Lib.newFile(ctx, "Carte d'identité")
                    Pdf.bitmapsToPdf(ctx, out, 1) { page }
                    out
                }
                app.showResult(f)
            }
        }
    }

    // Livre : chaque photo de double page est coupée en deux pages
    val book = rememberScanLauncher(30) { _, pages ->
        if (pages.isNotEmpty()) {
            app.task("Découpage des pages…") {
                val f = withContext(Dispatchers.IO) {
                    val out = Lib.newFile(ctx, "Livre")
                    Pdf.bitmapsToPdf(ctx, out, pages.size * 2) { i ->
                        val b = Img.decode(ctx, pages[i / 2], 2400)
                        val h = Img.half(b, i % 2 == 0)
                        b.recycle()
                        h
                    }
                    out
                }
                app.showResult(f)
            }
        }
    }

    // Diapositives
    val slides = rememberScanLauncher(60) { pdf, _ ->
        if (pdf != null) {
            app.task("Enregistrement…") {
                val f = withContext(Dispatchers.IO) { Lib.importUri(ctx, pdf, "Diapositives", false) }
                app.showResult(f)
            }
        }
    }

    // Tableau blanc
    val board = rememberScanLauncher(30) { _, pages ->
        if (pages.isNotEmpty()) {
            app.task("Amélioration du tableau…") {
                val f = withContext(Dispatchers.IO) {
                    val out = Lib.newFile(ctx, "Tableau blanc")
                    Pdf.bitmapsToPdf(ctx, out, pages.size) { i ->
                        val b = Img.decode(ctx, pages[i], 2200)
                        val w = Img.whiteboard(b)
                        b.recycle()
                        w
                    }
                    out
                }
                app.showResult(f)
            }
        }
    }

    val actions = listOf<() -> Unit>(
        { doc() },
        { batchCount = 0; batch() },
        { idScan() },
        { book() },
        { slides() },
        { board() }
    )

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Scanner", { app.back() })
        Text(
            "Choisissez ce que vous numérisez. L'appareil photo s'ouvre ensuite, avec détection des bords et import depuis la galerie.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(MODES.size) { i ->
                val m = MODES[i]
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { actions[i]() }
                        .padding(16.dp)
                        .fillMaxWidth()
                        .height(128.dp)
                ) {
                    IconTile(m.color, m.icon, 48.dp)
                    Spacer(Modifier.height(10.dp))
                    Text(m.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        m.desc,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Créer un QR code

private fun makeQr(text: String, size: Int = 768): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        for (x in 0 until size) {
            pixels[y * size + x] = if (m.get(x, y)) AColor.BLACK else AColor.WHITE
        }
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

@Composable
fun QrCreateScreen(app: AppState) {
    val ctx = app.ctx
    var text by remember { mutableStateOf("") }
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenBar("Créer un QR code", { app.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; qr = null },
                label = { Text("Texte ou lien à transformer en QR code") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                enabled = text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    try {
                        qr = makeQr(text.trim())
                    } catch (e: Exception) {
                        app.toast("Texte trop long pour un QR code")
                    }
                }
            ) { Text("Créer le QR code") }
            val b = qr
            if (b != null) {
                Image(
                    bitmap = b.asImageBitmap(),
                    contentDescription = "QR code",
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            app.task("Enregistrement…") {
                                val n = withContext(Dispatchers.IO) { Lib.saveImage(ctx, b, "QR code", 1, 100) }
                                app.toast(if (n > 0) "QR code enregistré dans la galerie (ZoomScanner)" else "Échec de l'enregistrement")
                            }
                        }
                    ) { Text("Dans la galerie") }
                    Button(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            app.task("Création du PDF…") {
                                val f = withContext(Dispatchers.IO) {
                                    val out = Lib.newFile(ctx, "QR code")
                                    Pdf.bitmapsToPdf(ctx, out, 1) { makeQr(text.trim(), 1000) }
                                    out
                                }
                                app.showResult(f)
                            }
                        }
                    ) { Text("En PDF") }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ Images : convertir, redimensionner, compresser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesScreen(app: AppState) {
    val ctx = app.ctx
    var uris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var format by remember { mutableIntStateOf(0) }
    var size by remember { mutableIntStateOf(0) }
    var quality by remember { mutableFloatStateOf(0.85f) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { r ->
        if (r.isNotEmpty()) uris = r
    }
    val formats = listOf("JPG", "PNG", "WebP")
    val sizes = listOf("Taille d'origine" to 100000, "Grande (2048 px)" to 2048, "Moyenne (1280 px)" to 1280, "Petite (800 px)" to 800)

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Images", { app.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                "Convertissez le format, réduisez la taille ou compressez vos photos. Les résultats vont dans la galerie, dossier « ZoomScanner ».",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = { pick.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (uris.isEmpty()) "Choisir des images" else "${uris.size} image(s) choisie(s) · changer")
            }
            Text("Format", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in formats.indices) {
                    FilterChip(selected = format == i, onClick = { format = i }, label = { Text(formats[i]) })
                }
            }
            Text("Taille", fontWeight = FontWeight.SemiBold)
            for (i in sizes.indices) {
                Row(
                    Modifier.fillMaxWidth().clickable { size = i },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = size == i, onClick = { size = i })
                    Text(sizes[i].first)
                }
            }
            if (format != 1) {
                Text("Qualité : ${(quality * 100).roundToInt()} %", fontWeight = FontWeight.SemiBold)
                Slider(value = quality, onValueChange = { quality = it }, valueRange = 0.3f..1f)
                Text(
                    "Plus la qualité est basse, plus le fichier est léger.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                enabled = uris.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    app.task("Conversion…") {
                        val list = uris
                        val total = withContext(Dispatchers.IO) {
                            var bytes = 0L
                            list.forEachIndexed { i, u ->
                                app.busy = "Image ${i + 1} / ${list.size}…"
                                val b = Img.decode(ctx, u, sizes[size].second)
                                val n = Lib.saveImage(ctx, b, "Image ${i + 1}", format, (quality * 100).roundToInt())
                                b.recycle()
                                if (n > 0) bytes += n
                            }
                            bytes
                        }
                        app.toast("${list.size} image(s) enregistrée(s) · ${Lib.sizeText(total)} au total")
                    }
                }
            ) { Text("Convertir et enregistrer") }
        }
    }
}
