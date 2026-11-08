package com.example.pdftoolkit.password

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.reorder.MediaStoreOutputSink
import com.example.pdftoolkit.reorder.PdfOutputSink
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ProtectResult(val uri: Uri, val displayName: String)

data class SetPasswordUiState(
    val fileName: String? = null,
    val pageCount: Int = 0,
    val password: String = "",
    val confirm: String = "",
    val showPassword: Boolean = false,
    val submitted: Boolean = false,          // errors for empty fields only show after a submit attempt
    val isLoading: Boolean = false,
    val isProtecting: Boolean = false,
    val progress: Float = 0f,
    val progressLabel: String = "",
    val error: String? = null,
    val result: ProtectResult? = null
) {
    val hasFile get() = fileName != null

    /** null = OK. */
    val passwordIssue: String?
        get() = when {
            password.isEmpty() -> if (submitted) "Enter a password" else null
            password.length < MIN_LENGTH -> "Use at least $MIN_LENGTH characters"
            password.length > MAX_LENGTH -> "Use at most $MAX_LENGTH characters"
            !password.all { it.code in 0x20..0x7E } ->
                "Use only letters, numbers and symbols from an English keyboard"
            else -> null
        }

    val confirmIssue: String?
        get() = when {
            confirm.isEmpty() -> if (submitted) "Confirm your password" else null
            confirm != password -> "Passwords don't match"
            else -> null
        }

    val canSubmit: Boolean
        get() = password.isNotEmpty() && confirm.isNotEmpty() &&
                passwordIssue == null && confirmIssue == null && !isProtecting

    /** 0..4 */
    val strength: Int
        get() {
            if (password.isEmpty()) return 0
            var s = 0
            if (password.length >= 8) s++
            if (password.length >= 12) s++
            if (password.any { it.isLowerCase() } && password.any { it.isUpperCase() }) s++
            if (password.any { it.isDigit() } && password.any { !it.isLetterOrDigit() }) s++
            return s
        }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 64
    }
}

class SetPasswordViewModel(app: Application) : AndroidViewModel(app) {

    private val processor = SetPasswordProcessor(app)
    private val sink: PdfOutputSink = MediaStoreOutputSink(app)

    private val _state = MutableStateFlow(SetPasswordUiState())
    val state: StateFlow<SetPasswordUiState> = _state.asStateFlow()

    private var workingFile: File? = null
    private var job: Job? = null

    fun onPdfPicked(uri: Uri) {
        job?.cancel()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, result = null) }
            try {
                val name = withContext(Dispatchers.IO) { queryDisplayName(uri) }
                val file = processor.copyToCache(uri)
                val pages = processor.inspect(file)
                if (pages == 0) throw IllegalStateException("This PDF has no pages")

                val old = workingFile
                workingFile = file
                withContext(Dispatchers.IO) { old?.delete() }

                _state.update {
                    it.copy(
                        fileName = name,
                        pageCount = pages,
                        password = "",
                        confirm = "",
                        submitted = false,
                        isLoading = false
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = e.message ?: "Could not open this PDF") }
            }
        }
    }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onConfirmChange(value: String) = _state.update { it.copy(confirm = value) }
    fun toggleShowPassword() = _state.update { it.copy(showPassword = !it.showPassword) }

    fun protect() {
        _state.update { it.copy(submitted = true) }
        val s = _state.value
        val source = workingFile ?: return
        if (!s.canSubmit) return

        val password = s.password
        job = viewModelScope.launch {
            val out = File(getApplication<Application>().cacheDir, "password_output.pdf")
            _state.update {
                it.copy(isProtecting = true, progress = 0.1f, progressLabel = "Preparing…", error = null)
            }
            try {
                processor.protect(source, out, password) { p, label ->
                    _state.update { it.copy(progress = p, progressLabel = label) }
                }
                _state.update { it.copy(progress = 0.95f, progressLabel = "Saving file…") }

                val base = (s.fileName ?: "document").removeSuffix(".pdf").removeSuffix(".PDF")
                val name = "${base}_protected.pdf"
                val uri = sink.save(out, name)

                _state.update {
                    it.copy(
                        isProtecting = false,
                        progress = 1f,
                        password = "",          // don't keep the password around
                        confirm = "",
                        submitted = false,
                        result = ProtectResult(uri, name)
                    )
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(isProtecting = false) }
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(isProtecting = false, error = e.message ?: "Could not protect this PDF")
                }
            } finally {
                out.delete()
            }
        }
    }

    fun cancelProtect() {
        job?.cancel()
    }

    /** Back to the "choose a PDF" state. */
    fun reset() {
        job?.cancel()
        val old = workingFile
        workingFile = null
        old?.delete()
        _state.value = SetPasswordUiState()
    }

    fun clearError() = _state.update { it.copy(error = null) }

    private fun queryDisplayName(uri: Uri): String =
        getApplication<Application>().contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: "document.pdf"

    override fun onCleared() {
        job?.cancel()
        workingFile?.delete()
        super.onCleared()
    }
}