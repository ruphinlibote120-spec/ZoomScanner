package com.zoomscanner.app

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Grille de documents avec une barre de titre (favoris, contenu d'un dossier…). */
@Composable
private fun DocListScreen(
    app: AppState,
    title: String,
    files: List<File>,
    emptyText: String,
    actions: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxSize()) {
        ScreenBar(title, { app.back() }, actions)
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    emptyText,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(files, key = { it.absolutePath }) { f ->
                    DocCard(app, f) { app.push(Screen.Viewer(f)) }
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(app: AppState) {
    val ctx = app.ctx
    val files by produceState(emptyList<File>(), app.refresh) {
        value = withContext(Dispatchers.IO) { Lib.favorites(ctx) }
    }
    DocListScreen(
        app, "Favoris", files,
        "Aucun favori.\nOuvrez un document et touchez l'étoile pour l'ajouter ici."
    )
}

@Composable
fun FolderScreen(app: AppState, name: String) {
    val ctx = app.ctx
    val files by produceState(emptyList<File>(), app.refresh, name) {
        value = withContext(Dispatchers.IO) { Lib.listIn(ctx, name) }
    }
    DocListScreen(
        app, name, files,
        "Ce dossier est vide.\nOuvrez un document, menu ⋮, puis « Déplacer vers un dossier »."
    )
}

@Composable
fun FoldersScreen(app: AppState) {
    val ctx = app.ctx
    var creating by remember { mutableStateOf(false) }
    val folders by produceState(emptyList<Pair<String, Int>>(), app.refresh) {
        value = withContext(Dispatchers.IO) {
            Lib.folders(ctx).map { it to Lib.listIn(ctx, it).size }
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenBar("Dossiers", { app.back() }) {
            TextButton(onClick = { creating = true }) { Text("Nouveau") }
        }
        if (folders.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Aucun dossier.\nTouchez « Nouveau » pour en créer un.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(folders, key = { it.first }) { (name, count) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { app.push(Screen.Folder(name)) }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTile(0xFF2563EB, "folder", 44.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (count == 1) "1 document" else "$count documents",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (count == 0) {
                            TextButton(onClick = {
                                Lib.deleteFolderIfEmpty(ctx, name)
                                app.refresh++
                            }) { Text("Supprimer") }
                        }
                    }
                }
            }
        }
    }
    if (creating) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nouveau dossier") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Nom") })
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        creating = false
                        if (!Lib.createFolder(ctx, name)) app.toast("Ce dossier existe déjà")
                        app.refresh++
                    }
                ) { Text("Créer") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun TrashScreen(app: AppState) {
    val ctx = app.ctx
    val files by produceState(emptyList<File>(), app.refresh) {
        value = withContext(Dispatchers.IO) { Lib.trashList(ctx) }
    }
    var confirmEmpty by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenBar("Corbeille", { app.back() }) {
            if (files.isNotEmpty()) {
                TextButton(onClick = { confirmEmpty = true }) { Text("Vider") }
            }
        }
        if (files.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "La corbeille est vide.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(files, key = { it.absolutePath }) { f ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PdfThumb(
                            f, 200,
                            Modifier.width(44.dp).height(58.dp).clip(RoundedCornerShape(6.dp)),
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
                                Lib.sizeText(f.length()),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = {
                            Lib.restore(ctx, f)
                            app.refresh++
                        }) { Text("Restaurer") }
                        TextButton(onClick = {
                            f.delete()
                            app.refresh++
                        }) { Text("Supprimer") }
                    }
                }
            }
        }
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Vider la corbeille ?") },
            text = { Text("Les documents seront supprimés définitivement.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmEmpty = false
                    Lib.emptyTrash(ctx)
                    app.refresh++
                }) { Text("Vider") }
            },
            dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text("Annuler") } }
        )
    }
}

@Composable
fun SettingsScreen(app: AppState) {
    val ctx = app.ctx
    val trashCount by produceState(0, app.refresh) {
        value = withContext(Dispatchers.IO) { Lib.trashList(ctx).size }
    }
    val used by produceState(0L, app.refresh) {
        value = withContext(Dispatchers.IO) {
            Lib.listAll(ctx).sumOf { it.length() } + Lib.trashList(ctx).sumOf { it.length() }
        }
    }
    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    Column(Modifier.fillMaxSize()) {
        ScreenBar("Réglages", { app.back() })
        Column(Modifier.fillMaxSize()) {
            ListRow(
                "Supprimer des pages", 0xFF475569, "Corbeille",
                if (trashCount == 0) "Vide" else "$trashCount document(s)"
            ) { app.push(Screen.Trash) }
            ListRow(
                "Compresser", 0xFF0D9488, "Espace utilisé",
                Lib.sizeText(used) + " par vos documents"
            ) { }
            Spacer(Modifier.height(24.dp))
            Text(
                "ZoomScanner $version\nVos documents restent sur votre téléphone : rien n'est envoyé sur internet.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}
