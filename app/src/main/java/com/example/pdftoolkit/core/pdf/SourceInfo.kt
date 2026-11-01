package com.example.pdftoolkit.core.pdf

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument

/** Picker se aaye PDF ki basic info (naam, size, pages). */
data class SourceInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int          // 0 = padh nahi paye (corrupt / password protected)
)

object SourceReader {

    /** Blocking hai: IO thread pe call karo. */
    fun read(context: Context, uri: Uri): SourceInfo {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "document.pdf"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (n >= 0) c.getString(n)?.let { name = it }
                val s = c.getColumnIndex(OpenableColumns.SIZE)
                if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
            }
        }
        return SourceInfo(uri, name, size, pageCount(context, uri))
    }

    fun pageCount(context: Context, uri: Uri): Int {
        // 1) Android ka native PdfRenderer (fast)
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val renderer = PdfRenderer(pfd)
                try {
                    return renderer.pageCount
                } finally {
                    renderer.close()
                }
            }
        } catch (_: Throwable) {
            // password protected / unsupported: PdfBox se try karenge
        }
        // 2) PdfBox fallback
        return try {
            PDFBoxResourceLoader.init(context.applicationContext)
            context.contentResolver.openInputStream(uri)?.use { s ->
                PDDocument.load(s).use { it.numberOfPages }
            } ?: 0
        } catch (_: Throwable) {
            0
        }
    }
}