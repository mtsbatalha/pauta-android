package app.pauta.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pauta.MainViewModel
import app.pauta.UiState
import app.pauta.model.SetupStep
import app.pauta.ui.theme.PautaTheme

@Composable
fun PautaApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    PautaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (val s = state) {
                UiState.NeedsModelSetup ->
                    ModelSetupScreen(step = SetupStep.Idle, onDownload = viewModel::startDownload)

                is UiState.ModelSetup ->
                    ModelSetupScreen(step = s.step, onDownload = viewModel::startDownload)

                is UiState.Recorder ->
                    RecorderScreen(
                        state = s.recorder,
                        onStart = viewModel::startRecording,
                        onStop = viewModel::stopRecording,
                        onNewRecording = viewModel::resetToIdle,
                    )
            }
        }
    }
}
