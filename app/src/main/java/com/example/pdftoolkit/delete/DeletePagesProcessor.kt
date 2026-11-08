package com.example.pdftoolkit.delete

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** PDF page deletion logic. No UI code here. */
class DeletePagesProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("delete_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "delete_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    /**
     * Writes a new PDF without the pages in [pagesToDelete] (zero-based indexes).
     * Refuses to create an empty PDF. Deleted pages' content is not written to the output.
     */
    suspend fun deletePages(
        source: File,
        pagesToDelete: Set<Int>,
        output: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val total = doc.numberOfPages
            require(pagesToDelete.isNotEmpty()) { "No pages selected" }
            require(pagesToDelete.all { it in 0 until total }) { "Invalid page selection" }
            require(pagesToDelete.size < total) { "At least one page must remain in the PDF" }

            // Remove from the end so earlier indexes stay valid.
            val sorted = pagesToDelete.sortedDescending()
            sorted.forEachIndexed { i, index ->
                ensureActive()
                doc.removePage(index)
                onProgress((i + 1f) / sorted.size)
            }

            ensureActive()
            doc.save(output)
        }
    }
}