package com.example.pdftoolkit.core.scan

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure-Kotlin document edge detector (OpenCV ke bina).
 * Idea: image ko chhota karo -> blur -> Otsu threshold -> sabse bada "document jaisa" blob dhundo
 * (dono polarity: halka kagaz / gehra kagaz) -> uske 4 extreme points = corners.
 * Agar kuch bharosemand na mile to null (caller full-frame use karta hai).
 */
object DocumentDetector {

    fun detect(src: Bitmap): Quad? {
        val longest = max(src.width, src.height)
        if (longest < 32) return null
        val scale = 320f / longest
        val w = max(24, (src.width * scale).roundToInt())
        val h = max(24, (src.height * scale).roundToInt())
        val small = if (w == src.width && h == src.height) src else Bitmap.createScaledBitmap(src, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== src) small.recycle()

        val gray = IntArray(w * h) { luma(px[it]) }
        val blur = ImageProcessor.boxMean(gray, w, h, 2)
        val threshold = otsu(blur)

        var best: Quad? = null
        var bestScore = 0f
        for (invert in booleanArrayOf(false, true)) {
            val mask = BooleanArray(w * h) { (blur[it] > threshold) != invert }
            val comp = largestComponent(mask, w, h) ?: continue
            val areaFrac = comp.size / (w * h).toFloat()
            if (areaFrac !in 0.12f..0.95f) continue
            // background (table / wall) frame ke kinare se chipka hota hai: use reject karo
            if (borderTouch(comp, w, h) > 0.35f) continue

            val pts = extremePoints(comp, w)
            val quadArea = polygonArea(pts)
            if (quadArea < 1f) continue
            val fill = (comp.size / quadArea).coerceAtMost(1f)
            if (fill < 0.55f) continue
            val score = areaFrac * fill
            if (score > bestScore) {
                bestScore = score
                best = Quad(
                    norm(pts[0], w, h),
                    norm(pts[1], w, h),
                    norm(pts[2], w, h),
                    norm(pts[3], w, h),
                )
            }
        }
        return best
    }

    /** Dono quads ke corners ke beech sabse bada farq (normalized). Stability check ke liye. */
    fun maxCornerDistance(a: Quad, b: Quad): Float {
        var m = 0f
        val pa = a.points()
        val pb = b.points()
        for (i in 0 until 4) {
            m = max(m, max(abs(pa[i].x - pb[i].x), abs(pa[i].y - pb[i].y)))
        }
        return m
    }

    fun blend(old: Quad, new: Quad, alpha: Float): Quad {
        val o = old.points()
        val n = new.points()
        fun mix(i: Int) = NPoint(o[i].x + (n[i].x - o[i].x) * alpha, o[i].y + (n[i].y - o[i].y) * alpha)
        return Quad(mix(0), mix(1), mix(2), mix(3))
    }

    /* ---------------- internals ---------------- */

    private fun luma(c: Int): Int =
        (299 * ((c shr 16) and 255) + 587 * ((c shr 8) and 255) + 114 * (c and 255)) / 1000

    private fun otsu(values: IntArray): Int {
        val hist = IntArray(256)
        for (v in values) hist[v.coerceIn(0, 255)]++
        val total = values.size
        var sum = 0L
        for (i in 0 until 256) sum += i.toLong() * hist[i]
        var sumB = 0L
        var wB = 0
        var maxVar = 0.0
        var threshold = 128
        for (t in 0 until 256) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toLong() * hist[t]
            val mB = sumB.toDouble() / wB
            val mF = (sum - sumB).toDouble() / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > maxVar) {
                maxVar = between
                threshold = t
            }
        }
        return threshold
    }

    /** 4-connected flood fill; sabse bade component ke pixel indexes. */
    private fun largestComponent(mask: BooleanArray, w: Int, h: Int): IntArray? {
        val visited = BooleanArray(w * h)
        val stack = IntArray(w * h)
        val buf = IntArray(w * h)
        var best: IntArray? = null
        for (start in mask.indices) {
            if (!mask[start] || visited[start]) continue
            var sp = 0
            var n = 0
            stack[sp++] = start
            visited[start] = true
            while (sp > 0) {
                val i = stack[--sp]
                buf[n++] = i
                val x = i % w
                val y = i / w
                if (x > 0 && mask[i - 1] && !visited[i - 1]) { visited[i - 1] = true; stack[sp++] = i - 1 }
                if (x < w - 1 && mask[i + 1] && !visited[i + 1]) { visited[i + 1] = true; stack[sp++] = i + 1 }
                if (y > 0 && mask[i - w] && !visited[i - w]) { visited[i - w] = true; stack[sp++] = i - w }
                if (y < h - 1 && mask[i + w] && !visited[i + w]) { visited[i + w] = true; stack[sp++] = i + w }
            }
            if (best == null || n > best.size) best = buf.copyOf(n)
        }
        return best
    }

    private fun borderTouch(comp: IntArray, w: Int, h: Int): Float {
        var count = 0
        for (i in comp) {
            val x = i % w
            val y = i / w
            if (x == 0 || y == 0 || x == w - 1 || y == h - 1) count++
        }
        return count / (2f * (w + h))
    }

    /** TL = min(x+y), BR = max(x+y), TR = max(x-y), BL = min(x-y). Returns [TL, TR, BR, BL] in pixels. */
    private fun extremePoints(comp: IntArray, w: Int): List<Pair<Float, Float>> {
        var tl = comp[0]; var br = comp[0]; var tr = comp[0]; var bl = comp[0]
        var minS = Int.MAX_VALUE; var maxS = Int.MIN_VALUE
        var maxD = Int.MIN_VALUE; var minD = Int.MAX_VALUE
        for (i in comp) {
            val x = i % w
            val y = i / w
            val s = x + y
            val d = x - y
            if (s < minS) { minS = s; tl = i }
            if (s > maxS) { maxS = s; br = i }
            if (d > maxD) { maxD = d; tr = i }
            if (d < minD) { minD = d; bl = i }
        }
        fun p(i: Int) = Pair((i % w).toFloat(), (i / w).toFloat())
        return listOf(p(tl), p(tr), p(br), p(bl))
    }

    private fun polygonArea(p: List<Pair<Float, Float>>): Float {
        var a = 0f
        for (i in p.indices) {
            val j = (i + 1) % p.size
            a += p[i].first * p[j].second - p[j].first * p[i].second
        }
        return abs(a) / 2f
    }

    private fun norm(p: Pair<Float, Float>, w: Int, h: Int) =
        NPoint((p.first / (w - 1f)).coerceIn(0f, 1f), (p.second / (h - 1f)).coerceIn(0f, 1f))
}