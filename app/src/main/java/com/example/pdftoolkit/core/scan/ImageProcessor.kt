package com.example.pdftoolkit.core.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import android.graphics.Matrix as GfxMatrix

/**
 * Native (OpenCV ke bina) image pipeline: load (EXIF fix) -> perspective warp -> enhance (filters) -> rotate.
 * Heavy kaam hai: hamesha background thread pe chalao.
 */
object ImageProcessor {

    /* ----------------------------- Load / save ----------------------------- */

    fun load(path: String, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) throw IOException("Cannot decode image")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        var bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Cannot decode image")

        val orientation = try {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Throwable) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (degrees != 0f) {
            val r = rotate(bmp, degrees)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        val longest = max(bmp.width, bmp.height)
        if (longest > maxSide) {
            val f = maxSide / longest.toFloat()
            val s = Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true)
            if (s !== bmp) bmp.recycle()
            bmp = s
        }
        return bmp
    }

    fun saveJpeg(bmp: Bitmap, file: File, quality: Int = 92) {
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    fun rotate(src: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return src
        val m = GfxMatrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /* ----------------------------- Perspective warp ----------------------------- */

    /** Quad ke andar ka hissa seedha (flat) karke return karta hai. */
    fun warp(src: Bitmap, quad: Quad, maxOutSide: Int): Bitmap {
        val p = quad.points().map { PointF(it.x * src.width, it.y * src.height) }
        val tl = p[0]; val tr = p[1]; val br = p[2]; val bl = p[3]
        var outW = max(dist(tl, tr), dist(bl, br))
        var outH = max(dist(tl, bl), dist(tr, br))
        outW = max(outW, 16f)
        outH = max(outH, 16f)
        val s = min(1f, maxOutSide / max(outW, outH))
        outW *= s
        outH *= s
        val w = outW.roundToInt().coerceAtLeast(16)
        val h = outH.roundToInt().coerceAtLeast(16)

        val srcPts = floatArrayOf(tl.x, tl.y, tr.x, tr.y, br.x, br.y, bl.x, bl.y)
        val dstPts = floatArrayOf(0f, 0f, w.toFloat(), 0f, w.toFloat(), h.toFloat(), 0f, h.toFloat())
        val matrix = GfxMatrix()
        matrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, matrix, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun dist(a: PointF, b: PointF) = hypot(a.x - b.x, a.y - b.y)

    /* ----------------------------- Enhance ----------------------------- */

    fun enhance(src: Bitmap, p: EnhanceParams): Bitmap {
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)

        if (p.shadowRemoval > 0f) normalizeIllumination(px, w, h, p.shadowRemoval / 100f)

        when (p.filter) {
            ScanFilter.GRAYSCALE -> toGray(px)
            ScanFilter.BW -> {
                toGray(px)
                adaptiveThreshold(px, w, h, 0.20f - p.contrast / 100f * 0.15f)
            }
            ScanFilter.MAGIC -> saturate(px, 1.15f)
            ScanFilter.WHITEBOARD -> saturate(px, 1.35f)
            ScanFilter.ORIGINAL -> Unit
        }
        if (p.filter != ScanFilter.BW) applyTone(px, p)

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Shadow / uneven light hatana: har 16x16 block ka sabse ujjwal pixel = "kagaz ki safedi",
     * use smooth karke har pixel ko usse normalize karte hain.
     */
    private fun normalizeIllumination(px: IntArray, w: Int, h: Int, strength: Float) {
        val block = 16
        val gw = (w + block - 1) / block
        val gh = (h + block - 1) / block
        val bg = FloatArray(gw * gh)
        for (by in 0 until gh) {
            for (bx in 0 until gw) {
                var m = 0
                val x0 = bx * block
                val x1 = min(w, x0 + block)
                val y0 = by * block
                val y1 = min(h, y0 + block)
                for (y in y0 until y1) {
                    var i = y * w + x0
                    for (x in x0 until x1) {
                        val c = px[i++]
                        val l = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
                        if (l > m) m = l
                    }
                }
                bg[by * gw + bx] = m.toFloat()
            }
        }
        repeat(2) { blur3x3(bg, gw, gh) }

        // x ke liye interpolation tables (ek baar)
        val xi0 = IntArray(w)
        val xi1 = IntArray(w)
        val xt = FloatArray(w)
        for (x in 0 until w) {
            val fx = (x / block.toFloat() - 0.5f).coerceIn(0f, gw - 1f)
            xi0[x] = fx.toInt()
            xi1[x] = min(gw - 1, xi0[x] + 1)
            xt[x] = fx - xi0[x]
        }
        for (y in 0 until h) {
            val fy = (y / block.toFloat() - 0.5f).coerceIn(0f, gh - 1f)
            val y0 = fy.toInt()
            val y1 = min(gh - 1, y0 + 1)
            val ty = fy - y0
            val row = y * w
            for (x in 0 until w) {
                val a = bg[y0 * gw + xi0[x]] + (bg[y0 * gw + xi1[x]] - bg[y0 * gw + xi0[x]]) * xt[x]
                val b = bg[y1 * gw + xi0[x]] + (bg[y1 * gw + xi1[x]] - bg[y1 * gw + xi0[x]]) * xt[x]
                val bgv = a + (b - a) * ty
                val ratio = 1f + (255f / max(bgv, 40f) - 1f) * strength
                val c = px[row + x]
                val r = min(255, (((c shr 16) and 255) * ratio).toInt())
                val g = min(255, (((c shr 8) and 255) * ratio).toInt())
                val bl = min(255, ((c and 255) * ratio).toInt())
                px[row + x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
    }

    private fun blur3x3(a: FloatArray, w: Int, h: Int) {
        val src = a.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                var sum = 0f
                var n = 0
                for (dy in -1..1) {
                    val yy = y + dy
                    if (yy < 0 || yy >= h) continue
                    for (dx in -1..1) {
                        val xx = x + dx
                        if (xx < 0 || xx >= w) continue
                        sum += src[yy * w + xx]
                        n++
                    }
                }
                a[y * w + x] = sum / n
            }
        }
    }

    private fun toGray(px: IntArray) {
        for (i in px.indices) {
            val c = px[i]
            val l = (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000
            px[i] = (0xFF shl 24) or (l shl 16) or (l shl 8) or l
        }
    }

    private fun saturate(px: IntArray, amount: Float) {
        for (i in px.indices) {
            val c = px[i]
            val r = (c shr 16) and 255
            val g = (c shr 8) and 255
            val b = c and 255
            val l = 0.299f * r + 0.587f * g + 0.114f * b
            val nr = (l + (r - l) * amount).roundToInt().coerceIn(0, 255)
            val ng = (l + (g - l) * amount).roundToInt().coerceIn(0, 255)
            val nb = (l + (b - l) * amount).roundToInt().coerceIn(0, 255)
            px[i] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
    }

    /** Bradley local threshold: pixel apne aas-paas ke mean se kaafi gehra ho to kaala, warna safed. */
    private fun adaptiveThreshold(px: IntArray, w: Int, h: Int, t: Float) {
        val gray = IntArray(w * h) { px[it] and 255 }
        val radius = max(8, min(w, h) / 30)
        val mean = boxMean(gray, w, h, radius)
        val k = 1f - t.coerceIn(0.05f, 0.3f)
        for (i in px.indices) {
            val v = if (gray[i] < mean[i] * k) 0 else 255
            px[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    private fun applyTone(px: IntArray, p: EnhanceParams) {
        val cf = 1f + p.contrast / 100f * 0.8f
        val white = 255f / (255f - p.brightness.coerceIn(0f, 100f) * 1.2f).coerceAtLeast(60f)
        val warm = p.colorBalance / 200f
        val lutR = buildLut(cf, white * (1f + warm), p.deepInk)
        val lutG = buildLut(cf, white, p.deepInk)
        val lutB = buildLut(cf, white * (1f - warm), p.deepInk)
        for (i in px.indices) {
            val c = px[i]
            px[i] = (0xFF shl 24) or
                (lutR[(c shr 16) and 255] shl 16) or
                (lutG[(c shr 8) and 255] shl 8) or
                lutB[c and 255]
        }
    }

    private fun buildLut(contrast: Float, scale: Float, deepInk: Boolean): IntArray = IntArray(256) { v ->
        var x = v * scale
        x = (x - 128f) * contrast + 128f
        if (deepInk && x < 110f) x *= (0.6f + 0.4f * (x.coerceAtLeast(0f) / 110f))
        x.roundToInt().coerceIn(0, 255)
    }

    /* ----------------------------- Shared helper ----------------------------- */

    /** Separable box mean (clipped window). Values 0..255 ke liye. */
    fun boxMean(src: IntArray, w: Int, h: Int, r: Int): IntArray {
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        val prefixRow = IntArray(w + 1)
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) prefixRow[x + 1] = prefixRow[x] + src[row + x]
            for (x in 0 until w) {
                val l = max(0, x - r)
                val rr = min(w - 1, x + r)
                tmp[row + x] = (prefixRow[rr + 1] - prefixRow[l]) / (rr - l + 1)
            }
        }
        val prefixCol = IntArray(h + 1)
        for (x in 0 until w) {
            for (y in 0 until h) prefixCol[y + 1] = prefixCol[y] + tmp[y * w + x]
            for (y in 0 until h) {
                val t = max(0, y - r)
                val b = min(h - 1, y + r)
                out[y * w + x] = (prefixCol[b + 1] - prefixCol[t]) / (b - t + 1)
            }
        }
        return out
    }
}