package com.example.pdftoolkit.rotate

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.max

/** PDF + image rotation logic. No UI code here. */
class RotateProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("rotate_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "rotate_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    /**
     * [rotations] = extra clockwise degrees (0/90/180/270) per page, in page order.
     * The extra rotation is ADDED to whatever rotation the page already has.
     */
    suspend fun rotatePdf(
        source: File,
        rotations: List<Int>,
        output: File,
        onProgress: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val total = doc.numberOfPages
            require(rotations.size == total) { "Page count mismatch" }
            for (i in 0 until total) {
                ensureActive()
                val extra = rotations[i]
                if (extra != 0) {
                    val page = doc.getPage(i)
                    page.rotation = ((page.rotation + extra) % 360 + 360) % 360
                }
                onProgress((i + 1f) / total)
            }
            ensureActive()
            doc.save(output)
        }
    }

    /** Decodes an image (down-sampled to roughly [maxSide]) with its EXIF orientation applied. */
    suspend fun decodeImage(uri: Uri, maxSide: Int): Bitmap = withContext(Dispatchers.IO) {
        val resolver = appContext.contentResolver

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("This image cannot be opened")
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: throw IOException("This image cannot be opened")

        val exifDegrees = readExifDegrees(uri)
        if (exifDegrees == 0) decoded
        else decoded.rotatedCopy(exifDegrees).also { decoded.recycle() }
    }

    /** Rotates the image by [degrees] clockwise and writes it to [output] (PNG stays PNG, rest → JPEG). */
    suspend fun rotateImage(uri: Uri, degrees: Int, output: File, mimeType: String) =
        withContext(Dispatchers.IO) {
            val source = decodeImage(uri, maxSide = 4096)   // very large photos are capped at ~4K to avoid OOM
            ensureActive()
            val rotated = source.rotatedCopy(degrees)
            if (rotated !== source) source.recycle()
            try {
                output.outputStream().use { out ->
                    val ok = if (mimeType == "image/png")
                        rotated.compress(Bitmap.CompressFormat.PNG, 100, out)
                    else
                        rotated.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    if (!ok) throw IOException("Could not encode the image")
                }
            } finally {
                rotated.recycle()
            }
        }

    private fun readExifDegrees(uri: Uri): Int = try {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }
}

/** Returns a rotated copy (or the same instance when degrees % 360 == 0). Caller owns recycling. */
fun Bitmap.rotatedCopy(degrees: Int): Bitmap {
    val d = ((degrees % 360) + 360) % 360
    if (d == 0) return this
    val matrix = Matrix().apply { postRotate(d.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}