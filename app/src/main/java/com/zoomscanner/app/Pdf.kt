package com.zoomscanner.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import java.text.Normalizer
import java.util.UUID
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Toutes les opérations sur les PDF : affichage, création, fusion, protection, filigrane, signature… */
object Pdf {
    private var ready = false

    fun init(ctx: Context) {
        if (!ready) {
            PDFBoxResourceLoader.init(ctx.applicationContext)
            ready = true
        }
    }

    // ---------- Affichage (rendu des pages) ----------

    fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T {
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            val renderer = PdfRenderer(pfd)
            try {
                return block(renderer)
            } finally {
                renderer.close()
            }
        } finally {
            pfd.close()
        }
    }

    /** Nombre de pages, ou -1 si le fichier est protégé / illisible. */
    fun pageCount(file: File): Int = try {
        withRenderer(file) { it.pageCount }
    } catch (e: Exception) {
        -1
    }

    fun renderPage(r: PdfRenderer, index: Int, widthPx: Int): Bitmap {
        val page = r.openPage(index)
        try {
            val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    fun renderPageOf(file: File, index: Int, widthPx: Int): Bitmap? = try {
        withRenderer(file) { renderPage(it, index, widthPx) }
    } catch (e: Exception) {
        null
    }

    // ---------- Création à partir d'images ----------

    /** Crée un PDF : une page par image fournie. Chaque image est libérée après usage. */
    fun bitmapsToPdf(ctx: Context, out: File, count: Int, provider: (Int) -> Bitmap) {
        init(ctx)
        val doc = PDDocument()
        try {
            for (i in 0 until count) {
                val raw = provider(i)
                val bmp = Img.flatten(raw)
                val w = 595f
                val h = w * bmp.height / bmp.width
                val page = PDPage(PDRectangle(w, h))
                doc.addPage(page)
                val img = JPEGFactory.createFromImage(doc, bmp, 0.85f)
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(img, 0f, 0f, w, h) }
                if (bmp !== raw) bmp.recycle()
                raw.recycle()
            }
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    fun imagesToPdf(ctx: Context, uris: List<Uri>, out: File, stampDate: Boolean = false) {
        bitmapsToPdf(ctx, out, uris.size) { i ->
            val b = Img.decode(ctx, uris[i])
            if (stampDate) {
                val s = Img.stampDate(b)
                b.recycle()
                s
            } else {
                b
            }
        }
    }

    // ---------- Assemblage : fusionner / extraire / réorganiser ----------

    /** Construit un nouveau PDF à partir de pages choisies (dans l'ordre donné) de plusieurs fichiers. */
    fun assemble(ctx: Context, parts: List<Pair<File, List<Int>>>, out: File) {
        init(ctx)
        val sources = ArrayList<PDDocument>()
        val dst = PDDocument()
        try {
            for ((file, pages) in parts) {
                val src = PDDocument.load(file)
                sources.add(src)
                for (p in pages) {
                    dst.importPage(src.getPage(p))
                }
            }
            dst.save(out)
        } finally {
            dst.close()
            sources.forEach { it.close() }
        }
    }

    // ---------- Mot de passe ----------

    fun protect(ctx: Context, src: File, out: File, password: String) {
        init(ctx)
        val doc = PDDocument.load(src)
        try {
            val policy = StandardProtectionPolicy(
                UUID.randomUUID().toString(),
                password,
                AccessPermission()
            )
            policy.setEncryptionKeyLength(128)
            policy.setPreferAES(true)
            doc.protect(policy)
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    // ---------- Filigrane ----------

    private fun stripAccents(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    fun watermark(ctx: Context, src: File, out: File, text: String) {
        init(ctx)
        val doc = PDDocument.load(src)
        try {
            val font = PDType1Font.HELVETICA_BOLD
            val safe = try {
                font.getStringWidth(text)
                text
            } catch (e: Exception) {
                stripAccents(text)
            }
            val base = font.getStringWidth(safe) / 1000f
            val angle = Math.toRadians(45.0)
            for (page in doc.pages) {
                val box = page.mediaBox
                val w = box.width
                val h = box.height
                val diag = sqrt(w * w + h * h)
                val size = min(0.7f * diag / base, h / 6f)
                val tw = base * size
                val cx = w / 2f
                val cy = h / 2f
                val x = (cx - (tw / 2f) * cos(angle) + 0.35f * size * sin(angle)).toFloat()
                val y = (cy - (tw / 2f) * sin(angle) - 0.35f * size * cos(angle)).toFloat()
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    val gs = PDExtendedGraphicsState()
                    gs.setNonStrokingAlphaConstant(0.25f)
                    gs.setStrokingAlphaConstant(0.25f)
                    cs.setGraphicsStateParameters(gs)
                    cs.setNonStrokingColor(128, 128, 128)
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setTextMatrix(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(angle, x, y))
                    cs.showText(safe)
                    cs.endText()
                }
            }
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    // ---------- Signature ----------

    /** corner : 0 = gauche, 1 = centre, 2 = droite (en bas de la page). */
    fun sign(
        ctx: Context, src: File, out: File, sig: Bitmap,
        pageIndex: Int, corner: Int, widthFraction: Float
    ) {
        init(ctx)
        val doc = PDDocument.load(src)
        try {
            val page = doc.getPage(pageIndex)
            val img = LosslessFactory.createFromImage(doc, sig)
            val box = page.mediaBox
            val w = box.width * widthFraction
            val h = w * sig.height / sig.width
            val margin = 36f
            val x = when (corner) {
                0 -> box.lowerLeftX + margin
                1 -> box.lowerLeftX + (box.width - w) / 2f
                else -> box.lowerLeftX + box.width - w - margin
            }
            val y = box.lowerLeftY + margin
            PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true, true
            ).use { cs -> cs.drawImage(img, x, y, w, h) }
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    // ---------- Compression ----------

    /** Les pages sont redessinées en images JPEG plus légères. scale : points → pixels. */
    fun compress(ctx: Context, src: File, out: File, scale: Float, quality: Float) {
        init(ctx)
        val dst = PDDocument()
        try {
            withRenderer(src) { r ->
                for (i in 0 until r.pageCount) {
                    val pg = r.openPage(i)
                    val wPt = pg.width.toFloat()
                    val hPt = pg.height.toFloat()
                    pg.close()
                    val bmp = renderPage(r, i, (wPt * scale).toInt().coerceAtLeast(200))
                    val page = PDPage(PDRectangle(wPt, hPt))
                    dst.addPage(page)
                    val img = JPEGFactory.createFromImage(dst, bmp, quality)
                    PDPageContentStream(dst, page).use { cs -> cs.drawImage(img, 0f, 0f, wPt, hPt) }
                    bmp.recycle()
                }
            }
            dst.save(out)
        } finally {
            dst.close()
        }
    }

    // ---------- Conversion en images ----------

    fun pdfToImages(ctx: Context, src: File, onProgress: (Int, Int) -> Unit): Int {
        val base = src.nameWithoutExtension
        var saved = 0
        withRenderer(src) { r ->
            val n = r.pageCount
            for (i in 0 until n) {
                onProgress(i + 1, n)
                val b = renderPage(r, i, 1600)
                if (Lib.saveJpeg(ctx, b, "$base - page ${i + 1}")) saved++
                b.recycle()
            }
        }
        return saved
    }

    /** Colle les pages les unes sous les autres (plusieurs images si le document est très long). */
    fun pdfToLongImages(ctx: Context, src: File, onProgress: (Int, Int) -> Unit): Int {
        val base = src.nameWithoutExtension
        val width = 1000
        val maxHeight = 12000
        val strip = ArrayList<Bitmap>()
        var total = 0
        var parts = 0

        fun flush() {
            if (strip.isEmpty()) return
            val out = Bitmap.createBitmap(width, total, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            var y = 0f
            for (b in strip) {
                c.drawBitmap(b, 0f, y, null)
                y += b.height
                b.recycle()
            }
            strip.clear()
            total = 0
            parts++
            Lib.saveJpeg(ctx, out, "$base - longue $parts")
            out.recycle()
        }

        withRenderer(src) { r ->
            val n = r.pageCount
            for (i in 0 until n) {
                onProgress(i + 1, n)
                val b = renderPage(r, i, width)
                if (total + b.height > maxHeight && strip.isNotEmpty()) flush()
                strip.add(b)
                total += b.height
            }
            flush()
        }
        return parts
    }

    // ---------- Retirer le mot de passe ----------

    fun unlock(ctx: Context, src: File, out: File, password: String) {
        init(ctx)
        val doc = PDDocument.load(src, password)
        try {
            doc.isAllSecurityToBeRemoved = true
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    // ---------- Texte sur chaque page (numéros, date, en-tête / pied de page) ----------

    /**
     * Écrit un texte sur chaque page. horizontal : 0 gauche, 1 centre, 2 droite.
     * vertical : 0 en bas, 1 en haut. texts(numéro de page à partir de 1, nombre de pages) -> texte.
     */
    fun stampText(
        ctx: Context, src: File, out: File, horizontal: Int, vertical: Int,
        texts: (Int, Int) -> String
    ) {
        init(ctx)
        val doc = PDDocument.load(src)
        try {
            val font = PDType1Font.HELVETICA
            val size = 10f
            val total = doc.numberOfPages
            for (i in 0 until total) {
                val page = doc.getPage(i)
                val raw = texts(i + 1, total)
                if (raw.isBlank()) continue
                val txt = try {
                    font.getStringWidth(raw)
                    raw
                } catch (e: Exception) {
                    stripAccents(raw)
                }
                val tw = font.getStringWidth(txt) / 1000f * size
                val box = page.mediaBox
                val margin = 24f
                val x = when (horizontal) {
                    0 -> box.lowerLeftX + margin
                    1 -> box.lowerLeftX + (box.width - tw) / 2f
                    else -> box.lowerLeftX + box.width - tw - margin
                }
                val y = if (vertical == 0) box.lowerLeftY + margin else box.lowerLeftY + box.height - margin - size
                PDPageContentStream(
                    doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { cs ->
                    cs.setNonStrokingColor(0, 0, 0)
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.newLineAtOffset(x, y)
                    cs.showText(txt)
                    cs.endText()
                }
            }
            doc.save(out)
        } finally {
            doc.close()
        }
    }
}
