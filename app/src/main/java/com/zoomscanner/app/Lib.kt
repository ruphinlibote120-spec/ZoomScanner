package com.zoomscanner.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bibliothèque de documents (dossier privé de l'application) et petits utilitaires fichiers. */
object Lib {
    fun dir(ctx: Context): File = File(ctx.filesDir, "docs").also { it.mkdirs() }

    fun list(ctx: Context): List<File> =
        dir(ctx).listFiles { f -> f.isFile && f.extension.equals("pdf", true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    private fun stamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH.mm", Locale.getDefault()).format(Date())

    fun clean(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifEmpty { "Document" }

    fun uniqueFile(ctx: Context, baseName: String): File {
        val base = clean(baseName)
        var f = File(dir(ctx), "$base.pdf")
        var i = 2
        while (f.exists()) {
            f = File(dir(ctx), "$base ($i).pdf")
            i++
        }
        return f
    }

    fun newFile(ctx: Context, prefix: String): File = uniqueFile(ctx, "$prefix ${stamp()}")

    fun derived(ctx: Context, src: File, suffix: String): File =
        uniqueFile(ctx, "${src.nameWithoutExtension} ($suffix)")

    private fun displayName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /** Copie un fichier (PDF) dans la bibliothèque. */
    fun importUri(ctx: Context, uri: Uri, prefix: String, keepName: Boolean = true): File {
        val name = if (keepName) {
            displayName(ctx, uri)?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }
        } else null
        val out = if (name != null) uniqueFile(ctx, name) else newFile(ctx, prefix)
        ctx.contentResolver.openInputStream(uri)!!.use { i ->
            out.outputStream().use { o -> i.copyTo(o) }
        }
        return out
    }

    fun copyTo(ctx: Context, file: File, dest: Uri) {
        ctx.contentResolver.openOutputStream(dest)!!.use { o ->
            file.inputStream().use { it.copyTo(o) }
        }
    }

    fun uriFor(ctx: Context, file: File): Uri =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)

    fun share(ctx: Context, file: File) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uriFor(ctx, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(i, "Partager")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    fun shareText(ctx: Context, text: String) {
        val i = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(i, "Partager le texte")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    fun sizeText(bytes: Long): String = when {
        bytes >= 1_048_576L -> String.format(Locale.getDefault(), "%.1f Mo", bytes / 1_048_576.0)
        bytes >= 1024L -> "${bytes / 1024} Ko"
        else -> "$bytes o"
    }

    fun dateText(ms: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ms))

    /** Enregistre une image dans la galerie (Images/ZoomScanner). */
    fun saveJpeg(ctx: Context, bmp: Bitmap, name: String): Boolean {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, clean(name) + ".jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZoomScanner")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        val ok = ctx.contentResolver.openOutputStream(uri)?.use {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, it)
        } ?: false
        return ok
    }

    /** Ouvre la boîte d'impression Android pour un PDF. */
    fun print(ctx: Context, file: File) {
        val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
        val adapter = object : PrintDocumentAdapter() {
            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal?,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal?.isCanceled == true) {
                    callback.onLayoutCancelled()
                    return
                }
                val info = PrintDocumentInfo.Builder(file.name)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                    .build()
                callback.onLayoutFinished(info, true)
            }

            override fun onWrite(
                pages: Array<out PageRange>?,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal?,
                callback: WriteResultCallback
            ) {
                try {
                    FileInputStream(file).use { input ->
                        FileOutputStream(destination.fileDescriptor).use { output ->
                            input.copyTo(output)
                        }
                    }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }
        }
        pm.print(file.nameWithoutExtension, adapter, null)
    }

    // ---------- Favoris, dossiers, corbeille ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("zoomscanner", Context.MODE_PRIVATE)

    fun trashDir(ctx: Context): File = File(ctx.filesDir, "trash").also { it.mkdirs() }

    private fun pdfsIn(d: File): List<File> =
        d.listFiles { f -> f.isFile && f.extension.equals("pdf", true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun folders(ctx: Context): List<String> =
        dir(ctx).listFiles { f -> f.isDirectory }?.map { it.name }?.sorted() ?: emptyList()

    fun listIn(ctx: Context, folder: String): List<File> = pdfsIn(File(dir(ctx), folder))

    fun listAll(ctx: Context): List<File> =
        (list(ctx) + folders(ctx).flatMap { listIn(ctx, it) }).sortedByDescending { it.lastModified() }

    fun relPath(ctx: Context, f: File): String = f.relativeTo(dir(ctx)).path

    fun createFolder(ctx: Context, name: String): Boolean {
        val n = clean(name)
        val d = File(dir(ctx), n)
        return if (d.exists()) false else d.mkdirs()
    }

    fun deleteFolderIfEmpty(ctx: Context, name: String): Boolean {
        val d = File(dir(ctx), name)
        return d.isDirectory && (d.listFiles()?.isEmpty() ?: true) && d.delete()
    }

    private fun favSet(ctx: Context): MutableSet<String> =
        HashSet(prefs(ctx).getStringSet("favs", emptySet()) ?: emptySet())

    fun isFav(ctx: Context, f: File): Boolean = favSet(ctx).contains(relPath(ctx, f))

    fun toggleFav(ctx: Context, f: File): Boolean {
        val s = favSet(ctx)
        val key = relPath(ctx, f)
        val now = if (s.contains(key)) {
            s.remove(key)
            false
        } else {
            s.add(key)
            true
        }
        prefs(ctx).edit().putStringSet("favs", s).apply()
        return now
    }

    fun favorites(ctx: Context): List<File> =
        favSet(ctx).map { File(dir(ctx), it) }.filter { it.isFile }
            .sortedByDescending { it.lastModified() }

    private fun renameFav(ctx: Context, oldKey: String, newKey: String?) {
        val s = favSet(ctx)
        if (s.remove(oldKey)) {
            if (newKey != null) s.add(newKey)
            prefs(ctx).edit().putStringSet("favs", s).apply()
        }
    }

    /** Renomme un document (garde le favori). Retourne le nouveau fichier, ou null si impossible. */
    fun rename(ctx: Context, f: File, newName: String): File? {
        val target = File(f.parentFile, clean(newName) + ".pdf")
        if (target.exists()) return null
        val oldKey = relPath(ctx, f)
        if (!f.renameTo(target)) return null
        renameFav(ctx, oldKey, relPath(ctx, target))
        return target
    }

    /** Déplace dans un dossier (folder = null : retour à la racine). */
    fun moveTo(ctx: Context, f: File, folder: String?): File? {
        val destDir = if (folder == null) dir(ctx) else File(dir(ctx), folder).also { it.mkdirs() }
        var target = File(destDir, f.name)
        var i = 2
        while (target.exists()) {
            target = File(destDir, f.nameWithoutExtension + " ($i).pdf")
            i++
        }
        val oldKey = relPath(ctx, f)
        if (!f.renameTo(target)) return null
        renameFav(ctx, oldKey, relPath(ctx, target))
        return target
    }

    fun moveToTrash(ctx: Context, f: File) {
        val oldKey = relPath(ctx, f)
        var target = File(trashDir(ctx), f.name)
        var i = 2
        while (target.exists()) {
            target = File(trashDir(ctx), f.nameWithoutExtension + " ($i).pdf")
            i++
        }
        if (!f.renameTo(target)) {
            f.copyTo(target, overwrite = true)
            f.delete()
        }
        renameFav(ctx, oldKey, null)
        textFile(ctx, f).delete()
    }

    fun trashList(ctx: Context): List<File> = pdfsIn(trashDir(ctx))

    fun restore(ctx: Context, f: File): File? {
        var target = File(dir(ctx), f.name)
        var i = 2
        while (target.exists()) {
            target = File(dir(ctx), f.nameWithoutExtension + " ($i).pdf")
            i++
        }
        return if (f.renameTo(target)) target else null
    }

    fun emptyTrash(ctx: Context) {
        trashDir(ctx).listFiles()?.forEach { it.delete() }
    }

    // ---------- Texte reconnu (pour la recherche dans les documents) ----------

    private fun textDir(ctx: Context): File = File(ctx.filesDir, "texts").also { it.mkdirs() }

    fun textFile(ctx: Context, f: File): File =
        File(textDir(ctx), relPathSafe(ctx, f).replace('/', '_').replace('\\', '_') + ".txt")

    private fun relPathSafe(ctx: Context, f: File): String =
        try { relPath(ctx, f) } catch (e: Exception) { f.name }

    fun saveText(ctx: Context, f: File, text: String) {
        try {
            textFile(ctx, f).writeText(text)
        } catch (e: Exception) {
            // la recherche dans le texte est un confort : on ignore l'erreur
        }
    }

    /** Documents dont le nom ou le texte reconnu contient la recherche. */
    fun search(ctx: Context, query: String): List<File> {
        val q = norm(query.trim())
        if (q.isEmpty()) return emptyList()
        return listAll(ctx).filter { f ->
            norm(f.nameWithoutExtension).contains(q) ||
                textFile(ctx, f).let { t -> t.isFile && norm(t.readText()).contains(q) }
        }
    }

    // ---------- Images : enregistrement dans la galerie avec format au choix ----------

    /** format : 0 = JPEG, 1 = PNG, 2 = WebP. Retourne la taille du fichier créé (octets), ou -1. */
    @Suppress("DEPRECATION")
    fun saveImage(ctx: Context, src: Bitmap, name: String, format: Int, quality: Int): Long {
        val (cf, ext, mime) = when (format) {
            1 -> Triple(Bitmap.CompressFormat.PNG, "png", "image/png")
            2 -> Triple(
                if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                else Bitmap.CompressFormat.WEBP,
                "webp", "image/webp"
            )
            else -> Triple(Bitmap.CompressFormat.JPEG, "jpg", "image/jpeg")
        }
        val bmp = if (format == 1) src else Img.flatten(src)
        val baos = ByteArrayOutputStream()
        bmp.compress(cf, quality, baos)
        if (bmp !== src) bmp.recycle()
        val bytes = baos.toByteArray()
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, clean(name) + "." + ext)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ZoomScanner")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return -1
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return -1
        return bytes.size.toLong()
    }
}
