package com.example.pdftoolkit.core.scan

/** Normalized point: x, y in 0..1 (image ke width / height ke hisab se). */
data class NPoint(val x: Float, val y: Float)

/** Document ke 4 corners (top-left, top-right, bottom-right, bottom-left). */
data class Quad(val tl: NPoint, val tr: NPoint, val br: NPoint, val bl: NPoint) {
    fun points(): List<NPoint> = listOf(tl, tr, br, bl)

    fun with(index: Int, p: NPoint): Quad = when (index) {
        0 -> copy(tl = p)
        1 -> copy(tr = p)
        2 -> copy(br = p)
        else -> copy(bl = p)
    }

    companion object {
        fun fullFrame(inset: Float = 0.03f) = Quad(
            NPoint(inset, inset), NPoint(1f - inset, inset),
            NPoint(1f - inset, 1f - inset), NPoint(inset, 1f - inset)
        )
    }
}

enum class ScanFilter { MAGIC, BW, GRAYSCALE, WHITEBOARD, ORIGINAL }

enum class FlashSetting { AUTO, ON, OFF }

/** Ek page ki enhancement settings (FineTune screen ke sliders se map hoti hain). */
data class EnhanceParams(
    val filter: ScanFilter = ScanFilter.MAGIC,
    val contrast: Float = 35f,          // 0..100
    val brightness: Float = 12f,        // 0..100  (white level)
    val shadowRemoval: Float = 75f,     // 0..100
    val colorBalance: Float = 0f,       // -50..50
    val deepInk: Boolean = true
) {
    companion object {
        fun forFilter(f: ScanFilter): EnhanceParams = when (f) {
            ScanFilter.MAGIC -> EnhanceParams(f, 35f, 12f, 75f, 0f, true)
            ScanFilter.BW -> EnhanceParams(f, 60f, 20f, 85f, 0f, true)
            ScanFilter.GRAYSCALE -> EnhanceParams(f, 25f, 8f, 60f, 0f, false)
            ScanFilter.WHITEBOARD -> EnhanceParams(f, 40f, 30f, 90f, 0f, true)
            ScanFilter.ORIGINAL -> EnhanceParams(f, 0f, 0f, 0f, 0f, false)
        }
    }
}