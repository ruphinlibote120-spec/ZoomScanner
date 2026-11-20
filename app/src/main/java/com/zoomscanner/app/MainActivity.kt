package com.zoomscanner.app

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZoomTheme { Root() }
        }
    }
}

@Composable
fun ZoomTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = Color(0xFF34D399),
            onPrimary = Color(0xFF04231A),
            background = Color(0xFF0B1210),
            onBackground = Color(0xFFEAF3EE),
            surface = Color(0xFF0B1210),
            onSurface = Color(0xFFEAF3EE),
            surfaceVariant = Color(0xFF1E2B25),
            onSurfaceVariant = Color(0xFF9FB3A9),
            surfaceContainerLowest = Color(0xFF0B1210),
            surfaceContainerLow = Color(0xFF131C18),
            surfaceContainer = Color(0xFF101815),
            surfaceContainerHigh = Color(0xFF1A2621),
            surfaceContainerHighest = Color(0xFF22302A),
            secondaryContainer = Color(0xFF1F3B30),
            onSecondaryContainer = Color(0xFFEAF3EE),
            outline = Color(0xFF4A5E55),
            outlineVariant = Color(0xFF2E3F38)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF0E9F6E),
            surfaceContainerLow = Color(0xFFF1F5F3),
            surfaceContainer = Color(0xFFEAF0ED)
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}

/** État global : pile d'écrans, message « travail en cours », rafraîchissement de la liste. */
class AppState(val ctx: Context, private val scope: CoroutineScope) {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var busy by mutableStateOf<String?>(null)
    var refresh by mutableIntStateOf(0)
    var homeTab by mutableIntStateOf(0)

    fun push(s: Screen) {
        stack.add(s)
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun replaceTop(s: Screen) {
        stack[stack.lastIndex] = s
    }

    /** Affiche le document créé (et le remplace dans la pile pour que « retour » revienne à l'accueil). */
    fun showResult(f: File) {
        refresh++
        if (stack.size > 1 && stack.last() !is Screen.Viewer) replaceTop(Screen.Viewer(f)) else push(Screen.Viewer(f))
    }

    fun toast(msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
    }

    fun task(msg: String, block: suspend () -> Unit) {
        scope.launch {
            busy = msg
            try {
                block()
            } catch (e: Throwable) {
                toast("Erreur : ${e.message ?: e.javaClass.simpleName}")
            } finally {
                busy = null
            }
        }
    }
}

@Composable
fun Root() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = remember { AppState(ctx, scope) }
    var splash by remember { mutableStateOf(true) }
    BackHandler(enabled = !splash && app.stack.size > 1) { app.back() }

    Crossfade(targetState = splash, animationSpec = tween(400), label = "splash") { showing ->
        if (showing) {
            SplashScreen { splash = false }
        } else {
            Surface(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    when (val s = app.stack.last()) {
                        is Screen.Home -> HomeScreen(app)
                        is Screen.Viewer -> ViewerScreen(app, s.file)
                        is Screen.Pick -> PickScreen(app, s.tool)
                        is Screen.Work -> WorkScreen(app, s.tool, s.files)
                        is Screen.ScanModes -> ScanModesScreen(app)
                        is Screen.QrCreate -> QrCreateScreen(app)
                        is Screen.ImagesTool -> ImagesScreen(app)
                        is Screen.Settings -> SettingsScreen(app)
                        is Screen.Favorites -> FavoritesScreen(app)
                        is Screen.Folders -> FoldersScreen(app)
                        is Screen.Folder -> FolderScreen(app, s.name)
                        is Screen.Trash -> TrashScreen(app)
                    }
                    val msg = app.busy
                    if (msg != null) BusyOverlay(msg)
                }
            }
        }
    }
}

@Composable
fun BusyOverlay(msg: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x88000000))
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center
    ) {
        Card {
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(32.dp))
                Spacer(Modifier.width(16.dp))
                Text(msg)
            }
        }
    }
}
