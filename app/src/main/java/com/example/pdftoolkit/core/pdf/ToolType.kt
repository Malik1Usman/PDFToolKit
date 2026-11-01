package com.example.pdftoolkit.core.pdf

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

enum class ToolType {
    MERGE, SPLIT, COMPRESS, ROTATE, REORDER, DELETE_PAGES,
    IMAGES_TO_PDF, PDF_TO_IMAGES, EXTRACT_TEXT, TO_WORD, PROTECT, WATERMARK, SCAN
}

/** App ke andar save hui har output file ka record (Files + History screen isi se chalengi). */
data class OutputRecord(
    val id: String,
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val pages: Int,
    val mime: String,
    val tool: ToolType,
    val createdAt: Long
) {
    val file: File get() = File(path)
}

/**
 * Outputs ko filesDir/outputs/ mein rakhta hai aur ek chhota JSON index maintain karta hai.
 * Koi storage permission nahi chahiye. Share / open FileProvider se hota hai.
 */
class OutputStore(context: Context) {

    private val dir = File(context.filesDir, "outputs").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "outputs_index.json")

    @Synchronized
    fun commit(temp: File, displayName: String, tool: ToolType, pages: Int, mime: String): OutputRecord {
        val safe = sanitize(displayName)
        val base = safe.substringBeforeLast('.', safe)
        val ext = safe.substringAfterLast('.', "")
        var target = File(dir, safe)
        var n = 1
        while (target.exists()) {
            target = File(dir, if (ext.isEmpty()) "$base ($n)" else "$base ($n).$ext")
            n++
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        val record = OutputRecord(
            id = UUID.randomUUID().toString(),
            name = target.name,
            path = target.absolutePath,
            sizeBytes = target.length(),
            pages = pages,
            mime = mime,
            tool = tool,
            createdAt = System.currentTimeMillis()
        )
        val all = load()
        all.add(0, record)
        save(all)
        return record
    }

    @Synchronized
    fun records(): List<OutputRecord> = load().filter { it.file.exists() }

    @Synchronized
    fun delete(id: String) {
        val all = load()
        all.firstOrNull { it.id == id }?.file?.delete()
        save(all.filterNot { it.id == id })
    }

    @Synchronized
    fun clearAll() {
        load().forEach { it.file.delete() }
        save(emptyList())
    }

    private fun load(): MutableList<OutputRecord> {
        if (!indexFile.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(indexFile.readText())
            MutableList(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                OutputRecord(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    path = o.getString("path"),
                    sizeBytes = o.getLong("size"),
                    pages = o.getInt("pages"),
                    mime = o.getString("mime"),
                    tool = runCatching { ToolType.valueOf(o.getString("tool")) }.getOrDefault(ToolType.MERGE),
                    createdAt = o.getLong("createdAt")
                )
            }
        } catch (_: Throwable) {
            mutableListOf()
        }
    }

    private fun save(list: List<OutputRecord>) {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(
                JSONObject()
                    .put("id", r.id).put("name", r.name).put("path", r.path)
                    .put("size", r.sizeBytes).put("pages", r.pages).put("mime", r.mime)
                    .put("tool", r.tool.name).put("createdAt", r.createdAt)
            )
        }
        indexFile.writeText(arr.toString())
    }

    companion object {
        private val illegal = Regex("[\\\\/:*?\"<>|]")

        fun sanitize(name: String): String =
            name.trim().replace(illegal, "_").ifEmpty { "output" }

        /** "Report", "Report.pdf" -> "Report.pdf" */
        fun fileName(raw: String, ext: String): String {
            val base = raw.trim().removeSuffix(".pdf").removeSuffix(".zip").removeSuffix(".docx")
                .removeSuffix(".txt").ifEmpty { "output" }
            return "$base.$ext"
        }
    }
}