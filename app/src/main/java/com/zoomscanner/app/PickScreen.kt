package com.zoomscanner.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Choix du ou des documents sur lesquels appliquer un outil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickScreen(app: AppState, tool: Tool) {
    val ctx = app.ctx
    val multi = tool == Tool.MERGE
    val docs by produceState(emptyList<File>(), app.refresh) {
        value = withContext(Dispatchers.IO) { Lib.list(ctx) }
    }
    val chosen = remember { mutableStateListOf<File>() }

    fun finish(files: List<File>) {
        if (tool == Tool.PRINT) {
            app.back()
            Lib.print(ctx, files[0])
        } else {
            app.replaceTop(Screen.Work(tool, files))
        }
    }

    val scan = rememberScanLauncher(30) { pdf, _ ->
        if (pdf != null) {
            app.task("Enregistrement…") {
                val f = withContext(Dispatchers.IO) { Lib.importUri(ctx, pdf, "Scan", false) }
                app.refresh++
                if (multi) chosen.add(f) else finish(listOf(f))
            }
        }
    }

    val importPdf = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            app.task("Importation…") {
                val files = withContext(Dispatchers.IO) {
                    uris.filter { (ctx.contentResolver.getType(it) ?: "") == "application/pdf" }
                        .map { Lib.importUri(ctx, it, "Document") }
                }
                app.refresh++
                if (files.isEmpty()) {
                    app.toast("Choisissez un fichier PDF")
                } else if (multi) {
                    chosen.addAll(files)
                } else {
                    finish(listOf(files[0]))
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(tool.label) },
                navigationIcon = {
                    IconButton(onClick = { app.back() }) { Text("←", fontSize = 22.sp) }
                }
            )
        },
        bottomBar = {
            if (multi) {
                Button(
                    onClick = { app.replaceTop(Screen.Work(tool, chosen.toList())) },
                    enabled = chosen.size >= 2,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) { Text("Continuer (${chosen.size} documents)") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { scan() }, modifier = Modifier.weight(1f)) {
                    Text("📷 Scanner")
                }
                OutlinedButton(onClick = { importPdf.launch(arrayOf("application/pdf")) }, modifier = Modifier.weight(1f)) {
                    Text("📂 Importer un PDF")
                }
            }
            Text(
                if (multi) "Touchez les documents à fusionner, dans l'ordre voulu :"
                else "Ou choisissez un document de la bibliothèque :",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            if (docs.isEmpty()) {
                Text(
                    "Votre bibliothèque est vide.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LazyColumn(Modifier.weight(1f)) {
                items(docs, key = { it.absolutePath }) { f ->
                    val index = chosen.indexOf(f)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multi) {
                                    if (index >= 0) chosen.remove(f) else chosen.add(f)
                                } else {
                                    finish(listOf(f))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (multi) {
                            Checkbox(checked = index >= 0, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                        }
                        PdfThumb(
                            f, 200,
                            Modifier.width(44.dp).height(58.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                f.nameWithoutExtension,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                Lib.sizeText(f.length()) + " · " + Lib.dateText(f.lastModified()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (multi && index >= 0) {
                            Text("${index + 1}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}
