package app.pauta

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pauta.model.SetupStep
import app.pauta.recorder.RecordingController
import app.pauta.recorder.RecordingResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado global da interface. */
sealed interface UiState {
    data object NeedsModelSetup : UiState
    data class ModelSetup(val step: SetupStep) : UiState
    data class Recorder(val recorder: RecorderState) : UiState
}

/** Estado da tela de gravação. */
sealed interface RecorderState {
    data object Idle : RecorderState
    data object LoadingModel : RecorderState
    data class Recording(
        val elapsedSeconds: Int,
        val level: Float,
        val segments: List<String>,
    ) : RecorderState

    data object Stopping : RecorderState
    data class Finished(val result: RecordingResult) : RecorderState
    data class Error(val message: String) : RecorderState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val pautaApp get() = getApplication<PautaApplication>()
    private val modelManager get() = pautaApp.modelManager

    private var controller: RecordingController? = null
    private var tickerJob: Job? = null
    private var collectorJob: Job? = null
    private var startedAt = 0L
    private var liveSegments: List<String> = emptyList()

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private fun initialState(): UiState =
        if (modelManager.isReady()) {
            UiState.Recorder(RecorderState.Idle)
        } else {
            UiState.NeedsModelSetup
        }

    // ---- Instalação dos modelos ----

    fun startDownload() {
        viewModelScope.launch {
            modelManager.downloadAndInstall().collect { step ->
                _uiState.value = when (step) {
                    is SetupStep.Done -> UiState.Recorder(RecorderState.Idle)
                    else -> UiState.ModelSetup(step)
                }
            }
        }
    }

    // ---- Gravação ----

    fun startRecording() {
        val current = _uiState.value
        if (current is UiState.Recorder) {
            val recorder = current.recorder
            if (recorder is RecorderState.Recording ||
                recorder is RecorderState.LoadingModel ||
                recorder is RecorderState.Stopping
            ) {
                return
            }
        }
        viewModelScope.launch {
            _uiState.value = UiState.Recorder(RecorderState.LoadingModel)
            try {
                pautaApp.asrEngine.load()

                val ctrl = controller
                    ?: RecordingController(pautaApp.asrEngine, pautaApp.recordingsDir)
                        .also { controller = it }

                liveSegments = emptyList()
                ctrl.start(viewModelScope)
                observeController(ctrl)

                startedAt = SystemClock.elapsedRealtime()
                startTicker()
                _uiState.value = UiState.Recorder(RecorderState.Recording(0, 0f, emptyList()))
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _uiState.value = UiState.Recorder(RecorderState.Error(t.message ?: "Falha ao iniciar a gravação"))
            }
        }
    }

    private fun observeController(ctrl: RecordingController) {
        collectorJob?.cancel()
        collectorJob = viewModelScope.launch {
            launch {
                ctrl.segmentText.collect { text ->
                    liveSegments = liveSegments + text
                    updateRecording { it.copy(segments = liveSegments) }
                }
            }
            launch {
                ctrl.level.collect { level ->
                    updateRecording { it.copy(level = level) }
                }
            }
            launch {
                ctrl.failure.collect { t ->
                    stopTicker()
                    _uiState.value = UiState.Recorder(
                        RecorderState.Error(t.message ?: "Falha durante a gravação"),
                    )
                }
            }
        }
    }

    fun stopRecording() {
        val ctrl = controller ?: return
        viewModelScope.launch {
            _uiState.value = UiState.Recorder(RecorderState.Stopping)
            val result = try {
                ctrl.stop()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                _uiState.value = UiState.Recorder(RecorderState.Error(t.message ?: "Falha ao finalizar"))
                return@launch
            }
            stopTicker()
            _uiState.value = UiState.Recorder(RecorderState.Finished(result))
        }
    }

    fun resetToIdle() {
        val current = _uiState.value
        val isRecording = (current as? UiState.Recorder)?.recorder is RecorderState.Recording
        if (!isRecording) {
            _uiState.value = UiState.Recorder(RecorderState.Idle)
        }
    }

    // ---- Auxiliares ----

    private fun updateRecording(transform: (RecorderState.Recording) -> RecorderState.Recording) {
        val state = (_uiState.value as? UiState.Recorder)?.recorder ?: return
        val recording = state as? RecorderState.Recording ?: return
        _uiState.value = UiState.Recorder(transform(recording))
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (true) {
                delay(1_000)
                val elapsed = ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()
                updateRecording { it.copy(elapsedSeconds = elapsed) }
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }
}
