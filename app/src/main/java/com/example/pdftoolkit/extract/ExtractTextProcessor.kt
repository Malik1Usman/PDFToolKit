package com.example.pdftoolkit.extract

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Text extraction logic. No UI code here. */
class ExtractTextProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("extract_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "extract_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    /** Extracts text page by page. Returns one string per page (blank string if the page has no text). */
    suspend fun extract(
        source: File,
        onProgress: (done: Int, total: Int) -> Unit
    ): List<String> = withContext(Dispatchers.IO) {
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val total = doc.numberOfPages
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val pages = ArrayList<String>(total)

            for (page in 1..total) {
                ensureActive()
                val text = try {
                    stripper.startPage = page
                    stripper.endPage = page
                    stripper.getText(doc)
                } catch (e: Exception) {
                    ""   // one broken page should not fail the whole document
                }
                pages.add(cleanup(text))
                onProgress(page, total)
            }
            pages
        }
    }

    /** Writes the whole text as UTF-8 TXT, with a header line per page. */
    suspend fun writeTxt(pages: List<String>, output: File) = withContext(Dispatchers.IO) {
        output.bufferedWriter(Charsets.UTF_8).use { w ->
            pages.forEachIndexed { i, text ->
                ensureActive()
                if (i > 0) w.write("\n\n")
                w.write("----- Page ${i + 1} -----\n\n")
                w.write(if (text.hasRealText()) text else "[No selectable text on this page]")
            }
        }
    }

    private fun cleanup(raw: String): String =
        raw.replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
}

/** A page counts as "has text" when it has at least a few letters/digits (ignores stray page numbers). */
fun String.hasRealText(): Boolean = count { it.isLetterOrDigit() } >= 3

/** Full text with page separators, used for Copy All. */
fun buildFullText(pages: List<String>): String = buildString {
    pages.forEachIndexed { i, text ->
        if (i > 0) append("\n\n")
        append("----- Page ${i + 1} -----\n\n")
        append(if (text.hasRealText()) text else "[No selectable text on this page]")
    }
}