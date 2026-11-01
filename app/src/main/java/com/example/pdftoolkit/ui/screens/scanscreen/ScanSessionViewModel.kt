package com.example.pdftoolkit.ui.screens.scanscreen

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.pdf.ScanExportPage
import com.example.pdftoolkit.core.pdf.ScanPageSize
import com.example.pdftoolkit.core.scan.DocumentDetector
import com.example.pdftoolkit.core.scan.EnhanceParams
import com.example.pdftoolkit.core.scan.FlashSetting
import com.example.pdftoolkit.core.scan.ImageProcessor
import com.example.pdftoolkit.core.scan.NPoint
import com.example.pdftoolkit.core.scan.Quad
import com.example.pdftoolkit.core.scan.ScanFilter
import com.example.pdftoolkit.core.pdf.OutputStore
import com.example.pdftoolkit.core.pdf.ToolType
import com.example.pdftoolkit.ui.screens.batchreviewscreen.PageSizeOption
import com.example.pdftoolkit.ui.screens.batchreviewscreen.PdfExportOptions
import com.example.pdftoolkit.ui.screens.batchreviewscreen.QualityOption
import com.example.pdftoolkit.ui.screens.batchreviewscreen.ScannedPage
import com.example.pdftoolkit.ui.screens.components.ToolViewModel
import com.example.pdftoolkit.ui.screens.finetunescreen.EnhancePreset
import com.example.pdftoolkit.ui.screens.finetunescreen.EnhanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Ek page ka asli data: original photo + crop quad + filter settings. */
class PageData(val originalPath: String, var quad: Quad, var params: EnhanceParams)

/**
 * Pura scan flow ek session mein: camera capture, import (image / PDF), crop, filters, review, export.
 * Activity-scoped hai taaki Scanner -> Batch Review -> Crop -> Fine Tune ek hi data share karein.
 */
class ScanSessionViewModel(app: Application) : ToolViewModel(app) {

    /* ----------------------------- Session data ----------------------------- */

    val pages = mutableStateListOf<ScannedPage>()               // UI models (order / selection / rotation)
    val thumbs = mutableStateMapOf<Int, ImageBitmap>()          // processed thumbnails
    private val data = ConcurrentHashMap<Int, PageData>()
    private var nextId = 1
    private val sessionDir = File(app.cacheDir, "scan_session").apply { mkdirs() }

    /* ----------------------------- Scanner UI state ----------------------------- */

    val liveQuad = MutableStateFlow<Quad?>(null)
    val mode = MutableStateFlow(CaptureMode.DOCUMENT)
    val preset = MutableStateFlow(EnhancePreset.MAGIC)
    val autoEdge = MutableStateFlow(true)
    val autoCapture = MutableStateFlow(true)
    val flash = MutableStateFlow(FlashSetting.AUTO)
    val capturing = MutableStateFlow(false)
    val idFrontPending = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private val captureChannel = Channel<Unit>(Channel.CONFLATED)
    val captureEvents = captureChannel.receiveAsFlow()

    var retakePageId: Int? = null
    private var pendingIdFrontPath: String? = null

    // auto-capture stability tracking
    private var lastQuad: Quad? = null
    private var stableFrames = 0
    private var armed = true

    /* ----------------------------- Scanner controls ----------------------------- */

    fun setMode(newMode: CaptureMode) {
        mode.value = newMode
        if (newMode != CaptureMode.ID_CARD) {
            pendingIdFrontPath?.let { File(it).delete() }
            pendingIdFrontPath = null
            idFrontPending.value = false
        }
        when {
            newMode == CaptureMode.WHITEBOARD -> preset.value = EnhancePreset.WHITEBOARD
            preset.value == EnhancePreset.WHITEBOARD -> preset.value = EnhancePreset.MAGIC
        }
    }

    fun setPreset(p: EnhancePreset) {
        preset.value = p
    }

    fun setAutoEdge(on: Boolean) {
        autoEdge.value = on
        if (!on) {
            liveQuad.value = null
            lastQuad = null
            stableFrames = 0
        }
    }

    fun cycleFlash() {
        flash.value = when (flash.value) {
            FlashSetting.AUTO -> FlashSetting.ON
            FlashSetting.ON -> FlashSetting.OFF
            FlashSetting.OFF -> FlashSetting.AUTO
        }
    }

    /** Camera analysis thread se call hota hai (har ~200ms). */
    fun onFrame(frame: Bitmap) {
        if (!autoEdge.value) return
        val detected = DocumentDetector.detect(frame)
        if (detected == null) {
            liveQuad.value = null
            lastQuad = null
            stableFrames = 0
            armed = true            // document hat gaya -> agla auto-capture allow
            return
        }
        val previous = lastQuad
        val smoothed = if (previous != null) DocumentDetector.blend(previous, detected, 0.5f) else detected
        val movement = if (previous != null) DocumentDetector.maxCornerDistance(previous, smoothed) else 1f
        lastQuad = smoothed
        liveQuad.value = smoothed

        if (!armed && movement > 0.06f) armed = true
        stableFrames = if (movement < 0.012f) stableFrames + 1 else 0
        if (autoCapture.value && armed && !capturing.value && stableFrames >= 6) {
            stableFrames = 0
            captureChannel.trySend(Unit)
        }
    }

    fun requestCaptureStarted() {
        capturing.value = true
    }

    fun onCaptureFailed(message: String) {
        capturing.value = false
        messages.tryEmit(message)
    }

    /** CameraX ne photo file save kar di. */
    fun onPhotoCaptured(file: File) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bmp = ImageProcessor.load(file.absolutePath, 3200)
                file.delete()
                val quad = (if (autoEdge.value) DocumentDetector.detect(bmp) else null) ?: Quad.fullFrame(0.03f)
                addFromBitmap(bmp, quad)
            } catch (t: Throwable) {
                messages.tryEmit(t.message ?: "Capture failed")
            } finally {
                armed = false          // jab tak document hat/hile nahi, dobara auto-capture nahi
                stableFrames = 0
                capturing.value = false
            }
        }
    }

    /* ----------------------------- Import (images / PDF) ----------------------------- */

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            capturing.value = true
            try {
                for (uri in uris) {
                    val type = getApplication<Application>().contentResolver.getType(uri).orEmpty()
                    if (type == "application/pdf" || uri.toString().endsWith(".pdf", ignoreCase = true)) {
                        importPdf(uri)
                    } else {
                        importImage(uri)
                    }
                }
            } catch (t: Throwable) {
                messages.tryEmit(t.message ?: "Import failed")
            } finally {
                capturing.value = false
            }
        }
    }

    private suspend fun importImage(uri: Uri) {
        val temp = copyToSession(uri, "jpg")
        val bmp = ImageProcessor.load(temp.absolutePath, 3200)
        temp.delete()
        val quad = DocumentDetector.detect(bmp) ?: Quad.fullFrame(0.03f)
        val path = saveOriginal(bmp)
        bmp.recycle()
        addPage(path, quad, defaultParams())
    }

    /** PDF ke har page ko image bana ke scan session mein daalta hai (max 100 pages). */
    private suspend fun importPdf(uri: Uri) {
        val temp = copyToSession(uri, "pdf")
        val pfd = ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(pfd)
        try {
            for (i in 0 until min(renderer.pageCount, 100)) {
                val page = renderer.openPage(i)
                val bmp: Bitmap
                try {
                    var w = (page.width * 150f / 72f).roundToInt().coerceAtLeast(1)
                    var h = (page.height * 150f / 72f).roundToInt().coerceAtLeast(1)
                    val longest = max(w, h)
                    if (longest > 2400) {
                        val f = 2400f / longest
                        w = (w * f).roundToInt()
                        h = (h * f).roundToInt()
                    }
                    bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                } finally {
                    page.close()
                }
                val path = saveOriginal(bmp)
                bmp.recycle()
                addPage(path, Quad.fullFrame(0f), EnhanceParams.forFilter(ScanFilter.ORIGINAL))
            }
        } finally {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
            temp.delete()
        }
    }

    /* ----------------------------- Page creation (modes) ----------------------------- */

    private suspend fun addFromBitmap(bmp: Bitmap, quad: Quad) {
        when (mode.value) {
            CaptureMode.ID_CARD -> handleIdCard(bmp, quad)
            CaptureMode.BOOK -> handleBook(bmp, quad)
            else -> {
                val path = saveOriginal(bmp)
                bmp.recycle()
                addPage(path, quad, defaultParams())
            }
        }
    }

    /** Book spread: flat karke beech se do pages (left / right) mein todta hai. */
    private suspend fun handleBook(bmp: Bitmap, quad: Quad) {
        val warped = ImageProcessor.warp(bmp, quad, 3000)
        bmp.recycle()
        val path = saveOriginal(warped)
        warped.recycle()
        val left = Quad(NPoint(0f, 0f), NPoint(0.5f, 0f), NPoint(0.5f, 1f), NPoint(0f, 1f))
        val right = Quad(NPoint(0.5f, 0f), NPoint(1f, 0f), NPoint(1f, 1f), NPoint(0.5f, 1f))
        addPage(path, left, defaultParams())
        addPage(path, right, defaultParams())
    }

    /** ID card: pehle front, phir back -> dono ek A4 page pe upar-neeche. */
    private suspend fun handleIdCard(bmp: Bitmap, quad: Quad) {
        val warped = ImageProcessor.warp(bmp, quad, 1800)
        bmp.recycle()
        val front = pendingIdFrontPath
        if (front == null) {
            pendingIdFrontPath = saveOriginal(warped)
            warped.recycle()
            idFrontPending.value = true
            return
        }
        val frontBmp = ImageProcessor.load(front, 1800)
        val pageW = 1240
        val pageH = 1754
        val composite = Bitmap.createBitmap(pageW, pageH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        fun drawFit(b: Bitmap, centerY: Float) {
            val maxW = pageW * 0.8f
            val maxH = pageH * 0.38f
            val s = min(maxW / b.width, maxH / b.height)
            val w = b.width * s
            val h = b.height * s
            val left = (pageW - w) / 2f
            val top = centerY - h / 2f
            canvas.drawBitmap(b, null, RectF(left, top, left + w, top + h), paint)
        }
        drawFit(frontBmp, pageH * 0.28f)
        drawFit(warped, pageH * 0.72f)
        frontBmp.recycle()
        warped.recycle()
        File(front).delete()
        pendingIdFrontPath = null
        idFrontPending.value = false

        val path = saveOriginal(composite)
        composite.recycle()
        addPage(path, Quad.fullFrame(0f), defaultParams())
    }

    private fun defaultParams(): EnhanceParams = EnhanceParams.forFilter(preset.value.toFilter())

    private suspend fun addPage(path: String, quad: Quad, params: EnhanceParams) {
        val retake = retakePageId
        if (retake != null && data.containsKey(retake)) {
            val old = data[retake]!!
            data[retake] = PageData(path, quad, old.params)
            retakePageId = null
            refreshThumb(retake)
            return
        }
        val id = nextId++
        data[id] = PageData(path, quad, params)
        val label = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
        withContext(Dispatchers.Main) {
            pages.add(ScannedPage(id = id, label = label, filter = tagFor(params.filter)))
        }
        refreshThumb(id)
    }

    private fun saveOriginal(bmp: Bitmap): String {
        val f = File(sessionDir, "orig_${System.nanoTime()}.jpg")
        ImageProcessor.saveJpeg(bmp, f, 92)
        return f.absolutePath
    }

    private fun copyToSession(uri: Uri, ext: String): File {
        val f = File(sessionDir, "imp_${System.nanoTime()}.$ext")
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw java.io.IOException("Cannot open file")
        input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        return f
    }

    /* ----------------------------- Page editing API ----------------------------- */

    fun lastPageId(): Int? = pages.lastOrNull()?.id
    fun quadOf(id: Int): Quad? = data[id]?.quad
    fun settingsFor(id: Int): EnhanceSettings = (data[id]?.params ?: defaultParams()).toSettings()
    fun setRetake(id: Int) {
        retakePageId = id
    }

    fun updateQuad(id: Int, quad: Quad) {
        data[id]?.quad = quad
        refreshThumb(id)
    }

    fun updateParams(id: Int, params: EnhanceParams) {
        val d = data[id] ?: return
        d.params = params
        val idx = pages.indexOfFirst { it.id == id }
        if (idx >= 0) pages[idx] = pages[idx].copy(filter = tagFor(params.filter))
        refreshThumb(id)
    }

    fun applyToAll(params: EnhanceParams) {
        pages.map { it.id }.forEach { updateParams(it, params) }
    }

    fun autoCropAll() {
        viewModelScope.launch(Dispatchers.Default) {
            pages.map { it.id }.forEach { id ->
                detectFor(id)?.let { q ->
                    data[id]?.quad = q
                    refreshThumb(id)
                }
            }
        }
    }

    suspend fun detectFor(id: Int): Quad? = withContext(Dispatchers.Default) {
        val d = data[id] ?: return@withContext null
        val bmp = ImageProcessor.load(d.originalPath, 640)
        val q = DocumentDetector.detect(bmp)
        bmp.recycle()
        q
    }

    suspend fun loadOriginalPreview(id: Int): ImageBitmap? = withContext(Dispatchers.Default) {
        val d = data[id] ?: return@withContext null
        ImageProcessor.load(d.originalPath, 1600).asImageBitmap()
    }

    suspend fun renderPreview(id: Int, params: EnhanceParams, maxSide: Int, enhance: Boolean): ImageBitmap? =
        withContext(Dispatchers.Default) {
            val d = data[id] ?: return@withContext null
            renderBitmap(d, params, maxSide, enhance).asImageBitmap()
        }

    private fun renderBitmap(d: PageData, params: EnhanceParams, maxSide: Int, enhance: Boolean): Bitmap {
        val original = ImageProcessor.load(d.originalPath, max(maxSide * 2, 1200))
        val warped = ImageProcessor.warp(original, d.quad, maxSide)
        original.recycle()
        if (!enhance) return warped
        val result = ImageProcessor.enhance(warped, params)
        warped.recycle()
        return result
    }

    private fun refreshThumb(id: Int) {
        val d = data[id] ?: return
        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val bmp = renderBitmap(d, d.params, 700, true)
                withContext(Dispatchers.Main) { thumbs[id] = bmp.asImageBitmap() }
            }
        }
    }

    /* ----------------------------- Export ----------------------------- */

    fun quickSave() {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        export(
            PdfExportOptions(
                fileName = "Scan_$stamp",
                pageSize = PageSizeOption.A4,
                quality = QualityOption.BALANCED,
                searchableOcr = false,
                pages = pages.filter { it.selected }
            )
        )
    }

    fun export(options: PdfExportOptions) {
        val selected = options.pages
        if (selected.isEmpty()) {
            messages.tryEmit(str(R.string.err_no_pages))
            return
        }
        launchTool(str(R.string.tool_working_scan)) { report ->
            val dpi = options.quality.dpi
            val maxSide = min(3300, (dpi * 11.7f).roundToInt())
            val jpegQuality = when {
                dpi <= 150 -> 0.75f
                dpi <= 300 -> 0.85f
                else -> 0.90f
            }
            val exportPages = selected.mapNotNull { sp ->
                val d = data[sp.id] ?: return@mapNotNull null
                ScanExportPage {
                    var bmp = renderBitmap(d, d.params, maxSide, true)
                    if (sp.rotation % 360f != 0f) {
                        val rotated = ImageProcessor.rotate(bmp, sp.rotation)
                        if (rotated !== bmp) bmp.recycle()
                        bmp = rotated
                    }
                    bmp
                }
            }
            val result = engine.scanPagesToPdf(
                pages = exportPages,
                size = ScanPageSize.valueOf(options.pageSize.name),
                jpegQuality = jpegQuality,
                searchable = options.searchableOcr,
                onProgress = report
            )
            store.commit(
                temp = result.file,
                displayName = OutputStore.fileName(options.fileName, "pdf"),
                tool = ToolType.SCAN,
                pages = result.pageCount,
                mime = result.mimeType
            )
        }
    }

    /* ----------------------------- Reset ----------------------------- */

    fun reset() {
        pages.clear()
        thumbs.clear()
        data.clear()
        nextId = 1
        retakePageId = null
        pendingIdFrontPath = null
        idFrontPending.value = false
        liveQuad.value = null
        lastQuad = null
        stableFrames = 0
        armed = true
        sessionDir.listFiles()?.forEach { it.delete() }
    }

    override fun onCleared() {
        sessionDir.listFiles()?.forEach { it.delete() }
        super.onCleared()
    }
}