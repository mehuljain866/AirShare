package com.mehuljain.airshare.ui

import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

            if (status.state == ServiceState.RUNNING && config.webShareEnabled) {
                var showQrDialog by remember { mutableStateOf(false) }
                val formattedPin = if (config.webSharePin.length == 6) {
                    "${config.webSharePin.substring(0, 3)} ${config.webSharePin.substring(3)}"
                } else config.webSharePin

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            "Video & Web PIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            formattedPin,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    OutlinedButton(
                        onClick = { showQrDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text("Send Videos / QR", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (showQrDialog) {
                    val hostAddress = status.addresses.firstOrNull() ?: "192.168.1.X"
                    val shareUrl = "http://$hostAddress:${config.port}?pin=${config.webSharePin}"
                    QrPairingDialog(
                        url = shareUrl,
                        pin = formattedPin,
                        serverAddress = "$hostAddress:${config.port}",
                        onDismiss = { showQrDialog = false },
                    )
                }
            }

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

@Composable
fun QrPairingDialog(
    url: String,
    pin: String,
    serverAddress: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val qrBitmap = remember(url) {
        runCatching { QrCodeGenerator.generateBitmap(url, 400) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transfer Videos & Files", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Scan with your iPhone/iPad camera or open in Safari to transfer videos directly into your Gallery.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (qrBitmap != null) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR Code",
                        modifier = Modifier
                            .size(180.dp)
                            .padding(8.dp),
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                ) {
                    Text("6-Digit Pairing PIN", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        pin,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "http://$serverAddress",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AirShare URL", url))
                android.widget.Toast.makeText(context, "URL & PIN copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                onDismiss()
            }) {
                Text("Copy Link")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}
