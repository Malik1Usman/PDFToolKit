package com.example.pdftoolkit.password

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** Thrown when the PDF already needs a password to open. */
class AlreadyProtectedException : Exception(
    "This PDF already needs a password to open. Remove that password first, then protect it again."
)

/** PDF encryption logic. No UI code here. The password is never logged or stored. */
class SetPasswordProcessor(context: Context) {

    private val appContext = context.applicationContext

    init {
        PDFBoxResourceLoader.init(appContext)
    }

    suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        appContext.cacheDir.listFiles { f -> f.name.startsWith("password_source_") }
            ?.forEach { it.delete() }
        val target = File(appContext.cacheDir, "password_source_${System.nanoTime()}.pdf")
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { input.copyTo(it) }
        } ?: throw IOException("Cannot open the selected file")
        target
    }

    /** Returns the page count; throws [AlreadyProtectedException] if an open-password is already set. */
    suspend fun inspect(file: File): Int = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
        } catch (e: InvalidPasswordException) {
            throw AlreadyProtectedException()
        }
    }

    /**
     * Writes an encrypted copy of [source] to [output] (user password = owner password = [password]).
     * Tries AES-256 first, falls back to AES-128 for maximum compatibility.
     * The result is re-opened with the password to make sure the file is valid.
     */
    suspend fun protect(
        source: File,
        output: File,
        password: String,
        onProgress: (Float, String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val scope = this
        var lastError: Exception? = null

        for (keyLength in intArrayOf(256, 128)) {
            try {
                scope.ensureActive()
                onProgress(0.3f, "Encrypting…")
                encrypt(source, output, password, keyLength)

                scope.ensureActive()
                onProgress(0.8f, "Verifying…")
                verify(output, password)
                return@withContext
            } catch (e: CancellationException) {
                output.delete()
                throw e
            } catch (e: Exception) {
                lastError = e
                output.delete()
            }
        }
        throw lastError ?: IOException("Could not protect the PDF")
    }

    private fun encrypt(source: File, output: File, password: String, keyLength: Int) {
        PDDocument.load(source, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission()).apply {
                encryptionKeyLength = keyLength
                isPreferAES = true
            }
            doc.protect(policy)
            doc.save(output)
        }
    }

    private fun verify(file: File, password: String) {
        PDDocument.load(file, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            check(doc.isEncrypted) { "Encryption was not applied" }
            check(doc.numberOfPages > 0) { "Output has no pages" }
        }
    }
}