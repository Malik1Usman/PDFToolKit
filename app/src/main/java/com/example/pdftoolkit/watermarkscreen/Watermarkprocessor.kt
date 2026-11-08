package com.example.pdftoolkit.watermarkscreen

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

enum class WatermarkPosition(val label: String) {
    CENTER("Center"), TOP("Top"), BOTTOM("Bottom"), DIAGONAL("Diagonal")
}

/** [angleDeg] is counter-clockwise (same as PDF). */
data class WatermarkParams(
    val text: String,
    val fontSizePt: Float,
    val colorArgb: Int,
    val opacity: Float,
    val angleDeg: Float,
    val position: WatermarkPosition
)

/** Visible page size in points (page rotation already applied). */
data class PageSize(val width: Float, val height: Float)

class WatermarkImage(val bitmap: Bitmap, val widthPt: Float, val heightPt: Float)

/**
 * Draws the watermark text with Android's text engine, so every script works
 * (Latin, Arabic, Urdu, Hindi…, with correct shaping and RTL). Preview and export use this same code.
 */
object WatermarkRenderer {

    fun render(
        text: String,
        sizePt: Float,
        color: Int,
        pxPerPt: Float,
        maxWidthPx: Int
    ): WatermarkImage {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color or 0xFF000000.toInt()
            typeface = Typeface.DEFAULT_BOLD
        }
        var scale = pxPerPt
        paint.textSize = sizePt * scale
        var textW = paint.measureText(text)
        if (textW > maxWidthPx) {
            scale *= maxWidthPx / textW
            paint.textSize = sizePt * scale
            textW = paint.measureText(text)
        }

        val fm = paint.fontMetrics
        val pad = ceil(paint.textSize * 0.08f).toInt()
        val w = ceil(textW).toInt().coerceAtLeast(1) + pad * 2
        val h = ceil(fm.descent - fm.ascent).toInt().coerceAtLeast(1) + pad * 2

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad.toFloat(), pad - fm.ascent, paint)
        return WatermarkImage(bmp, w / scale, h / scale)
    }
}

object WatermarkGeometry {
    /** Watermark centre in VISIBLE page coordinates, origin bottom-left. */
    fun center(
        position: WatermarkPosition,
        pageW: Float,
        pageH: Float,
        wmH: Float
    ): Pair<Float, Float> = when (position) {
        WatermarkPosition.CENTER, WatermarkPosition.DIAGONAL -> pageW / 2 to pageH / 2
        WatermarkPosition.TOP -> pageW / 2 to pageH - (pageH * 0.05f + wmH / 2)
        WatermarkPosition.BOTTOM -> pageW / 2 to (pageH * 0.05f + wmH / 2)
    }
}

/** PDF watermark logic. No UI code here. */
class WatermarkProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("watermark_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "watermark_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    /** Visible size of every page. Throws if the PDF needs a password. */
    suspend fun inspect(file: File): List<PageSize> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                (0 until doc.numberOfPages).map { i ->
                    val page = doc.getPage(i)
                    val box = page.cropBox
                    val rot = ((page.rotation % 360) + 360) % 360
                    if (rot == 90 || rot == 270) PageSize(box.height, box.width)
                    else PageSize(box.width, box.height)
                }
            }
        } catch (e: InvalidPasswordException) {
            throw IllegalStateException(
                "This PDF needs a password to open. Remove the password first, then add the watermark."
            )
        }
    }

    /**
     * Stamps the watermark on [pages] (zero-based indexes) or on every page when [pages] is null.
     * Writes a new file to [output]; [source] is never modified.
     */
    suspend fun addWatermark(
        source: File,
        output: File,
        params: WatermarkParams,
        pages: Set<Int>?,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val wm = WatermarkRenderer.render(
            text = params.text,
            sizePt = params.fontSizePt,
            color = params.colorArgb,
            pxPerPt = 4f,
            maxWidthPx = 3000
        )

        try {
            PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val total = doc.numberOfPages
                val targets = (pages ?: (0 until total).toSet()).filter { it in 0 until total }.sorted()
                require(targets.isNotEmpty()) { "No pages selected" }

                val image = LosslessFactory.createFromImage(doc, wm.bitmap)   // shared by all pages
                val gs = PDExtendedGraphicsState().apply {
                    nonStrokingAlphaConstant = params.opacity.coerceIn(0.01f, 1f)
                }

                targets.forEachIndexed { n, index ->
                    ensureActive()
                    val page = doc.getPage(index)
                    val box = page.cropBox
                    val rot = ((page.rotation % 360) + 360) % 360
                    val cw = box.width
                    val ch = box.height
                    val visW = if (rot == 90 || rot == 270) ch else cw
                    val visH = if (rot == 90 || rot == 270) cw else ch

                    // position in visible space → content space
                    val (vx, vy) = WatermarkGeometry.center(params.position, visW, visH, wm.heightPt)
                    val (cx, cy) = when (rot) {
                        90 -> (cw - vy) to vx
                        180 -> (cw - vx) to (ch - vy)
                        270 -> vy to (ch - vx)
                        else -> vx to vy
                    }
                    val x = cx + box.lowerLeftX
                    val y = cy + box.lowerLeftY

                    // visual angle φ (CCW) becomes φ + pageRotation in content space
                    val theta = Math.toRadians((params.angleDeg + rot).toDouble())
                    val cosT = cos(theta).toFloat()
                    val sinT = sin(theta).toFloat()
                    val w = wm.widthPt
                    val h = wm.heightPt
                    val matrix = Matrix(
                        w * cosT, w * sinT,
                        -h * sinT, h * cosT,
                        x - (w / 2 * cosT - h / 2 * sinT),
                        y - (w / 2 * sinT + h / 2 * cosT)
                    )

                    PDPageContentStream(
                        doc, page, PDPageContentStream.AppendMode.APPEND, true, true
                    ).use { cs ->
                        cs.saveGraphicsState()
                        cs.setGraphicsStateParameters(gs)
                        cs.drawImage(image, matrix)
                        cs.restoreGraphicsState()
                    }

                    onProgress(0.9f * (n + 1) / targets.size, "Page ${n + 1} of ${targets.size}")
                }

                ensureActive()
                onProgress(0.92f, "Saving…")
                doc.save(output)
            }
        } finally {
            wm.bitmap.recycle()
        }
    }
}