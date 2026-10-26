package com.mehuljain.airshare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mehuljain.airshare.R
import com.mehuljain.airshare.printer.PrinterConfig
import com.mehuljain.airshare.service.PrinterController
import com.mehuljain.airshare.service.PrinterStatus
import com.mehuljain.airshare.service.ServiceState
import java.util.Locale

@Composable
fun StatusCard(
    status: PrinterStatus,
    config: PrinterConfig,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remainingSeconds by PrinterController.remainingSeconds.collectAsStateWithLifecycle()
    val mode = stringResource(
        if (config.compatibilityMode) R.string.ui_mode_compat_short else R.string.ui_mode_pdf_short,
    )
    val timerStr = if (status.state == ServiceState.RUNNING && remainingSeconds != null && remainingSeconds!! > 0) {
        val m = remainingSeconds!! / 60
        val s = remainingSeconds!! % 60
        String.format(Locale.getDefault(), " · Auto-off in %02d:%02d", m, s)
    } else ""
    val summary = when (status.state) {
        ServiceState.RUNNING -> stringResource(R.string.ui_summary_running, mode) + timerStr
        ServiceState.STARTING -> stringResource(R.string.ui_state_starting)
        else -> stringResource(R.string.ui_summary_stopped, mode)
    }
    val checked = status.state == ServiceState.RUNNING || status.state == ServiceState.STARTING
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    config.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(checked = checked, onCheckedChange = onToggle)
            }
            Text(
                summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (status.state == ServiceState.ERROR) {
                Text(
                    status.error?.let { stringResource(R.string.ui_state_error, it) }
                        ?: stringResource(R.string.ui_state_error_unknown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
