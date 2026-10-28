package com.mehuljain.airshare.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mehuljain.airshare.R
import com.mehuljain.airshare.printer.PrinterConfig

@Composable
fun SettingsDialog(
    config: PrinterConfig,
    addresses: List<String>,
    onDismiss: () -> Unit,
    onSave: (PrinterConfig) -> Unit,
) {
    var name by remember { mutableStateOf(config.name) }
    var portText by remember { mutableStateOf(config.port.toString()) }
    var location by remember { mutableStateOf(config.location) }
    var compat by remember { mutableStateOf(config.compatibilityMode) }
    var savePhotosAsImages by remember { mutableStateOf(config.savePhotosAsImages) }
    var extractPdfPhotos by remember { mutableStateOf(config.extractPdfPhotos) }
    var autoAccept by remember { mutableStateOf(config.autoAccept) }
    var autoOffMinutes by remember { mutableStateOf(config.autoOffMinutes) }
    var webSharePin by remember { mutableStateOf(config.webSharePin) }
    var webShareEnabled by remember { mutableStateOf(config.webShareEnabled) }

    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "?"
    }

    val port = portText.toIntOrNull()
    val portValid = port != null && port in 1024..65535
    val nameValid = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ui_settings_title)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
            ) {
                SectionLabel(R.string.ui_section_printer, first = true)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.ui_field_name)) },
                    singleLine = true,
                    isError = !nameValid,
                    supportingText = if (!nameValid) {
                        { Text(stringResource(R.string.ui_field_name_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text(stringResource(R.string.ui_field_location)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                SectionLabel(R.string.ui_section_network)
                OutlinedTextField(
                    value = portText,
                    onValueChange = { v -> portText = v.filter { it.isDigit() }.take(5) },
                    label = { Text(stringResource(R.string.ui_field_port)) },
                    singleLine = true,
                    isError = !portValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            stringResource(
                                if (portValid) R.string.ui_field_port_support else R.string.ui_field_port_error,
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.ui_current_addresses),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                if (addresses.isEmpty()) {
                    Text(
                        stringResource(R.string.ui_not_connected),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                addresses.forEach { addr ->
                    Text(
                        addr,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                SectionLabel(R.string.ui_section_mode)
                Column(Modifier.selectableGroup()) {
                    ModeOption(
                        selected = compat,
                        title = R.string.ui_mode_compat_title,
                        description = R.string.ui_mode_compat_desc,
                        onClick = { compat = true },
                    )
                    ModeOption(
                        selected = !compat,
                        title = R.string.ui_mode_pdf_title,
                        description = R.string.ui_mode_pdf_desc,
                        onClick = { compat = false },
                    )
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.ui_save_photos_as_image_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.ui_save_photos_as_image_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = savePhotosAsImages, onCheckedChange = { savePhotosAsImages = it })
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.ui_extract_pdf_photos_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.ui_extract_pdf_photos_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = extractPdfPhotos, onCheckedChange = { extractPdfPhotos = it })
                }

                Row(
                    Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(stringResource(R.string.ui_auto_accept_title), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            stringResource(R.string.ui_auto_accept_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoAccept, onCheckedChange = { autoAccept = it })
                }

                SectionLabel(R.string.ui_section_timer)
                val timerOptions = listOf(
                    5 to "5 minutes",
                    10 to "10 minutes (Default)",
                    15 to "15 minutes",
                    30 to "30 minutes",
                    0 to "Never (keep active)",
                )
                Column(Modifier.selectableGroup()) {
                    timerOptions.forEach { (mins, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = (autoOffMinutes == mins),
                                onClick = { autoOffMinutes = mins },
                                role = Role.RadioButton,
                            ).padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = (autoOffMinutes == mins),
                                onClick = null,
                            )
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 12.dp),
                            )
                        }
                    }
                }

                SectionLabel(R.string.ui_section_about)
                Text(
                    text = stringResource(R.string.ui_field_uuid),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = config.uuid,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Web & Video Transfer",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
                )
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("Enable Web & Video Transfer", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Allows transferring 4K videos, media, and files via Safari / Web Portal or iOS Shortcut.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = webShareEnabled, onCheckedChange = { webShareEnabled = it })
                }

                if (webShareEnabled) {
                    OutlinedTextField(
                        value = webSharePin,
                        onValueChange = { v -> webSharePin = v.filter { it.isDigit() }.take(6) },
                        label = { Text("6-Digit Pairing PIN") },
                        singleLine = true,
                        trailingIcon = {
                            TextButton(onClick = { webSharePin = com.mehuljain.airshare.service.PrinterPrefs.generatePin() }) {
                                Text("New PIN")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.ui_app_version, version),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = portValid && nameValid,
                onClick = {
                    onSave(
                        config.copy(
                            name = name.trim(),
                            port = port!!,
                            location = location.trim(),
                            compatibilityMode = compat,
                            savePhotosAsImages = savePhotosAsImages,
                            extractPdfPhotos = extractPdfPhotos,
                            autoAccept = autoAccept,
                            autoOffMinutes = autoOffMinutes,
                            webSharePin = webSharePin.ifBlank { config.webSharePin },
                            webShareEnabled = webShareEnabled,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.ui_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
        },
    )
}

@Composable
private fun ModeOption(
    selected: Boolean,
    title: Int,
    description: Int,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 12.dp, top = 2.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionLabel(title: Int, first: Boolean = false) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = if (first) 0.dp else 20.dp, bottom = 8.dp),
    )
}
