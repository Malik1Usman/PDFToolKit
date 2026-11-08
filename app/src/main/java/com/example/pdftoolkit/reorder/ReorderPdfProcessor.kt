package com.example.pdftoolkit.reorder

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.LruCache
import androidx.core.content.FileProvider
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * PDF processing for the Reorder screen. No UI code here.
 * Uses PDFBox-Android for the real reorder (text / vectors stay intact).
 */
class ReorderPdfProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    /** Copies the picked Uri into the cache so PdfRenderer and PDFBox can use a real file. */
    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("reorder_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "reorder_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    /**
     * Writes a new PDF where page i of the output = page order[i] of the source.
     * [order] holds ORIGINAL zero-based page indexes. [onProgress] gets 0f..1f.
     */
    suspend fun reorder(
        source: File,
        order: List<Int>,
        output: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val total = doc.numberOfPages
            require(order.size == total && order.toSet() == (0 until total).toSet()) {
                "Page order is invalid"
            }

            val pages = (0 until total).map { doc.getPage(it) }
            // Inherited attributes would be lost when the parent changes, so make them explicit.
            pages.forEach { p ->
                p.resources = p.resources
                p.mediaBox = p.mediaBox
                p.cropBox = p.cropBox
                p.rotation = p.rotation
            }

            for (i in total - 1 downTo 0) doc.removePage(i)

            order.forEachIndexed { newPos, originalIndex ->
                ensureActive()
                doc.addPage(pages[originalIndex])
                onProgress((newPos + 1f) / total)
            }

            ensureActive()
            doc.save(output)
        }
    }
}

/** Renders page thumbnails. PdfRenderer allows only one open page at a time, hence the Mutex. */
class PdfPageThumbnailer(file: File) : Closeable {

    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    @Volatile private var closed = false

    val pageCount: Int = renderer.pageCount

    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt()
    ) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount / 1024
    }

    suspend fun thumbnail(index: Int, targetWidthPx: Int = 360): Bitmap? =
        withContext(Dispatchers.IO) {
            cache.get(index)?.let { return@withContext it }
            mutex.withLock {
                if (closed) return@withLock null
                cache.get(index)?.let { return@withLock it }
                val page = renderer.openPage(index)
                try {
                    val height = (targetWidthPx * page.height.toFloat() / page.width).toInt()
                        .coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(targetWidthPx, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cache.put(index, bitmap)
                    bitmap
                } finally {
                    page.close()
                }
            }
        }

    override fun close() {
        runBlocking {
            mutex.withLock {
                if (!closed) {
                    closed = true
                    cache.evictAll()
                    renderer.close()
                    fd.close()
                }
            }
        }
    }
}

/**
 * Where the finished file goes. Swap this for your existing OutputStore:
 * just implement save() by calling OutputStore and returning the Uri of the saved file.
 */
interface PdfOutputSink {
    suspend fun save(file: File, displayName: String, mimeType: String = "application/pdf"): Uri
}

class MediaStoreOutputSink(context: Context) : PdfOutputSink {

    private val context = context.applicationContext

    override suspend fun save(file: File, displayName: String, mimeType: String): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val isImage = mimeType.startsWith("image/")
            val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val folder = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/PDF Toolkit")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IOException("Could not create output file")
            try {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { it.copyTo(out) }
                } ?: throw IOException("Could not open output file")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri
        } else {
            // Needs a FileProvider with authority "<applicationId>.fileprovider"
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "PDF Toolkit")
                .apply { mkdirs() }
            val dest = File(dir, displayName)
            file.copyTo(dest, overwrite = true)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", dest)
        }
    }
}