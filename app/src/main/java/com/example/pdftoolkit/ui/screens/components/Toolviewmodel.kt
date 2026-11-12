package com.example.pdftoolkit.ui.screens.components

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.pdftoolkit.R
import com.example.pdftoolkit.core.db.HistoryRepository
import com.example.pdftoolkit.core.pdf.PdfEngine
import com.example.pdftoolkit.core.pdf.OutputRecord
import com.example.pdftoolkit.core.pdf.OutputStore
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Har tool ka UI state: idle -> working -> success / failure. */
sealed interface ToolState {
    object Idle : ToolState
    data class Working(val message: String, val progress: Float? = null) : ToolState
    data class Success(val record: OutputRecord) : ToolState
    data class Failure(val message: String) : ToolState
}

/**
 * Sab tool ViewModels ka base: engine chalata hai, progress / cancel / error handle karta hai,
 * aur result ko OutputStore & Room DB mein save karta hai.
 */
abstract class ToolViewModel(app: Application) : AndroidViewModel(app) {

    protected val engine = PdfEngine(app)
    protected val store = OutputStore(app)
    protected val historyRepository = HistoryRepository(app)

    private val _state = MutableStateFlow<ToolState>(ToolState.Idle)
    val state: StateFlow<ToolState> = _state.asStateFlow()

    private var job: Job? = null

    protected fun str(id: Int): String = getApplication<Application>().getString(id)

    protected fun fail(message: String) {
        _state.value = ToolState.Failure(message)
    }

    /** block ke andar engine call karo aur OutputRecord return karo (store.commit se). */
    protected fun launchTool(message: String, block: suspend (report: (Float) -> Unit) -> OutputRecord) {
        if (_state.value is ToolState.Working) return
        job = viewModelScope.launch {
            _state.value = ToolState.Working(message, null)
            try {
                val record = block { p -> _state.value = ToolState.Working(message, p.coerceIn(0f, 1f)) }
                historyRepository.recordSuccess(record)
                _state.value = ToolState.Success(record)
            } catch (e: CancellationException) {
                _state.value = ToolState.Idle
                throw e
            } catch (e: Throwable) {
                _state.value = ToolState.Failure(friendly(e))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = ToolState.Idle
    }

    fun dismissState() {
        _state.value = ToolState.Idle
    }

    private fun friendly(e: Throwable): String = when (e) {
        is InvalidPasswordException -> str(R.string.err_password)
        is OutOfMemoryError -> str(R.string.err_memory)
        else -> getApplication<Application>().getString(R.string.err_generic, e.message ?: e.javaClass.simpleName)
    }
}
