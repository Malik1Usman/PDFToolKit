package com.example.pdftoolkit.toword

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.io.Writer
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"

/** Thrown when the PDF has no selectable text (scanned / image-only PDF). */
class NoTextException : Exception(
    "This PDF has no selectable text. It looks like a scan (pages are images), " +
            "so converting it to an editable Word file needs OCR."
)

data class PreviewItem(val isHeading: Boolean, val text: String)

data class ConversionStats(
    val pages: Int,
    val pagesWithoutText: Int,
    val paragraphs: Int,
    val headings: Int,
    val words: Int,
    val preview: List<PreviewItem>
)

/**
 * PDF → DOCX conversion. No UI code and no extra library:
 * PDFBox reads text with font info, [LayoutAnalyzer] rebuilds paragraphs/headings,
 * [DocxWriter] writes the .docx (a zip of XML files) directly.
 */
class PdfToWordProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("pdf2word_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "pdf2word_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
    }

    suspend fun convert(
        source: File,
        output: File,
        onProgress: (progress: Float, label: String) -> Unit
    ): ConversionStats = withContext(Dispatchers.IO) {
        val scope = this
        var total = 1
        var pageW = 612f
        var pageH = 792f

        val stripper = FormattedStripper { pageNo ->
            scope.ensureActive()
            onProgress(0.8f * pageNo / total, "Reading page $pageNo of $total")
        }

        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            total = doc.numberOfPages
            if (total == 0) throw IllegalStateException("This PDF has no pages")

            val first = doc.getPage(0)
            pageW = first.mediaBox.width
            pageH = first.mediaBox.height
            if (first.rotation % 180 != 0) { val t = pageW; pageW = pageH; pageH = t }

            stripper.writeText(doc, NullWriter)
        }

        val realLines = stripper.lines.filter { l -> l.text.any { it.isLetterOrDigit() } }
        val textChars = realLines.sumOf { l -> l.text.count { it.isLetterOrDigit() } }
        if (textChars < 10) throw NoTextException()
        val pagesWithText = realLines.map { it.page }.toSet().size

        scope.ensureActive()
        onProgress(0.82f, "Analyzing layout…")
        val paragraphs = LayoutAnalyzer.analyze(realLines)

        scope.ensureActive()
        onProgress(0.9f, "Writing Word document…")
        val wTwips = (pageW * 20).toInt().coerceIn(1440, 31680)
        val hTwips = (pageH * 20).toInt().coerceIn(1440, 31680)
        val margin = (min(wTwips, hTwips) * 0.09f).toInt().coerceIn(720, 1440)
        DocxWriter.write(output, paragraphs, wTwips, hTwips, margin)
        onProgress(1f, "Done")

        ConversionStats(
            pages = total,
            pagesWithoutText = total - pagesWithText,
            paragraphs = paragraphs.size,
            headings = paragraphs.count { it.style != ParaStyle.NORMAL },
            words = paragraphs.sumOf { p -> p.text.split(Regex("\\s+")).count { it.isNotBlank() } },
            preview = paragraphs.take(6).map {
                PreviewItem(it.style != ParaStyle.NORMAL, it.text.take(220))
            }
        )
    }
}

// ───────────────────────── 1. Formatted text extraction ─────────────────────────

internal data class Run(val text: String, val bold: Boolean, val italic: Boolean, val size: Float)

internal class Line(val page: Int, val x: Float, val y: Float) {
    val runs = ArrayList<Run>()
    var endX = x
    var text = ""
    var dominantSize = 0f
    var allBold = false

    fun add(text: String, bold: Boolean, italic: Boolean, size: Float) {
        val last = runs.lastOrNull()
        if (last != null && last.bold == bold && last.italic == italic && abs(last.size - size) < 0.5f) {
            runs[runs.size - 1] = last.copy(text = last.text + text)
        } else {
            runs.add(Run(text, bold, italic, size))
        }
    }

    fun appendSpace() {
        val last = runs.lastOrNull() ?: return
        if (!last.text.endsWith(" ")) runs[runs.size - 1] = last.copy(text = last.text + " ")
    }

    /** Trims the line and caches text / dominant size. Returns false if the line is empty. */
    fun finish(): Boolean {
        while (runs.isNotEmpty()) {
            val f = runs.first()
            val t = f.text.trimStart()
            if (t.isEmpty()) runs.removeAt(0) else { runs[0] = f.copy(text = t); break }
        }
        while (runs.isNotEmpty()) {
            val l = runs.last()
            val t = l.text.trimEnd()
            if (t.isEmpty()) runs.removeAt(runs.size - 1) else { runs[runs.size - 1] = l.copy(text = t); break }
        }
        if (runs.isEmpty()) return false
        text = runs.joinToString("") { it.text }
        dominantSize = runs.maxByOrNull { it.text.length }!!.size
        allBold = runs.all { it.bold }
        return true
    }
}

/** Collects lines with font style (bold / italic / size) and position instead of plain text. */
private class FormattedStripper(private val onPageDone: (Int) -> Unit) : PDFTextStripper() {

    val lines = ArrayList<Line>()
    private var current: Line? = null
    private var pageNo = 0

    init {
        sortByPosition = true
    }

    override fun startPage(page: PDPage) {
        pageNo++
        super.startPage(page)
    }

    override fun endPage(page: PDPage) {
        endLine()
        onPageDone(pageNo)
        super.endPage(page)
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (text.isEmpty() || textPositions.isEmpty()) return
        val first = textPositions.first()
        val last = textPositions.last()

        val font = first.font
        val name = font?.name.orEmpty().lowercase()
        val desc = font?.fontDescriptor
        val bold = name.contains("bold") || name.contains("black") || name.contains("heavy") ||
                (desc?.fontWeight ?: 0f) >= 700f || desc?.isForceBold == true
        val italic = name.contains("italic") || name.contains("oblique") || desc?.isItalic == true

        val line = current ?: Line(pageNo, first.xDirAdj, first.yDirAdj).also { current = it }
        line.add(text, bold, italic, first.fontSizeInPt)
        line.endX = max(line.endX, last.xDirAdj + last.widthDirAdj)
    }

    override fun writeWordSeparator() {
        current?.appendSpace()
    }

    override fun writeLineSeparator() = endLine()
    override fun writeParagraphStart() = endLine()
    override fun writeParagraphEnd() = endLine()

    private fun endLine() {
        val line = current ?: return
        current = null
        if (line.finish()) lines.add(line)
    }
}

private object NullWriter : Writer() {
    override fun write(cbuf: CharArray, off: Int, len: Int) {}
    override fun flush() {}
    override fun close() {}
}

// ───────────────────────── 2. Paragraph / heading detection ─────────────────────────

internal enum class ParaStyle(val id: String?) { NORMAL(null), H1("Heading1"), H2("Heading2"), H3("Heading3") }

internal data class DocRun(val text: String, val bold: Boolean, val italic: Boolean, val halfPts: Int)

internal data class DocPara(val style: ParaStyle, val runs: List<DocRun>, val rtl: Boolean) {
    val text: String get() = runs.joinToString("") { it.text }
}

internal object LayoutAnalyzer {

    private val LIST_ITEM = Regex("^(?:[•●▪◦‣·–—*-]|\\d{1,3}[.)]|\\(?[a-z]\\)|[ivxIVX]{1,4}\\))\\s+")

    fun analyze(lines: List<Line>): List<DocPara> {
        // Body font size = most common size (by character count)
        val hist = HashMap<Int, Int>()
        var chars = 0
        var boldChars = 0
        for (line in lines) for (r in line.runs) {
            val n = r.text.count { !it.isWhitespace() }
            hist.merge((r.size * 2).roundToInt(), n) { a, b -> a + b }
            chars += n
            if (r.bold) boldChars += n
        }
        val body = (hist.maxByOrNull { it.value }?.key ?: 22) / 2f
        val boldHeadings = chars > 0 && boldChars.toFloat() / chars < 0.4f

        // Text bounds per page, to spot short last lines of paragraphs
        val bounds = HashMap<Int, Pair<Float, Float>>()
        for (l in lines) {
            val b = bounds[l.page]
            bounds[l.page] = if (b == null) l.x to l.endX else min(b.first, l.x) to max(b.second, l.endX)
        }

        val out = ArrayList<DocPara>()
        var runs = ArrayList<Run>()
        var prev: Line? = null

        fun flush() {
            if (runs.isEmpty()) return
            val text = runs.joinToString("") { it.text }
            val dom = runs.maxByOrNull { it.text.length }!!.size
            val style = when {
                text.length > 160 -> ParaStyle.NORMAL
                dom >= body * 1.6f -> ParaStyle.H1
                dom >= body * 1.3f -> ParaStyle.H2
                dom >= body * 1.12f -> ParaStyle.H3
                boldHeadings && runs.all { it.bold } && text.length <= 90 && !endsSentence(text) -> ParaStyle.H3
                else -> ParaStyle.NORMAL
            }
            val docRuns = ArrayList<DocRun>()
            for (r in runs) {
                val dr = if (style == ParaStyle.NORMAL) {
                    val hp = if (abs(r.size - body) >= 1f) (r.size * 2).roundToInt().coerceIn(2, 400) else 0
                    DocRun(r.text, r.bold, r.italic, hp)
                } else DocRun(r.text, false, r.italic, 0)
                val last = docRuns.lastOrNull()
                if (last != null && last.bold == dr.bold && last.italic == dr.italic && last.halfPts == dr.halfPts) {
                    docRuns[docRuns.size - 1] = last.copy(text = last.text + dr.text)
                } else docRuns.add(dr)
            }
            out.add(DocPara(style, docRuns, isRtl(text)))
            runs = ArrayList()
        }

        for (line in lines) {
            val p = prev
            if (p != null && runs.isNotEmpty() && shouldMerge(p, line, body, bounds)) {
                joinInto(runs, line)
            } else {
                flush()
                runs = ArrayList(line.runs)
            }
            prev = line
        }
        flush()
        return out
    }

    private fun sizeClass(size: Float, body: Float) = when {
        size < body * 1.12f -> 0
        size < body * 1.3f -> 1
        size < body * 1.6f -> 2
        else -> 3
    }

    private fun shouldMerge(
        prev: Line,
        line: Line,
        body: Float,
        bounds: Map<Int, Pair<Float, Float>>
    ): Boolean {
        if (LIST_ITEM.containsMatchIn(line.text)) return false
        val cls = sizeClass(line.dominantSize, body)
        if (sizeClass(prev.dominantSize, body) != cls) return false
        if (prev.allBold != line.allBold) return false

        val prevEnds = endsSentence(prev.text)
        if (prev.page != line.page) {
            // paragraph running over a page break
            return cls == 0 && !prevEnds && line.text.firstOrNull()?.isUpperCase() != true
        }

        val gap = line.y - prev.y
        if (gap <= 0f || gap > 1.7f * line.dominantSize) return false

        val b = bounds[prev.page]
        if (b != null && prevEnds && (prev.endX - b.first) < 0.8f * (b.second - b.first)) return false
        return true
    }

    private fun joinInto(runs: MutableList<Run>, line: Line) {
        val last = runs.last()
        val firstChar = line.runs.first().text.firstOrNull()
        val hyphenated = last.text.length >= 2 && last.text.endsWith("-") &&
                last.text[last.text.length - 2].isLetter() && firstChar?.isLowerCase() == true
        runs[runs.size - 1] = last.copy(text = if (hyphenated) last.text.dropLast(1) else last.text + " ")
        for (r in line.runs) {
            val l = runs.last()
            if (l.bold == r.bold && l.italic == r.italic && abs(l.size - r.size) < 0.5f) {
                runs[runs.size - 1] = l.copy(text = l.text + r.text)
            } else runs.add(r)
        }
    }

    private fun endsSentence(text: String): Boolean {
        val t = text.trimEnd('"', '\'', '”', '’', ')', ']', '»', ' ')
        return t.lastOrNull()?.let { it in ".!?:;…؟۔" } ?: false
    }

    private fun isRtl(text: String): Boolean {
        var rtl = 0
        var ltr = 0
        for (ch in text) {
            when (Character.getDirectionality(ch)) {
                Character.DIRECTIONALITY_RIGHT_TO_LEFT,
                Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> rtl++
                Character.DIRECTIONALITY_LEFT_TO_RIGHT -> ltr++
                else -> {}
            }
        }
        return rtl > ltr
    }
}

// ───────────────────────── 3. DOCX writer (zip of XML parts) ─────────────────────────

internal object DocxWriter {

    private const val W_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val XML_HEAD = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    fun write(output: File, paragraphs: List<DocPara>, pageW: Int, pageH: Int, margin: Int) {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            zip.entry("[Content_Types].xml", CONTENT_TYPES)
            zip.entry("_rels/.rels", ROOT_RELS)
            zip.entry("word/_rels/document.xml.rels", DOC_RELS)
            zip.entry("word/styles.xml", STYLES)

            zip.putNextEntry(ZipEntry("word/document.xml"))
            val w = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))   // not closed: zip stays open
            w.write(XML_HEAD)
            w.write("<w:document xmlns:w=\"$W_NS\"><w:body>")
            for (p in paragraphs) writeParagraph(w, p)
            w.write(
                "<w:sectPr><w:pgSz w:w=\"$pageW\" w:h=\"$pageH\"/>" +
                        "<w:pgMar w:top=\"$margin\" w:right=\"$margin\" w:bottom=\"$margin\" w:left=\"$margin\" " +
                        "w:header=\"720\" w:footer=\"720\" w:gutter=\"0\"/></w:sectPr>"
            )
            w.write("</w:body></w:document>")
            w.flush()
            zip.closeEntry()
        }
    }

    private fun writeParagraph(w: Writer, p: DocPara) {
        w.write("<w:p>")
        if (p.style.id != null || p.rtl) {
            w.write("<w:pPr>")
            p.style.id?.let { w.write("<w:pStyle w:val=\"$it\"/>") }
            if (p.rtl) w.write("<w:bidi/>")
            w.write("</w:pPr>")
        }
        for (r in p.runs) {
            w.write("<w:r>")
            val rpr = StringBuilder()
            if (r.bold) { rpr.append("<w:b/>"); if (p.rtl) rpr.append("<w:bCs/>") }
            if (r.italic) { rpr.append("<w:i/>"); if (p.rtl) rpr.append("<w:iCs/>") }
            if (r.halfPts > 0) { rpr.append("<w:sz w:val=\"${r.halfPts}\"/><w:szCs w:val=\"${r.halfPts}\"/>") }
            if (p.rtl) rpr.append("<w:rtl/>")
            if (rpr.isNotEmpty()) w.write("<w:rPr>$rpr</w:rPr>")
            w.write("<w:t xml:space=\"preserve\">")
            w.write(xmlEscape(r.text))
            w.write("</w:t></w:r>")
        }
        w.write("</w:p>")
    }

    private fun ZipOutputStream.entry(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    /** Escapes XML and drops characters that are illegal in XML 1.0 (they would corrupt the file). */
    private fun xmlEscape(s: String): String {
        val sb = StringBuilder(s.length + 16)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp == '&'.code -> sb.append("&amp;")
                cp == '<'.code -> sb.append("&lt;")
                cp == '>'.code -> sb.append("&gt;")
                cp == 0x9 || cp == 0xA || cp == 0xD ||
                        cp in 0x20..0xD7FF || cp in 0xE000..0xFFFD || cp in 0x10000..0x10FFFF -> sb.appendCodePoint(cp)
                else -> {}
            }
        }
        return sb.toString()
    }

    private const val CONTENT_TYPES = XML_HEAD +
            "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
            "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
            "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
            "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
            "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
            "</Types>"

    private const val ROOT_RELS = XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
            "</Relationships>"

    private const val DOC_RELS = XML_HEAD +
            "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
            "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
            "</Relationships>"

    private val STYLES = XML_HEAD +
            "<w:styles xmlns:w=\"$W_NS\">" +
            "<w:docDefaults><w:rPrDefault><w:rPr>" +
            "<w:rFonts w:ascii=\"Calibri\" w:hAnsi=\"Calibri\" w:eastAsia=\"Calibri\" w:cs=\"Arial\"/>" +
            "<w:sz w:val=\"22\"/><w:szCs w:val=\"22\"/><w:lang w:val=\"en-US\" w:eastAsia=\"en-US\" w:bidi=\"ar-SA\"/>" +
            "</w:rPr></w:rPrDefault><w:pPrDefault><w:pPr><w:spacing w:after=\"120\" w:line=\"276\" w:lineRule=\"auto\"/></w:pPr></w:pPrDefault></w:docDefaults>" +
            "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/><w:qFormat/></w:style>" +
            heading(1, 36, 360) + heading(2, 30, 280) + heading(3, 26, 200) +
            "</w:styles>"

    private fun heading(level: Int, halfPts: Int, before: Int) =
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading$level\"><w:name w:val=\"heading $level\"/>" +
                "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:qFormat/>" +
                "<w:pPr><w:keepNext/><w:spacing w:before=\"$before\" w:after=\"120\"/><w:outlineLvl w:val=\"${level - 1}\"/></w:pPr>" +
                "<w:rPr><w:b/><w:bCs/><w:sz w:val=\"$halfPts\"/><w:szCs w:val=\"$halfPts\"/></w:rPr></w:style>"
}