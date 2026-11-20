package com.zoomscanner.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
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
}
