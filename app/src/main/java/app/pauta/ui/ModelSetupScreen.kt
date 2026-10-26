package app.pauta.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pauta.R
import app.pauta.model.SetupStep

/**
 * Tela de preparo: baixa e instala os modelos locais (única etapa online).
 */
@Composable
fun ModelSetupScreen(step: SetupStep, onDownload: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )

        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineMedium,
        )

        Text(
            text = stringResource(R.string.setup_body),
            style = MaterialTheme.typography.bodyMedium,
        )

        when (step) {
            SetupStep.Idle -> {
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.setup_download))
                }
            }

            is SetupStep.Downloading -> {
                if (step.fraction >= 0f) {
                    LinearProgressIndicator(
                        progress = { step.fraction },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = stringResource(
                        R.string.setup_downloading,
                        step.label,
                        if (step.fraction >= 0f) "${(step.fraction * 100).toInt()}%" else "…",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            SetupStep.Extracting -> {
                CircularProgressIndicator()
                Text(
                    text = stringResource(R.string.setup_extracting),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is SetupStep.Error -> {
                Text(
                    text = "${stringResource(R.string.setup_error)}: ${step.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = onDownload,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.setup_retry))
                }
            }

            SetupStep.Done -> Unit // transitório: a tela muda para o gravador
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.setup_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
