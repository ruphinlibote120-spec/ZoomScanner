package com.zoomscanner.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val GREEN = Color(0xFF0E9F6E)
private val DARK = Color(0xFF052B1E)

// Courbes d'animation (mêmes réglages que l'aperçu validé)
private val easeWrite = CubicBezierEasing(0.45f, 0f, 0.3f, 1f)
private val easeOut = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
private val easeSoft = CubicBezierEasing(0f, 0f, 0.58f, 1f)

private fun seg(t: Float, start: Float, dur: Float, e: Easing = LinearEasing): Float =
    e.transform(((t - start) / dur).coerceIn(0f, 1f))

private fun mix(a: Float, b: Float, f: Float): Float = a + (b - a) * f

private fun poly(vararg p: Float): Path {
    val path = Path()
    path.moveTo(p[0], p[1])
    var i = 2
    while (i < p.size) {
        path.lineTo(p[i], p[i + 1])
        i += 2
    }
    path.close()
    return path
}

/** Courbe de Bézier quadratique écrite en cubique (x0,y0 = point de départ). */
private fun Path.quad(x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) {
    cubicTo(
        x0 + 2f / 3f * (cx - x0), y0 + 2f / 3f * (cy - y0),
        x1 + 2f / 3f * (cx - x1), y1 + 2f / 3f * (cy - y1),
        x1, y1
    )
}

private fun DrawScope.bar(t: Float, start: Float, x: Float, y: Float, w: Float, h: Float) {
    val p = seg(t, start, 0.3f, easeSoft)
    if (p > 0f) {
        drawRoundRect(GREEN, topLeft = Offset(x, y), size = Size(w * p, h), cornerRadius = CornerRadius(1.5f))
    }
}

/**
 * Animation d'ouverture : le Z s'écrit, le cadre se resserre comme pour une photo (avec un petit éclair),
 * puis le nom « ZoomScanner » monte du bas. Touchez l'écran pour passer.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val time = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        time.animateTo(5f, tween(5000, easing = LinearEasing))
        onDone()
    }

    val zPath = remember {
        poly(
            161.1f, 85.5f, 261.3f, 85.5f, 261.3f, 117.5f, 168f, 221.1f, 261.3f, 221.1f,
            261.3f, 251.4f, 119.3f, 251.4f, 119.3f, 224.5f, 209.3f, 123.5f, 161.1f, 123.5f
        )
    }
    val fold1 = remember { poly(157.1f, 89.2f, 157.1f, 124.4f, 122.7f, 125.2f) }
    val fold2 = remember { poly(120.7f, 129.4f, 157.1f, 129.4f, 120.7f, 168.4f) }
    val frames = remember {
        listOf(
            Path().apply {
                moveTo(86.8f, 99.4f); lineTo(86.8f, 64f)
                quad(86.8f, 64f, 86.8f, 57.9f, 92.9f, 57.9f); lineTo(127.4f, 57.9f)
            },
            Path().apply {
                moveTo(254f, 57.9f); lineTo(287.7f, 57.9f)
                quad(287.7f, 57.9f, 293.8f, 57.9f, 293.8f, 64f); lineTo(293.8f, 99.4f)
            },
            Path().apply {
                moveTo(86.8f, 221.1f); lineTo(86.8f, 257.9f)
                quad(86.8f, 257.9f, 86.8f, 264f, 92.9f, 264f); lineTo(127.4f, 264f)
            },
            Path().apply {
                moveTo(293.8f, 221.1f); lineTo(293.8f, 257.9f)
                quad(293.8f, 257.9f, 293.8f, 264f, 287.7f, 264f); lineTo(254f, 264f)
            }
        )
    }
    // Trajet du « stylo » qui écrit le Z (révèle la forme au fur et à mesure)
    val measure = remember {
        val p = Path().apply {
            moveTo(150f, 104.5f); lineTo(259f, 104.5f); lineTo(122f, 238f); lineTo(266f, 238f)
        }
        PathMeasure().apply { setPath(p, false) }
    }
    val penPaint = remember {
        android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 46f
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.BUTT
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable { onDone() },
        contentAlignment = Alignment.Center
    ) {
        val maxW: Dp = 460.dp
        val w: Dp = if (maxWidth * 0.9f < maxW) maxWidth * 0.9f else maxW
        val h: Dp = w * 330f / 380f
        val wPx = with(LocalDensity.current) { w.toPx() }

        Box(Modifier.size(w, h)) {
            Canvas(Modifier.fillMaxSize()) {
                val t = time.value
                val s = size.width / 380f
                withTransform({
                    scale(s, s, Offset.Zero)
                    translate(0f, -24f)
                }) {
                    // 1. Le Z s'écrit
                    val pw = seg(t, 0.3f, 1.7f, easeWrite)
                    val pp = seg(t, 3.35f, 0.35f)
                    val pulse = 1f + 0.04f * (if (pp < 0.4f) pp / 0.4f else 1f - (pp - 0.4f) / 0.6f)
                    if (pw > 0f) {
                        val part = Path()
                        measure.getSegment(0f, measure.length * pw, part, true)
                        val outline = android.graphics.Path()
                        penPaint.getFillPath(part.asAndroidPath(), outline)
                        withTransform({ scale(pulse, pulse, Offset(190.3f, 168.5f)) }) {
                            clipPath(outline.asComposePath()) { drawPath(zPath, GREEN) }
                        }
                    }

                    // 2. Coin plié et lignes de texte
                    val f1 = seg(t, 2.0f, 0.35f, easeSoft)
                    if (f1 > 0f) {
                        val sc = mix(0.6f, 1f, f1)
                        withTransform({ scale(sc, sc, Offset(145.6f, 112.9f)) }) {
                            drawPath(fold1, GREEN, alpha = f1)
                        }
                    }
                    val f2 = seg(t, 2.15f, 0.35f, easeSoft)
                    if (f2 > 0f) {
                        val sc = mix(0.6f, 1f, f2)
                        withTransform({ scale(sc, sc, Offset(132.8f, 142.4f)) }) {
                            drawPath(fold2, DARK, alpha = f2)
                        }
                    }
                    bar(t, 2.35f, 215.8f, 182.9f, 30.1f, 5.7f)
                    bar(t, 2.45f, 205.8f, 195.9f, 40.2f, 6.1f)
                    bar(t, 2.55f, 205.8f, 208.7f, 40.2f, 5.9f)

                    // 3. Le cadre se resserre comme un autofocus
                    val fp = seg(t, 2.9f, 0.6f)
                    if (fp > 0f) {
                        val sc: Float
                        val al: Float
                        if (fp < 0.6f) {
                            sc = mix(1.6f, 0.96f, easeOut.transform(fp / 0.6f))
                            al = (fp / 0.6f).coerceIn(0f, 1f)
                        } else {
                            sc = mix(0.96f, 1f, easeOut.transform((fp - 0.6f) / 0.4f))
                            al = 1f
                        }
                        withTransform({ scale(sc, sc, Offset(190.3f, 161f)) }) {
                            for (f in frames) {
                                drawPath(f, GREEN, alpha = al, style = Stroke(width = 8.3f))
                            }
                        }
                    }
                }
            }

            // 4. Le nom monte depuis le bas (découpé directement dans votre logo)
            Image(
                painter = painterResource(R.drawable.logo_text),
                contentDescription = "ZoomScanner",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .offset(x = w * 0.1328f, y = w * 0.6910f)
                    .size(w * 0.7370f, w * 0.0866f)
                    .graphicsLayer {
                        val p = seg(time.value, 3.8f, 0.7f, easeOut)
                        alpha = p
                        translationY = (1f - p) * 26f / 380f * wPx
                    }
            )
        }

        // 5. Petit éclair vert au moment de la « photo »
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val fl = seg(time.value, 3.4f, 0.3f)
                    alpha = 0.4f * (if (fl < 0.3f) fl / 0.3f else 1f - (fl - 0.3f) / 0.7f)
                }
                .background(GREEN)
        )
    }
}
