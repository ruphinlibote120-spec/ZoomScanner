package com.zoomscanner.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.exifinterface.media.ExifInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/** Outils images : décodage, horodatage, carte d'identité, signature. */
object Img {
    fun decode(ctx: Context, uri: Uri, maxSide: Int = 2200): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)!!.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = ctx.contentResolver.openInputStream(uri)!!.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IllegalStateException("Image illisible")
        val rot = try {
            ctx.contentResolver.openInputStream(uri)!!.use { ExifInterface(it).rotationDegrees }
        } catch (e: Exception) {
            0
        }
        val longest = max(bmp.width, bmp.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        if (rot != 0 || scale < 1f) {
            val m = Matrix()
            if (scale < 1f) m.postScale(scale, scale)
            if (rot != 0) m.postRotate(rot.toFloat())
            val t = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (t !== bmp) bmp.recycle()
            bmp = t
        }
        return bmp
    }

    /** Remplace la transparence par du blanc (le JPEG n'a pas de transparence). */
    fun flatten(b: Bitmap): Bitmap {
        if (!b.hasAlpha()) return b
        val out = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(b, 0f, 0f, null)
        return out
    }

    /** Écrit la date et l'heure en bas à droite de l'image. */
    fun stampDate(src: Bitmap): Bitmap {
        val b = src.copy(Bitmap.Config.ARGB_8888, true)
        val c = Canvas(b)
        val text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())
        val size = b.width * 0.04f
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size
            typeface = Typeface.DEFAULT_BOLD
        }
        val bg = Paint().apply { color = 0xAA000000.toInt() }
        val w = p.measureText(text)
        val pad = size * 0.4f
        val left = b.width - w - pad * 2 - size * 0.5f
        val top = b.height - size * 1.8f - pad
        c.drawRect(left, top, left + w + pad * 2, top + size + pad * 2, bg)
        c.drawText(text, left + pad, top + pad + size * 0.85f, p)
        return b
    }

    /** Place le recto (et le verso) d'une carte sur une même page A4. */
    fun idCardPage(front: Bitmap, back: Bitmap?): Bitmap {
        val w = 1240
        val h = 1754
        val page = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(page)
        c.drawColor(Color.WHITE)
        val boxW = 1000f
        val boxH = 700f
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)

        fun place(b: Bitmap, top: Float) {
            val s = min(boxW / b.width, boxH / b.height)
            val dw = b.width * s
            val dh = b.height * s
            val l = (w - dw) / 2f
            val t = top + (boxH - dh) / 2f
            c.drawBitmap(b, null, RectF(l, t, l + dw, t + dh), paint)
        }

        place(front, 150f)
        if (back != null) place(back, 150f + boxH + 100f)
        return page
    }

    /** Transforme les traits dessinés en image PNG transparente (recadrée). */
    fun signatureBitmap(strokes: List<List<Offset>>): Bitmap? {
        val pts = strokes.flatten()
        if (pts.size < 2) return null
        val pad = 10f
        val minX = pts.minOf { it.x } - pad
        val minY = pts.minOf { it.y } - pad
        val maxX = pts.maxOf { it.x } + pad
        val maxY = pts.maxOf { it.y } + pad
        val w = (maxX - minX).toInt().coerceAtLeast(2)
        val h = (maxY - minY).toInt().coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        for (s in strokes) {
            for (i in 1 until s.size) {
                c.drawLine(s[i - 1].x - minX, s[i - 1].y - minY, s[i].x - minX, s[i].y - minY, p)
            }
        }
        return bmp
    }
}
