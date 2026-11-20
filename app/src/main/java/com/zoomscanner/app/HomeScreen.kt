package com.zoomscanner.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(app: AppState) {
    val ctx = app.ctx
    var qrChoice by remember { mutableStateOf(false) }
    var importChoice by remember { mutableStateOf(false) }
    var compressChoice by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }

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
        when (t) {
            Tool.SCAN -> app.push(Screen.ScanModes)
            Tool.ID_PHOTO -> app.toast("La photo d'identité arrive dans la prochaine mise à jour")
            Tool.QR -> qrChoice = true
            Tool.IMPORT -> importChoice = true
            Tool.IMAGES -> app.push(Screen.ImagesTool)
            Tool.COMPRESS -> compressChoice = true
            else -> app.push(Screen.Pick(t))
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            if (app.homeTab == 0) DocsTab(app) else ToolsTab(app) { openTool(it) }
        }
        BottomBar(app.homeTab, { app.homeTab = it }) { app.push(Screen.ScanModes) }
    }

    if (qrChoice) {
        ChoiceDialog(
            title = "QR code",
            options = listOf("Scanner un code", "Créer un code"),
            onDismiss = { qrChoice = false }
        ) { i ->
            qrChoice = false
            if (i == 0) startQr() else app.push(Screen.QrCreate)
        }
    }
    if (importChoice) {
        ChoiceDialog(
            title = "Importer",
            options = listOf("Des images (galerie)", "Des fichiers (PDF ou images)"),
            onDismiss = { importChoice = false }
        ) { i ->
            importChoice = false
            if (i == 0) importImages.launch("image/*")
            else importFiles.launch(arrayOf("application/pdf", "image/*"))
        }
    }
    if (compressChoice) {
        ChoiceDialog(
            title = "Compresser",
            options = listOf("Un document PDF", "Des images"),
            onDismiss = { compressChoice = false }
        ) { i ->
            compressChoice = false
            if (i == 0) app.push(Screen.Pick(Tool.COMPRESS)) else app.push(Screen.ImagesTool)
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

@Composable
fun ChoiceDialog(title: String, options: List<String>, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, label ->
                    Text(
                        label,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(i) }
                            .padding(horizontal = 8.dp, vertical = 14.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("ZoomScanner", text))
}

// ------------------------------------------------------------------ Barre du bas

@Composable
fun BottomBar(tab: Int, onTab: (Int) -> Unit, onScan: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(110.dp)) {
        Surface(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(84.dp),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(Modifier.fillMaxSize().padding(top = 10.dp), verticalAlignment = Alignment.Top) {
                NavItem(Modifier.weight(1f), "file", "Documents", tab == 0) { onTab(0) }
                Spacer(Modifier.weight(1f))
                NavItem(Modifier.weight(1f), "grid", "Outils", tab == 1) { onTab(1) }
            }
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(60.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(Brand)
                .clickable(onClick = onScan),
            contentAlignment = Alignment.Center
        ) {
            Glyph("cam", OnBrand, 28.dp, 2f)
        }
    }
}

@Composable
fun NavItem(modifier: Modifier, icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val col = if (selected) Brand else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(64.dp, 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) Brand.copy(alpha = 0.18f) else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Glyph(icon, col, 24.dp)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = col)
    }
}

// ------------------------------------------------------------------ Documents

private fun dayLabel(ms: Long): String {
    val now = Calendar.getInstance()
    val d = Calendar.getInstance().apply { timeInMillis = ms }
    fun same(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    if (same(now, d)) return "Aujourd'hui"
    now.add(Calendar.DAY_OF_YEAR, -1)
    if (same(now, d)) return "Hier"
    return SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(ms))
}

@Composable
fun DocsTab(app: AppState) {
    val ctx = app.ctx
    var query by remember { mutableStateOf("") }
    val docs by produceState(emptyList<File>(), app.refresh, query) {
        value = withContext(Dispatchers.IO) {
            if (query.isBlank()) Lib.list(ctx) else Lib.search(ctx, query)
        }
    }
    val groups = remember(docs) { docs.groupBy { dayLabel(it.lastModified()) } }

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Documents",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                GearButton { app.push(Screen.Settings) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchField(query, { query = it }, "Rechercher dans les documents")
        }
        if (query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Shortcut(Modifier.weight(1f), "star", "Favoris", Color(0xFFFBBF24)) { app.push(Screen.Favorites) }
                    Shortcut(Modifier.weight(1f), "folder", "Dossiers", Color(0xFF60A5FA)) { app.push(Screen.Folders) }
                    Shortcut(Modifier.weight(1f), "trash", "Corbeille", Color(0xFF9FB3A9)) { app.push(Screen.Trash) }
                }
            }
        }
        if (docs.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    if (query.isBlank()) "Aucun document pour l'instant.\nTouchez le bouton caméra pour numériser votre premier document."
                    else "Aucun document ne correspond à « $query ».",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                )
            }
        }
        for ((label, list) in groups) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(label, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            items(list, key = { it.absolutePath }) { f ->
                DocCard(app, f) { app.push(Screen.Viewer(f)) }
            }
        }
    }
}

@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Glyph("sliders", MaterialTheme.colorScheme.onSurface, 20.dp)
    }
}

@Composable
fun Shortcut(modifier: Modifier, icon: String, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Glyph(icon, color, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DocCard(app: AppState, f: File, onClick: () -> Unit) {
    val pages by produceState(0, f.absolutePath, f.lastModified()) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(f) }
    }
    val fav = Lib.isFav(app.ctx, f)
    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box {
            PdfThumb(
                f, 360,
                Modifier.fillMaxWidth().height(148.dp).clip(RoundedCornerShape(14.dp)),
                contentScale = ContentScale.Crop
            )
            if (fav) {
                Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                    Glyph("star", Color(0xFFFBBF24), 18.dp, 1.8f, filled = true)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            f.nameWithoutExtension,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        val pageInfo = when {
            pages > 1 -> "$pages pages · "
            pages == 1 -> "1 page · "
            pages < 0 -> "protégé · "
            else -> ""
        }
        Text(
            pageInfo + Lib.sizeText(f.length()),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
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
            contentScale = contentScale,
            alignment = Alignment.TopCenter
        )
    } else {
        Box(modifier.background(Color(0xFFE0E0E0)), contentAlignment = Alignment.Center) {
            Text("PDF", color = Color.Gray, fontSize = 12.sp)
        }
    }
}

// ------------------------------------------------------------------ Outils

@Composable
fun ToolsTab(app: AppState, onTool: (Tool) -> Unit) {
    var query by remember { mutableStateOf("") }
    val q = norm(query.trim())
    val tools = Tool.entries.filter { q.isEmpty() || norm(it.label).contains(q) }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Outils",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                GearButton { app.push(Screen.Settings) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchField(query, { query = it }, "Rechercher un outil")
        }
        items(tools, key = { it.name }) { t -> ToolTile(t) { onTool(t) } }
    }
}

@Composable
fun ToolTile(t: Tool, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(t.color, t.icon, 48.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            t.label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 17.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
