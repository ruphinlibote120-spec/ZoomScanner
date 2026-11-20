package com.zoomscanner.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Cadre commun des écrans de travail (barre du haut + retour), puis aiguillage vers l'outil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkScreen(app: AppState, tool: Tool, files: List<File>) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.label) },
                navigationIcon = {
                    IconButton(onClick = { app.back() }) { Text("←", fontSize = 22.sp) }
                }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val f = files.first()
            when (tool) {
                Tool.OCR -> OcrWork(app, f)
                Tool.PASSWORD -> PasswordWork(app, f)
                Tool.COMPRESS -> CompressWork(app, f)
                Tool.STAMPS -> StampsWork(app, f)
                Tool.ANNOTATE -> SignWork(app, f)
                Tool.MERGE -> MergeWork(app, files)
                Tool.PAGES -> ReorderWork(app, f)
                Tool.PDF_TO_IMAGES -> ToImagesWork(app, f)
                else -> Text("Outil indisponible", Modifier.padding(16.dp))
            }
        }
    }
}

// ---------------------------------------------------------------- Extraire le texte (OCR)

@Composable
fun OcrWork(app: AppState, file: File) {
    val ctx = app.ctx
    var text by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf("Préparation…") }

    LaunchedEffect(file) {
        try {
            val n = withContext(Dispatchers.IO) { Pdf.pageCount(file) }
            if (n < 0) throw IllegalStateException("Document protégé ou illisible")
            val sb = StringBuilder()
            for (i in 0 until n) {
                progress = "Lecture de la page ${i + 1} / $n…"
                val bmp = withContext(Dispatchers.IO) { Pdf.renderPageOf(file, i, 1600) }
                if (bmp == null) continue
                val t = Ocr.read(bmp)
                bmp.recycle()
                if (n > 1) sb.append("--- Page ${i + 1} ---\n")
                sb.append(t.trim()).append("\n\n")
            }
            text = sb.toString().trim().ifEmpty { "(Aucun texte détecté)" }
            if (sb.isNotBlank()) Lib.saveText(ctx, file, sb.toString())
        } catch (e: Exception) {
            text = "Erreur : ${e.message ?: e.javaClass.simpleName}"
        }
    }

    val result = text
    if (result == null) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(progress)
        }
    } else {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    copyText(ctx, result)
                    app.toast("Texte copié")
                }) { Text("Copier") }
                OutlinedButton(onClick = { Lib.shareText(ctx, result) }) { Text("Partager") }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(result) }
            }
        }
    }
}

// ---------------------------------------------------------------- Verrouiller

@Composable
fun LockWork(app: AppState, file: File) {
    val ctx = app.ctx
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Choisissez un mot de passe : il sera demandé pour ouvrir ce document.")
        Text(
            "Attention : si vous l'oubliez, il ne pourra pas être récupéré.",
            color = MaterialTheme.colorScheme.error
        )
        OutlinedTextField(
            value = p1, onValueChange = { p1 = it },
            label = { Text("Mot de passe (4 caractères minimum)") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = p2, onValueChange = { p2 = it },
            label = { Text("Confirmer le mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = p1.length >= 4 && p1 == p2,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Verrouillage…") {
                    val out = Lib.derived(ctx, file, "verrouillé")
                    withContext(Dispatchers.IO) { Pdf.protect(ctx, file, out, p1) }
                    app.toast("Document verrouillé")
                    app.showResult(out)
                }
            }
        ) { Text("Verrouiller le document") }
    }
}

// ---------------------------------------------------------------- Compresser

@Composable
fun CompressWork(app: AppState, file: File) {
    val ctx = app.ctx
    var level by remember { mutableIntStateOf(1) }
    val labels = listOf(
        "Forte (fichier le plus léger)",
        "Moyenne (recommandé)",
        "Légère (meilleure qualité)"
    )
    val scales = listOf(1.0f, 1.4f, 2.0f)
    val qualities = listOf(0.5f, 0.65f, 0.8f)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Taille actuelle : ${Lib.sizeText(file.length())}", fontWeight = FontWeight.Bold)
        for (i in labels.indices) {
            Row(
                Modifier.fillMaxWidth().clickable { level = i }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = level == i, onClick = { level = i })
                Text(labels[i])
            }
        }
        Text(
            "Les pages sont converties en images : le texte n'est plus sélectionnable (adapté aux documents scannés).",
            style = MaterialTheme.typography.bodySmall
        )
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Compression…") {
                    val out = Lib.derived(ctx, file, "compressé")
                    withContext(Dispatchers.IO) {
                        Pdf.compress(ctx, file, out, scales[level], qualities[level])
                    }
                    app.toast("Taille : ${Lib.sizeText(file.length())} → ${Lib.sizeText(out.length())}")
                    app.showResult(out)
                }
            }
        ) { Text("Compresser") }
    }
}

// ---------------------------------------------------------------- Filigrane

@Composable
fun WatermarkWork(app: AppState, file: File) {
    val ctx = app.ctx
    var text by remember { mutableStateOf("CONFIDENTIEL") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Texte écrit en diagonale, en transparence, sur toutes les pages :")
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            label = { Text("Texte du filigrane") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Ajout du filigrane…") {
                    val out = Lib.derived(ctx, file, "filigrane")
                    withContext(Dispatchers.IO) { Pdf.watermark(ctx, file, out, text.trim()) }
                    app.showResult(out)
                }
            }
        ) { Text("Ajouter le filigrane") }
    }
}

// ---------------------------------------------------------------- Signature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignWork(app: AppState, file: File) {
    val ctx = app.ctx
    val count by produceState(0, file.absolutePath) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(file) }
    }
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var corner by remember { mutableIntStateOf(2) }
    var size by remember { mutableStateOf(0.3f) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Dessinez votre signature avec le doigt :")
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color.White)
                .border(1.dp, Color.Gray)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { current = listOf(it) },
                        onDrag = { change, _ -> current = current + change.position },
                        onDragEnd = {
                            strokes.add(current)
                            current = emptyList()
                        },
                        onDragCancel = {
                            strokes.add(current)
                            current = emptyList()
                        }
                    )
                }
        ) {
            val all = strokes.toList() + listOf(current)
            for (pts in all) {
                for (i in 1 until pts.size) {
                    drawLine(
                        color = Color.Black,
                        start = pts[i - 1],
                        end = pts[i],
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }
            }
        }
        TextButton(onClick = {
            strokes.clear()
            current = emptyList()
        }) { Text("Effacer la signature") }

        Text("Page à signer :")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { if (page > 0) page-- }) { Text("−") }
            Text("${page + 1} / $count", Modifier.padding(horizontal = 16.dp))
            OutlinedButton(onClick = { if (page < count - 1) page++ }) { Text("+") }
        }

        Text("Position (en bas de la page) :")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val names = listOf("Gauche", "Centre", "Droite")
            for (i in names.indices) {
                FilterChip(
                    selected = corner == i,
                    onClick = { corner = i },
                    label = { Text(names[i]) }
                )
            }
        }

        Text("Taille de la signature :")
        Slider(value = size, onValueChange = { size = it }, valueRange = 0.15f..0.6f)

        Button(
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val bmp = Img.signatureBitmap(strokes.toList())
                if (bmp == null) {
                    app.toast("Dessinez d'abord votre signature")
                } else {
                    app.task("Ajout de la signature…") {
                        val out = Lib.derived(ctx, file, "signé")
                        withContext(Dispatchers.IO) {
                            Pdf.sign(ctx, file, out, bmp, page, corner, size)
                        }
                        app.showResult(out)
                    }
                }
            }
        ) { Text("Apposer la signature") }
    }
}

// ---------------------------------------------------------------- Fusionner

@Composable
fun MergeWork(app: AppState, files: List<File>) {
    val ctx = app.ctx
    Column(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Ces ${files.size} documents seront fusionnés dans cet ordre :", fontWeight = FontWeight.Bold)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            files.forEachIndexed { i, f ->
                Text("${i + 1}. ${f.nameWithoutExtension}", Modifier.padding(vertical = 4.dp))
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Fusion en cours…") {
                    val out = Lib.newFile(ctx, "Fusion")
                    withContext(Dispatchers.IO) {
                        val parts = files.map { f ->
                            val n = Pdf.pageCount(f)
                            if (n <= 0) throw IllegalStateException("« ${f.nameWithoutExtension} » est protégé ou illisible")
                            f to (0 until n).toList()
                        }
                        Pdf.assemble(ctx, parts, out)
                    }
                    app.showResult(out)
                }
            }
        ) { Text("Fusionner") }
    }
}

// ---------------------------------------------------------------- Extraire des pages

@Composable
fun ExtractWork(app: AppState, file: File) {
    val ctx = app.ctx
    val count by produceState(0, file.absolutePath) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(file) }
    }
    val sel = remember { mutableStateListOf<Int>() }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                sel.clear()
                sel.addAll(0 until count)
            }) { Text("Tout") }
            TextButton(onClick = { sel.clear() }) { Text("Aucune") }
            Spacer(Modifier.weight(1f))
            Text("${sel.size} sélectionnée(s)")
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(count) { i ->
                val on = sel.contains(i)
                Box(
                    Modifier
                        .clickable { if (on) sel.remove(i) else sel.add(i) }
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else Color.Gray)
                ) {
                    PdfThumb(
                        file, 400, Modifier.fillMaxWidth(),
                        page = i, contentScale = ContentScale.FillWidth
                    )
                    Text(
                        "${i + 1}",
                        Modifier.align(Alignment.BottomStart).background(Color(0xAA000000)).padding(4.dp),
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    if (on) {
                        Text(
                            "✔",
                            Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.primary).padding(4.dp),
                            color = Color.White
                        )
                    }
                }
            }
        }
        Button(
            enabled = sel.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            onClick = {
                app.task("Extraction…") {
                    val out = Lib.derived(ctx, file, "extrait")
                    val pages = sel.sorted()
                    withContext(Dispatchers.IO) { Pdf.assemble(ctx, listOf(file to pages), out) }
                    app.showResult(out)
                }
            }
        ) { Text("Extraire ${sel.size} page(s)") }
    }
}

// ---------------------------------------------------------------- Réorganiser

@Composable
fun ReorderWork(app: AppState, file: File) {
    val ctx = app.ctx
    val count by produceState(0, file.absolutePath) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(file) }
    }
    val order = remember(count) { mutableStateListOf<Int>().apply { addAll(0 until count) } }
    Column(Modifier.fillMaxSize()) {
        Text(
            "Montez, descendez ou supprimez des pages, puis enregistrez.",
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(order, key = { _, orig -> orig }) { pos, orig ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PdfThumb(
                        file, 200,
                        Modifier.width(60.dp).height(80.dp),
                        page = orig, contentScale = ContentScale.Crop
                    )
                    Text(
                        "Page d'origine ${orig + 1}\nNouvelle position : ${pos + 1}",
                        Modifier.weight(1f).padding(start = 12.dp)
                    )
                    IconButton(onClick = {
                        if (pos > 0) {
                            val t = order[pos]
                            order[pos] = order[pos - 1]
                            order[pos - 1] = t
                        }
                    }) { Text("▲") }
                    IconButton(onClick = {
                        if (pos < order.size - 1) {
                            val t = order[pos]
                            order[pos] = order[pos + 1]
                            order[pos + 1] = t
                        }
                    }) { Text("▼") }
                    IconButton(onClick = { order.removeAt(pos) }) { Text("🗑️") }
                }
            }
        }
        Button(
            enabled = order.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            onClick = {
                app.task("Enregistrement…") {
                    val out = Lib.derived(ctx, file, "réorganisé")
                    val pages = order.toList()
                    withContext(Dispatchers.IO) { Pdf.assemble(ctx, listOf(file to pages), out) }
                    app.showResult(out)
                }
            }
        ) { Text("Enregistrer le nouvel ordre") }
    }
}

// ---------------------------------------------------------------- PDF en images (pages ou image longue)

@Composable
fun ToImagesWork(app: AppState, file: File) {
    val ctx = app.ctx
    var long by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Comment voulez-vous obtenir les images ?", fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clickable { long = false },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = !long, onClick = { long = false })
            Text("Une image par page")
        }
        Row(
            Modifier.fillMaxWidth().clickable { long = true },
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = long, onClick = { long = true })
            Text("Une seule image longue (pages collées les unes sous les autres)")
        }
        Text(
            "Les images iront dans la galerie, dossier « ZoomScanner ».",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Conversion…") {
                    val n = withContext(Dispatchers.IO) {
                        val progress = { i: Int, total: Int -> app.busy = "Page $i / $total…" }
                        if (long) Pdf.pdfToLongImages(ctx, file, progress) else Pdf.pdfToImages(ctx, file, progress)
                    }
                    app.toast("$n image(s) enregistrée(s) dans la galerie (ZoomScanner)")
                    app.back()
                }
            }
        ) { Text("Enregistrer dans la galerie") }
    }
}

// ---------------------------------------------------------------- Mot de passe (verrouiller ou déverrouiller)

@Composable
fun PasswordWork(app: AppState, file: File) {
    val ctx = app.ctx
    val count by produceState(0, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(file) }
    }
    when {
        count == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        count < 0 -> UnlockWork(app, file)
        else -> LockWork(app, file)
    }
}

@Composable
fun UnlockWork(app: AppState, file: File) {
    val ctx = app.ctx
    var pw by remember { mutableStateOf("") }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Ce document est protégé. Entrez son mot de passe pour en créer une copie sans protection.")
        OutlinedTextField(
            value = pw, onValueChange = { pw = it },
            label = { Text("Mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = pw.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Déverrouillage…") {
                    val out = Lib.derived(ctx, file, "déverrouillé")
                    try {
                        withContext(Dispatchers.IO) { Pdf.unlock(ctx, file, out, pw) }
                        app.showResult(out)
                    } catch (e: Exception) {
                        out.delete()
                        val bad = e.javaClass.simpleName.contains("Password", true) ||
                            (e.message ?: "").contains("password", true)
                        app.toast(if (bad) "Mot de passe incorrect" else "Erreur : ${e.message ?: e.javaClass.simpleName}")
                    }
                }
            }
        ) { Text("Déverrouiller") }
    }
}

// ---------------------------------------------------------------- Tampons : filigrane, numéros, date, texte

@Composable
fun StampsWork(app: AppState, file: File) {
    val ctx = app.ctx
    var mode by remember { mutableIntStateOf(0) }
    val modes = listOf("Filigrane", "Numéros de page", "Date et heure", "En-tête / pied")
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (i in modes.indices) {
                FilterChip(selected = mode == i, onClick = { mode = i }, label = { Text(modes[i]) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (mode) {
                0 -> WatermarkWork(app, file)
                1 -> StampTextWork(app, file, 1)
                2 -> StampTextWork(app, file, 2)
                else -> StampTextWork(app, file, 3)
            }
        }
    }
}

@Composable
fun StampTextWork(app: AppState, file: File, kind: Int) {
    val ctx = app.ctx
    var format by remember { mutableIntStateOf(1) }
    var horizontal by remember { mutableIntStateOf(1) }
    var vertical by remember { mutableIntStateOf(0) }
    var custom by remember { mutableStateOf("") }
    val formats = listOf("1", "1 / N", "Page 1 sur N")
    val hNames = listOf("Gauche", "Centre", "Droite")
    val vNames = listOf("En bas", "En haut")
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (kind) {
            1 -> {
                Text("Format du numéro :")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (i in formats.indices) {
                        FilterChip(selected = format == i, onClick = { format = i }, label = { Text(formats[i]) })
                    }
                }
            }
            2 -> Text("La date et l'heure d'aujourd'hui seront écrites sur chaque page.")
            else -> {
                Text("Texte écrit sur chaque page (par exemple le nom du dossier) :")
                OutlinedTextField(
                    value = custom, onValueChange = { custom = it },
                    label = { Text("Texte") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Text("Position :")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in vNames.indices) {
                FilterChip(selected = vertical == i, onClick = { vertical = i }, label = { Text(vNames[i]) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in hNames.indices) {
                FilterChip(selected = horizontal == i, onClick = { horizontal = i }, label = { Text(hNames[i]) })
            }
        }
        Button(
            enabled = kind != 3 || custom.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                app.task("Ajout du texte…") {
                    val out = Lib.derived(ctx, file, "tampon")
                    val now = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date())
                    val fmt = format
                    val txt = custom.trim()
                    withContext(Dispatchers.IO) {
                        Pdf.stampText(ctx, file, out, horizontal, vertical) { n, total ->
                            when (kind) {
                                1 -> when (fmt) {
                                    0 -> "$n"
                                    1 -> "$n / $total"
                                    else -> "Page $n sur $total"
                                }
                                2 -> now
                                else -> txt
                            }
                        }
                    }
                    app.showResult(out)
                }
            }
        ) { Text("Appliquer sur toutes les pages") }
    }
}
