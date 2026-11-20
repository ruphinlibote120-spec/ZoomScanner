package com.zoomscanner.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(app: AppState) {
    val ctx = app.ctx
    var tab by remember { mutableIntStateOf(0) }
    var qr by remember { mutableStateOf<String?>(null) }

    // --- Scanner classique ---
    val scan = rememberScanLauncher(30) { pdf, _ ->
        if (pdf != null) {
            app.task("Enregistrement…") {
                val f = withContext(Dispatchers.IO) { Lib.importUri(ctx, pdf, "Scan", false) }
                app.showResult(f)
            }
        }
    }

    // --- Carte d'identité : recto + verso sur une page ---
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

    // --- Horodatage ---
    val stampScan = rememberScanLauncher(30) { _, pages ->
        if (pages.isNotEmpty()) {
            app.task("Ajout de la date et de l'heure…") {
                val f = withContext(Dispatchers.IO) {
                    val out = Lib.newFile(ctx, "Scan horodaté")
                    Pdf.imagesToPdf(ctx, pages, out, true)
                    out
                }
                app.showResult(f)
            }
        }
    }

    // --- Importer des images (galerie) ---
    val importImages = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            app.task("Création du PDF…") {
                val f = withContext(Dispatchers.IO) {
                    val out = Lib.newFile(ctx, "Images")
                    Pdf.imagesToPdf(ctx, uris, out)
                    out
                }
                app.showResult(f)
            }
        }
    }

    // --- Importer des fichiers (PDF et images) ---
    val importFiles = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            app.task("Importation…") {
                val last = withContext(Dispatchers.IO) {
                    var result: File? = null
                    val imgs = ArrayList<Uri>()
                    for (u in uris) {
                        val type = ctx.contentResolver.getType(u) ?: ""
                        if (type == "application/pdf") {
                            result = Lib.importUri(ctx, u, "Document")
                        } else if (type.startsWith("image/")) {
                            imgs.add(u)
                        }
                    }
                    if (imgs.isNotEmpty()) {
                        val out = Lib.newFile(ctx, "Images")
                        Pdf.imagesToPdf(ctx, imgs, out)
                        result = out
                    }
                    result
                }
                if (last != null) app.showResult(last) else app.toast("Aucun PDF ou image reconnu")
            }
        }
    }

    fun startQr() {
        GmsBarcodeScanning.getClient(ctx).startScan()
            .addOnSuccessListener { b -> qr = b.rawValue ?: b.displayValue ?: "" }
            .addOnFailureListener { e -> app.toast("Lecteur indisponible : ${e.message}") }
    }

    fun openTool(t: Tool) {
        if (!t.ready) {
            app.toast("« ${t.label} » arrive dans une prochaine version")
            return
        }
        when (t) {
            Tool.SCAN -> scan()
            Tool.ID_CARD -> idScan()
            Tool.TIMESTAMP -> stampScan()
            Tool.IMPORT_IMAGES -> importImages.launch("image/*")
            Tool.IMPORT_FILES -> importFiles.launch(arrayOf("application/pdf", "image/*"))
            Tool.QR -> startQr()
            else -> app.push(Screen.Pick(t))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (tab == 0) "ZoomScanner" else "Outils",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("🗂️") },
                    label = { Text("Documents") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("🧰") },
                    label = { Text("Outils") }
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { scan() },
                    icon = { Text("📷") },
                    text = { Text("Scanner") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (tab == 0) DocsTab(app) else ToolsTab { openTool(it) }
        }
    }

    val text = qr
    if (text != null) {
        AlertDialog(
            onDismissRequest = { qr = null },
            title = { Text("Code scanné") },
            text = { Text(text.ifEmpty { "(vide)" }) },
            confirmButton = {
                TextButton(onClick = {
                    copyText(ctx, text)
                    app.toast("Copié")
                    qr = null
                }) { Text("Copier") }
            },
            dismissButton = {
                Row {
                    if (text.startsWith("http://") || text.startsWith("https://")) {
                        TextButton(onClick = {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(text))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (e: Exception) {
                                app.toast("Impossible d'ouvrir le lien")
                            }
                            qr = null
                        }) { Text("Ouvrir") }
                    }
                    TextButton(onClick = { qr = null }) { Text("Fermer") }
                }
            }
        )
    }
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ZoomScanner", text))
}

@Composable
fun DocsTab(app: AppState) {
    val ctx = app.ctx
    val docs by produceState(emptyList<File>(), app.refresh) {
        value = withContext(Dispatchers.IO) { Lib.list(ctx) }
    }
    if (docs.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Aucun document pour l'instant.\nTouchez « Scanner » pour numériser votre premier document.",
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
            items(docs, key = { it.absolutePath }) { f ->
                DocRow(f) { app.push(Screen.Viewer(f)) }
            }
        }
    }
}

@Composable
fun DocRow(f: File, onClick: () -> Unit) {
    val pages by produceState(0, f.absolutePath, f.lastModified()) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(f) }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PdfThumb(
            f, 240,
            Modifier.width(56.dp).height(72.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                f.nameWithoutExtension,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            val pageInfo = when {
                pages > 1 -> "$pages pages · "
                pages == 1 -> "1 page · "
                pages < 0 -> "🔒 protégé · "
                else -> ""
            }
            Text(
                pageInfo + Lib.sizeText(f.length()) + " · " + Lib.dateText(f.lastModified()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Miniature d'une page de PDF. */
@Composable
fun PdfThumb(
    file: File,
    widthPx: Int,
    modifier: Modifier = Modifier,
    page: Int = 0,
    contentScale: ContentScale = ContentScale.FillWidth
) {
    val bmp by produceState<android.graphics.Bitmap?>(null, file.absolutePath, file.lastModified(), page) {
        value = withContext(Dispatchers.IO) { Pdf.renderPageOf(file, page, widthPx) }
    }
    val b = bmp
    if (b != null) {
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier.background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) {
            Text("PDF", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

@Composable
fun ToolsTab(onTool: (Tool) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        for (sec in SECTIONS) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    sec.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 4.dp)
                )
            }
            items(sec.tools) { t -> ToolCell(t) { onTool(t) } }
        }
    }
}

@Composable
fun ToolCell(t: Tool, onClick: () -> Unit) {
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp)
            .alpha(if (t.ready) 1f else 0.45f),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(t.icon, fontSize = 24.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            t.label,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            textAlign = TextAlign.Center,
            maxLines = 3
        )
        if (!t.ready) {
            Text("Bientôt", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}
