package com.zoomscanner.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(app: AppState, file: File) {
    val ctx = app.ctx
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    val save = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            app.task("Enregistrement…") {
                withContext(Dispatchers.IO) { Lib.copyTo(ctx, file, uri) }
                app.toast("Fichier enregistré")
            }
        }
    }

    val count by produceState(0, file.absolutePath, file.lastModified()) {
        value = withContext(Dispatchers.IO) { Pdf.pageCount(file) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(file.nameWithoutExtension, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { app.back() }) { Text("←", fontSize = 22.sp) }
                },
                actions = {
                    IconButton(onClick = { Lib.share(ctx, file) }) { Text("📤") }
                    Box {
                        IconButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Enregistrer sous…") },
                                onClick = {
                                    menu = false
                                    save.launch(file.name)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Imprimer") },
                                onClick = {
                                    menu = false
                                    Lib.print(ctx, file)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Renommer") },
                                onClick = {
                                    menu = false
                                    renaming = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
                                onClick = {
                                    menu = false
                                    deleting = true
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val quick = listOf(
                        Tool.OCR, Tool.SIGN, Tool.WATERMARK, Tool.LOCK, Tool.COMPRESS,
                        Tool.EXTRACT, Tool.REORDER, Tool.PDF_TO_IMAGES, Tool.PDF_LONG
                    )
                    for (t in quick) {
                        AssistChip(
                            onClick = { app.push(Screen.Work(t, listOf(file))) },
                            label = { Text(t.icon + " " + t.label) }
                        )
                    }
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                count < 0 -> Box(
                    Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🔒 Ce document est protégé par un mot de passe.\nOuvrez-le avec une autre application (bouton Partager).",
                        textAlign = TextAlign.Center
                    )
                }

                count == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> LazyColumn(
                    Modifier.fillMaxSize().background(Color(0xFF8A8A8A)),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(count) { i ->
                        PdfThumb(
                            file, 1100,
                            Modifier.fillMaxWidth().background(Color.White),
                            page = i,
                            contentScale = ContentScale.FillWidth
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(file.nameWithoutExtension) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renommer") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = File(file.parentFile, Lib.clean(name) + ".pdf")
                    renaming = false
                    if (target != file && target.exists()) {
                        app.toast("Ce nom existe déjà")
                    } else if (target == file) {
                        // rien à changer
                    } else if (file.renameTo(target)) {
                        app.refresh++
                        app.replaceTop(Screen.Viewer(target))
                    } else {
                        app.toast("Impossible de renommer")
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Annuler") } }
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Supprimer ce document ?") },
            text = { Text("« ${file.nameWithoutExtension} » sera supprimé définitivement de ZoomScanner.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    file.delete()
                    app.refresh++
                    app.back()
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Annuler") } }
        )
    }
}
