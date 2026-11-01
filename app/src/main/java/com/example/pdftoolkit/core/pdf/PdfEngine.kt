package com.example.pdftoolkit.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import android.graphics.Matrix as GfxMatrix
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/* ----------------------------- Public models ----------------------------- */

/** Engine ka output: cache mein bani temp file. OutputStore.commit() isse final jagah le jata hai. */
data class EngineFile(val file: File, val mimeType: String, val pageCount: Int)

/** pageIndices: 0-based. null = saare pages. */
data class MergeInput(val uri: Uri, val pageIndices: List<Int>?, val title: String)

data class MergeSettings(
    val removeBlankPages: Boolean,
    val normalizeToA4: Boolean,
    val generateBookmarks: Boolean
)

data class CompressSettings(
    val targetDpi: Int,          // 72 / 150 / 220
    val jpegQuality: Float,      // 0.1 .. 1.0
    val stripMetadata: Boolean,
    val grayscale: Boolean
)

enum class ImagePageFit { A4, LETTER, FIT_IMAGE }
enum class RasterFormat(val mime: String, val ext: String) {
    PNG("image/png", "png"), JPEG("image/jpeg", "jpg")
}

data class ExtractedText(val fullText: String, val pageTexts: List<String>, val usedOcr: Boolean)

enum class ScanPageSize { A4, LETTER, LEGAL, ORIGINAL }

/** render() ek final page bitmap banata hai (warp + filter + rotate). Engine use PDF mein daal ke recycle karta hai. */
class ScanExportPage(val render: () -> Bitmap)

/**
 * Saara PDF kaam yahin hota hai (offline, on-device).
 * - Merge / split / compress / rotate / reorder / delete / protect / watermark / text: PdfBox-Android
 * - Page render + PDF -> image: android.graphics.pdf.PdfRenderer (native)
 * - Photos -> PDF: android.graphics.pdf.PdfDocument (native)
 * - OCR: ML Kit (on-device)
 *
 * Sab functions suspend hain aur Dispatchers.IO pe chalte hain; cancel hone pe beech mein ruk jate hain.
 */
class PdfEngine(context: Context) {

    private val app = context.applicationContext
    private val workDir = File(app.cacheDir, "work").apply { mkdirs() }

    init {
        PDFBoxResourceLoader.init(app)
    }

    /* ----------------------------- MERGE ----------------------------- */

    suspend fun merge(
        inputs: List<MergeInput>,
        settings: MergeSettings,
        onProgress: (Float) -> Unit = {}
    ): EngineFile = withContext(Dispatchers.IO) {
        require(inputs.isNotEmpty()) { "Nothing to merge" }
        val sources = ArrayList<PDDocument>()
        val dest = PDDocument()
        val out = newTemp("pdf")
        try {
            val outline = if (settings.generateBookmarks) {
                PDDocumentOutline().also { dest.documentCatalog.documentOutline = it }
            } else null
            val layer = if (settings.normalizeToA4) LayerUtility(dest) else null

            inputs.forEachIndexed { index, input ->
                ensureActive()
                val src = openDoc(input.uri)
                sources.add(src)   // source dest.save() tak open rehna chahiye
                val indices = input.pageIndices ?: (0 until src.numberOfPages).toList()
                val firstPage = dest.numberOfPages

                for (pageIndex in indices) {
                    if (pageIndex !in 0 until src.numberOfPages) continue
                    if (settings.removeBlankPages && isBlankPage(src, pageIndex)) continue
                    if (layer != null) {
                        appendAsA4(dest, layer, src, pageIndex)
                    } else {
                        dest.importPage(src.getPage(pageIndex))
                    }
                }

                if (outline != null && dest.numberOfPages > firstPage) {
                    val item = PDOutlineItem()
                    item.title = input.title
                    val destination = PDPageFitDestination()
                    destination.page = dest.getPage(firstPage)
                    item.destination = destination
                    outline.addLast(item)
                }
                onProgress((index + 1f) / inputs.size)
            }

            check(dest.numberOfPages > 0) { "No pages to merge" }
            outline?.openNode()
            val pages = dest.numberOfPages
            dest.save(out)
            EngineFile(out, "application/pdf", pages)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            sources.forEach { runCatching { it.close() } }
            runCatching { dest.close() }
        }
    }

    /** Page ko A4 canvas pe fit karke (center) dest mein add karta hai. */
    private fun appendAsA4(dest: PDDocument, layer: LayerUtility, src: PDDocument, pageIndex: Int) {
        val srcPage = src.getPage(pageIndex)
        val form = layer.importPageAsForm(src, pageIndex)
        val box = srcPage.cropBox
        val rotated = srcPage.rotation % 180 != 0
        val w = if (rotated) box.height else box.width
        val h = if (rotated) box.width else box.height

        val a4 = PDRectangle.A4
        val pageSize = if (w > h) PDRectangle(a4.height, a4.width) else a4
        val scale = min(pageSize.width / w, pageSize.height / h)
        var tx = (pageSize.width - w * scale) / 2f
        var ty = (pageSize.height - h * scale) / 2f
        if (!rotated) {
            tx -= box.lowerLeftX * scale
            ty -= box.lowerLeftY * scale
        }

        val newPage = PDPage(pageSize)
        dest.addPage(newPage)
        PDPageContentStream(dest, newPage).use { cs ->
            cs.saveGraphicsState()
            cs.transform(Matrix(scale, 0f, 0f, scale, tx, ty))
            cs.drawForm(form)
            cs.restoreGraphicsState()
        }
    }

    /** Heuristic: koi image/XObject nahi + text nahi + bahut chhota content stream = blank. */
    private fun isBlankPage(doc: PDDocument, pageIndex: Int): Boolean {
        return try {
            val page = doc.getPage(pageIndex)
            val hasXObjects = page.resources?.xObjectNames?.iterator()?.hasNext() == true
            if (hasXObjects) return false
            val stripper = PDFTextStripper().apply {
                startPage = pageIndex + 1
                endPage = pageIndex + 1
            }
            if (stripper.getText(doc).trim().isNotEmpty()) return false
            val size = page.contents?.use { it.readBytes().size } ?: 0
            size < 64
        } catch (_: Throwable) {
            false
        }
    }

    /* ----------------------------- SPLIT ----------------------------- */

    /**
     * groups: har group ek output file hai. Pages 1-based.
     * 1 group -> single PDF, 1 se zyada -> ZIP (har group ek PDF).
     */
    suspend fun split(
        uri: Uri,
        groups: List<List<Int>>,
        baseName: String,
        onProgress: (Float) -> Unit = {}
    ): EngineFile = withContext(Dispatchers.IO) {
        require(groups.isNotEmpty() && groups.all { it.isNotEmpty() }) { "No pages selected" }
        val src = openDoc(uri)
        val out = newTemp(if (groups.size == 1) "pdf" else "zip")
        try {
            if (groups.size == 1) {
                writePages(src, groups[0], out)
                EngineFile(out, "application/pdf", groups[0].size)
            } else {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
                    groups.forEachIndexed { i, pages ->
                        ensureActive()
                        val part = newTemp("pdf")
                        try {
                            writePages(src, pages, part)
                            val entry = "${baseName}_part_${(i + 1).toString().padStart(2, '0')}.pdf"
                            zip.putNextEntry(ZipEntry(entry))
                            part.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        } finally {
                            part.delete()
                        }
                        onProgress((i + 1f) / groups.size)
                    }
                }
                EngineFile(out, "application/zip", groups.sumOf { it.size })
            }
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            runCatching { src.close() }
        }
    }

    private fun writePages(src: PDDocument, pages1Based: List<Int>, out: File) {
        val doc = PDDocument()
        try {
            pages1Based.forEach { p ->
                if (p in 1..src.numberOfPages) doc.importPage(src.getPage(p - 1))
            }
            doc.save(out)
        } finally {
            doc.close()
        }
    }

    /* ----------------------------- COMPRESS ----------------------------- */

    /**
     * Embedded images ko target DPI pe downsample + JPEG re-encode karta hai, metadata strip karta hai.
     * Agar result original se bada nikle to original hi return hota hai (kabhi bada nahi hoga).
     */
    suspend fun compress(
        uri: Uri,
        settings: CompressSettings,
        originalSizeBytes: Long,
        onProgress: (Float) -> Unit = {}
    ): EngineFile = withContext(Dispatchers.IO) {
        val doc = openDoc(uri)
        val out = newTemp("pdf")
        try {
            val cache = HashMap<COSBase, PDImageXObject?>()
            val total = doc.numberOfPages.coerceAtLeast(1)
            var done = 0
            for (page in doc.pages) {
                ensureActive()
                recompressPageImages(doc, page, settings, cache)
                done++
                onProgress(done / total.toFloat() * 0.9f)
            }
            if (settings.stripMetadata) {
                doc.documentInformation = PDDocumentInformation()
                doc.documentCatalog.metadata = null
            }
            val pages = doc.numberOfPages
            doc.save(out)
            onProgress(1f)
            if (originalSizeBytes > 0 && out.length() >= originalSizeBytes) {
                // compress karke bada ho gaya -> original wapas do
                app.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
            EngineFile(out, "application/pdf", pages)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            runCatching { doc.close() }
        }
    }

    private fun recompressPageImages(
        doc: PDDocument,
        page: PDPage,
        settings: CompressSettings,
        cache: HashMap<COSBase, PDImageXObject?>
    ) {
        val resources = page.resources ?: return
        val pageWidthInches = page.mediaBox.width / 72f
        val names = resources.xObjectNames.toList()
        for (name in names) {
            val xo = runCatching { resources.getXObject(name) }.getOrNull() as? PDImageXObject ?: continue
            val key: COSBase = xo.cosObject
            val replacement = if (cache.containsKey(key)) {
                cache[key]
            } else {
                recompressImage(doc, xo, pageWidthInches, settings).also { cache[key] = it }
            }
            if (replacement != null) resources.put(name, replacement)
        }
    }

    private fun recompressImage(
        doc: PDDocument,
        xo: PDImageXObject,
        pageWidthInches: Float,
        settings: CompressSettings
    ): PDImageXObject? {
        // Transparency / mask wali images ko JPEG banane se kharab ho jati hain: skip
        if (xo.isStencil || xo.softMask != null || xo.mask != null) return null
        val originalSize = xo.stream.length
        var scaled: Bitmap? = null
        var gray: Bitmap? = null
        return try {
            val bitmap = xo.image
            var work = bitmap
            val effectiveDpi = if (pageWidthInches > 0f) xo.width / pageWidthInches else settings.targetDpi.toFloat()
            if (effectiveDpi > settings.targetDpi) {
                val s = settings.targetDpi / effectiveDpi
                val w = max(1, (xo.width * s).roundToInt())
                val h = max(1, (xo.height * s).roundToInt())
                scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
                work = scaled
            }
            if (settings.grayscale) {
                gray = toGray(work)
                work = gray
            }
            val jpeg = JPEGFactory.createFromImage(doc, work, settings.jpegQuality)
            if (jpeg.stream.length < originalSize) jpeg else null
        } catch (_: Throwable) {
            null
        } finally {
            if (scaled != null && !scaled.isRecycled) scaled.recycle()
            if (gray != null && !gray.isRecycled) gray.recycle()
        }
    }

    private fun toGray(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /* ----------------------------- ROTATE / DELETE / REORDER ----------------------------- */

    /** rotations: 0-based page index -> extra degrees (90/180/270). */
    suspend fun rotatePages(uri: Uri, rotations: Map<Int, Int>): EngineFile = editDocument(uri) { doc ->
        rotations.forEach { (index, degrees) ->
            if (index in 0 until doc.numberOfPages) {
                val page = doc.getPage(index)
                page.rotation = (((page.rotation + degrees) % 360) + 360) % 360
            }
        }
    }

    /** pagesToDelete: 0-based. */
    suspend fun deletePages(uri: Uri, pagesToDelete: Set<Int>): EngineFile = editDocument(uri) { doc ->
        check(pagesToDelete.size < doc.numberOfPages) { "Cannot delete all pages" }
        pagesToDelete.sortedDescending().forEach { index ->
            if (index in 0 until doc.numberOfPages) doc.removePage(index)
        }
    }

    /** newOrder: 0-based source page indexes naye order mein. */
    suspend fun reorderPages(uri: Uri, newOrder: List<Int>): EngineFile = withContext(Dispatchers.IO) {
        require(newOrder.isNotEmpty())
        val src = openDoc(uri)
        val dest = PDDocument()
        val out = newTemp("pdf")
        try {
            newOrder.forEach { i -> if (i in 0 until src.numberOfPages) dest.importPage(src.getPage(i)) }
            val pages = dest.numberOfPages
            dest.save(out)
            EngineFile(out, "application/pdf", pages)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            runCatching { src.close() }
            runCatching { dest.close() }
        }
    }

    private suspend fun editDocument(uri: Uri, block: (PDDocument) -> Unit): EngineFile =
        withContext(Dispatchers.IO) {
            val doc = openDoc(uri)
            val out = newTemp("pdf")
            try {
                block(doc)
                val pages = doc.numberOfPages
                doc.save(out)
                EngineFile(out, "application/pdf", pages)
            } catch (t: Throwable) {
                out.delete()
                throw t
            } finally {
                runCatching { doc.close() }
            }
        }

    /* ----------------------------- PROTECT (password) ----------------------------- */

    suspend fun protect(
        uri: Uri,
        userPassword: String,
        ownerPassword: String? = null,
        allowPrint: Boolean = true,
        allowCopy: Boolean = false,
        allowModify: Boolean = false
    ): EngineFile = editDocument(uri) { doc ->
        val permission = AccessPermission().apply {
            setCanPrint(allowPrint)
            setCanExtractContent(allowCopy)
            setCanModify(allowModify)
        }
        val owner = ownerPassword?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val policy = StandardProtectionPolicy(owner, userPassword, permission).apply {
            encryptionKeyLength = 128
            isPreferAES = true
        }
        doc.protect(policy)
    }

    /* ----------------------------- WATERMARK ----------------------------- */

    /** Text watermark har page ke center mein (Latin characters only - Helvetica font). */
    suspend fun watermarkText(
        uri: Uri,
        text: String,
        opacity: Float = 0.25f,
        fontSize: Float = 56f,
        angleDegrees: Float = 45f,
        red: Float = 0.5f, green: Float = 0.5f, blue: Float = 0.5f
    ): EngineFile = editDocument(uri) { doc ->
        val safeText = text.filter { it.code in 32..126 }.ifEmpty { "CONFIDENTIAL" }
        val font = PDType1Font.HELVETICA_BOLD
        val textWidth = font.getStringWidth(safeText) / 1000f * fontSize
        val gstate = PDExtendedGraphicsState().apply {
            nonStrokingAlphaConstant = opacity.coerceIn(0.05f, 1f)
        }
        for (page in doc.pages) {
            val box = page.mediaBox
            val cx = box.lowerLeftX + box.width / 2f
            val cy = box.lowerLeftY + box.height / 2f
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setGraphicsStateParameters(gstate)
                cs.setNonStrokingColor(red, green, blue)
                cs.transform(Matrix.getTranslateInstance(cx, cy))
                cs.transform(Matrix.getRotateInstance(Math.toRadians(angleDegrees.toDouble()), 0f, 0f))
                cs.beginText()
                cs.setFont(font, fontSize)
                cs.newLineAtOffset(-textWidth / 2f, -fontSize / 4f)
                cs.showText(safeText)
                cs.endText()
            }
        }
    }

    /* ----------------------------- PHOTOS -> PDF (native PdfDocument) ----------------------------- */

    suspend fun imagesToPdf(
        images: List<Uri>,
        fit: ImagePageFit = ImagePageFit.A4,
        marginPoints: Float = 24f,
        maxImageSide: Int = 2400,
        onProgress: (Float) -> Unit = {}
    ): EngineFile = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "No images" }
        val doc = PdfDocument()
        val out = newTemp("pdf")
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        try {
            images.forEachIndexed { i, uri ->
                ensureActive()
                val bmp = decodeBitmap(uri, maxImageSide)
                try {
                    var pw: Int
                    var ph: Int
                    var margin = marginPoints
                    when (fit) {
                        ImagePageFit.A4 -> { pw = 595; ph = 842 }
                        ImagePageFit.LETTER -> { pw = 612; ph = 792 }
                        ImagePageFit.FIT_IMAGE -> {
                            pw = (bmp.width * 72f / 150f).roundToInt().coerceAtLeast(1)
                            ph = (bmp.height * 72f / 150f).roundToInt().coerceAtLeast(1)
                            margin = 0f
                        }
                    }
                    if (fit != ImagePageFit.FIT_IMAGE && bmp.width > bmp.height) {
                        val t = pw; pw = ph; ph = t     // landscape photo -> landscape page
                    }
                    val page = doc.startPage(PdfDocument.PageInfo.Builder(pw, ph, i + 1).create())
                    val avail = RectF(margin, margin, pw - margin, ph - margin)
                    val scale = min(avail.width() / bmp.width, avail.height() / bmp.height)
                    val dw = bmp.width * scale
                    val dh = bmp.height * scale
                    val left = avail.left + (avail.width() - dw) / 2f
                    val top = avail.top + (avail.height() - dh) / 2f
                    page.canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
                    doc.finishPage(page)
                } finally {
                    bmp.recycle()
                }
                onProgress((i + 1f) / images.size)
            }
            FileOutputStream(out).use { doc.writeTo(it) }
            EngineFile(out, "application/pdf", images.size)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            doc.close()
        }
    }

    private fun decodeBitmap(uri: Uri, maxSide: Int): Bitmap {
        val resolver = app.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw IOException("Cannot decode image")

        val orientation = try {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation != 0f) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, GfxMatrix().apply { postRotate(rotation) }, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        val longest = max(bmp.width, bmp.height)
        if (longest > maxSide) {
            val f = maxSide / longest.toFloat()
            val resized = Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true)
            if (resized !== bmp) bmp.recycle()
            bmp = resized
        }
        return bmp
    }

    /* ----------------------------- PDF -> IMAGES (native PdfRenderer) ----------------------------- */

    /** pages: 0-based. 1 page -> single image, zyada -> ZIP. */
    suspend fun pdfToImages(
        uri: Uri,
        pages: List<Int>,
        dpi: Int = 150,
        format: RasterFormat = RasterFormat.PNG,
        jpegQuality: Int = 90,
        baseName: String = "page",
        onProgress: (Float) -> Unit = {}
    ): EngineFile = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "No pages selected" }
        val source = copyToCache(uri)
        val pfd = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        val out = newTemp(if (pages.size == 1) format.ext else "zip")
        try {
            if (pages.size == 1) {
                val bmp = renderPage(renderer, pages[0], dpi)
                try {
                    FileOutputStream(out).use { compressBitmap(bmp, format, jpegQuality, it) }
                } finally {
                    bmp.recycle()
                }
                EngineFile(out, format.mime, 1)
            } else {
                ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
                    pages.forEachIndexed { i, index ->
                        ensureActive()
                        val bmp = renderPage(renderer, index, dpi)
                        try {
                            zip.putNextEntry(ZipEntry("${baseName}_${(index + 1).toString().padStart(3, '0')}.${format.ext}"))
                            compressBitmap(bmp, format, jpegQuality, zip)
                            zip.closeEntry()
                        } finally {
                            bmp.recycle()
                        }
                        onProgress((i + 1f) / pages.size)
                    }
                }
                EngineFile(out, "application/zip", pages.size)
            }
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
            source.delete()
        }
    }

    private fun compressBitmap(bmp: Bitmap, format: RasterFormat, quality: Int, out: java.io.OutputStream) {
        val cf = if (format == RasterFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        bmp.compress(cf, quality, out)
    }

    private fun renderPage(renderer: PdfRenderer, index: Int, dpi: Int): Bitmap {
        val page = renderer.openPage(index)
        try {
            val scale = dpi / 72f
            var w = (page.width * scale).roundToInt().coerceAtLeast(1)
            var h = (page.height * scale).roundToInt().coerceAtLeast(1)
            val longest = max(w, h)
            val maxSide = 4800
            if (longest > maxSide) {
                val f = maxSide / longest.toFloat()
                w = (w * f).roundToInt()
                h = (h * f).roundToInt()
            }
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bmp
        } finally {
            page.close()
        }
    }

    /* ----------------------------- TEXT EXTRACTION (+ OCR) ----------------------------- */

    /** pages: 0-based, null = sab. Digital PDF -> PdfBox text; scanned page -> ML Kit OCR (agar ocrFallback). */
    suspend fun extractText(
        uri: Uri,
        pages: List<Int>? = null,
        ocrFallback: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): ExtractedText = withContext(Dispatchers.IO) {
        val doc = openDoc(uri)
        var renderFile: File? = null
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var usedOcr = false
        try {
            val indices = pages ?: (0 until doc.numberOfPages).toList()
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val texts = ArrayList<String>()
            indices.forEachIndexed { i, index ->
                ensureActive()
                stripper.startPage = index + 1
                stripper.endPage = index + 1
                var text = stripper.getText(doc).trim()
                if (text.isEmpty() && ocrFallback) {
                    if (renderer == null) {
                        renderFile = copyToCache(uri)
                        pfd = ParcelFileDescriptor.open(renderFile, ParcelFileDescriptor.MODE_READ_ONLY)
                        renderer = PdfRenderer(pfd!!)
                    }
                    val bmp = renderPage(renderer!!, index, 200)
                    try {
                        text = OcrEngine.recognize(bmp).trim()
                        usedOcr = true
                    } finally {
                        bmp.recycle()
                    }
                }
                texts.add(text)
                onProgress((i + 1f) / indices.size)
            }
            ExtractedText(texts.joinToString("\n\n"), texts, usedOcr)
        } finally {
            runCatching { renderer?.close() }
            runCatching { pfd?.close() }
            renderFile?.delete()
            runCatching { doc.close() }
        }
    }

    suspend fun saveText(text: String): EngineFile = withContext(Dispatchers.IO) {
        val out = newTemp("txt")
        out.writeText(text, Charsets.UTF_8)
        EngineFile(out, "text/plain", 0)
    }

    /** PDF text -> .docx (har PDF page ke baad page break). Minimal OOXML, Word / Docs dono kholte hain. */
    suspend fun saveDocx(pageTexts: List<String>): EngineFile = withContext(Dispatchers.IO) {
        val out = newTemp("docx")
        val body = StringBuilder()
        pageTexts.forEachIndexed { i, pageText ->
            pageText.split('\n').forEach { line ->
                body.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(xmlEscape(line)).append("</w:t></w:r></w:p>")
            }
            if (i < pageTexts.lastIndex) body.append("<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>")
        }
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>"""
        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>"""
        val document = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>$body</w:body></w:document>"""
        ZipOutputStream(BufferedOutputStream(FileOutputStream(out))).use { zip ->
            fun put(name: String, content: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            put("[Content_Types].xml", contentTypes)
            put("_rels/.rels", rels)
            put("word/document.xml", document)
        }
        EngineFile(out, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", pageTexts.size)
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .filter { it.code >= 32 || it == '\t' }   // XML ke invalid control chars hata do

    /* ----------------------------- SCAN PAGES -> PDF (+ searchable OCR layer) ----------------------------- */

    /**
     * Scanned pages ko PDF mein jodta hai. Har page JPEG (quality control) + optional invisible OCR text layer
     * (searchable / copyable PDF). Pages ek-ek karke render hote hain, taaki memory kam rahe.
     */
    suspend fun scanPagesToPdf(
        pages: List<ScanExportPage>,
        size: ScanPageSize,
        jpegQuality: Float,
        searchable: Boolean,
        onProgress: (Float) -> Unit = {}
    ): EngineFile = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "No pages to save" }
        val doc = PDDocument()
        val out = newTemp("pdf")
        try {
            val font = PDType1Font.HELVETICA
            pages.forEachIndexed { index, exportPage ->
                ensureActive()
                val bmp = exportPage.render()
                try {
                    var pw: Float
                    var ph: Float
                    when (size) {
                        ScanPageSize.A4 -> { pw = 595.28f; ph = 841.89f }
                        ScanPageSize.LETTER -> { pw = 612f; ph = 792f }
                        ScanPageSize.LEGAL -> { pw = 612f; ph = 1008f }
                        ScanPageSize.ORIGINAL -> { pw = bmp.width * 72f / 200f; ph = bmp.height * 72f / 200f }
                    }
                    if (size != ScanPageSize.ORIGINAL && bmp.width > bmp.height) {
                        val t = pw; pw = ph; ph = t      // landscape scan -> landscape page
                    }
                    val scale = min(pw / bmp.width, ph / bmp.height)
                    val dw = bmp.width * scale
                    val dh = bmp.height * scale
                    val x0 = (pw - dw) / 2f
                    val y0 = (ph - dh) / 2f

                    val pdPage = PDPage(PDRectangle(pw, ph))
                    doc.addPage(pdPage)
                    val image = JPEGFactory.createFromImage(doc, bmp, jpegQuality)
                    val lines = if (searchable) {
                        runCatching { OcrEngine.recognizeLines(bmp) }.getOrDefault(emptyList())
                    } else emptyList()

                    PDPageContentStream(doc, pdPage).use { cs ->
                        cs.drawImage(image, x0, y0, dw, dh)
                        if (lines.isNotEmpty()) {
                            cs.beginText()
                            cs.setRenderingMode(RenderingMode.NEITHER)     // invisible text
                            for (line in lines) {
                                val text = line.text.filter { it.code in 32..126 }.trim()
                                if (text.isEmpty()) continue
                                val widthPts = (line.right - line.left) * scale
                                val heightPts = (line.bottom - line.top) * scale
                                val fontSize = (heightPts * 0.8f).coerceAtLeast(4f)
                                val natural = font.getStringWidth(text) / 1000f * fontSize
                                if (natural <= 0f) continue
                                val sx = widthPts / natural
                                val tx = x0 + line.left * scale
                                val ty = y0 + (bmp.height - line.bottom) * scale
                                cs.setFont(font, fontSize)
                                cs.setTextMatrix(Matrix(sx, 0f, 0f, 1f, tx, ty))
                                cs.showText(text)
                            }
                            cs.endText()
                        }
                    }
                } finally {
                    bmp.recycle()
                }
                onProgress((index + 1f) / pages.size)
            }
            val count = doc.numberOfPages
            doc.save(out)
            EngineFile(out, "application/pdf", count)
        } catch (t: Throwable) {
            out.delete()
            throw t
        } finally {
            runCatching { doc.close() }
        }
    }

    /* ----------------------------- Helpers ----------------------------- */

    private fun newTemp(ext: String): File = File.createTempFile("job_", ".$ext", workDir)

    private fun openDoc(uri: Uri, password: String? = null): PDDocument {
        val input = app.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        return input.use { s -> if (password == null) PDDocument.load(s) else PDDocument.load(s, password) }
    }

    private fun copyToCache(uri: Uri): File {
        val f = newTemp("pdf")
        val input = app.contentResolver.openInputStream(uri) ?: throw IOException("Cannot open file")
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    /** Purani temp files saaf karo (app start pe call karna). */
    fun cleanWorkDir() {
        workDir.listFiles()?.forEach { it.delete() }
    }
}