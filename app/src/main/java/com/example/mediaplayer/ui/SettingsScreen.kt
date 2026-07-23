package com.example.mediaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.viewmodel.MediaViewModel

@Composable
fun SettingsScreen(viewModel: MediaViewModel) {
    val isBackgroundPlayEnabled by viewModel.isBackgroundPlayEnabled.collectAsState()
    val sleepTimerRemaining by viewModel.sleepTimerRemainingMillis.collectAsState()

    var showSleepTimerDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Background Playback",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Continue playing audio/video when app is minimized",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isBackgroundPlayEnabled,
                onCheckedChange = { viewModel.toggleBackgroundPlay(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showSleepTimerDialog = true }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (sleepTimerRemaining != null) {
                        "Music will stop in ${formatTimerTime(sleepTimerRemaining!!)}"
                    } else {
                        "Off"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (sleepTimerRemaining != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (sleepTimerRemaining != null) {
                TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                    Text("Cancel")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Sleep Timer",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            viewModel = viewModel,
            currentTimerRemaining = sleepTimerRemaining,
            onDismiss = { showSleepTimerDialog = false }
        )
    }
}

@Composable
fun SleepTimerDialog(
    viewModel: MediaViewModel,
    currentTimerRemaining: Long?,
    onDismiss: () -> Unit
) {
    var customMinutesText by remember { mutableStateOf("") }
    var isCustomSelected by remember { mutableStateOf(false) }

    val presetOptions = listOf(15, 30, 45, 60)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Sleep Timer") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                presetOptions.forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.startSleepTimer(minutes)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp)
                        )
                        Text(
                            text = "$minutes minutes",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (!isCustomSelected) {
                    TextButton(
                        onClick = { isCustomSelected = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Custom duration...")
                    }
                } else {
                    OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { customMinutesText = it.filter { char -> char.isDigit() } },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    )
                    Button(
                        onClick = {
                            val mins = customMinutesText.toIntOrNull() ?: 0
                            if (mins > 0) {
                                viewModel.startSleepTimer(mins)
                                onDismiss()
                            }
                        },
                        enabled = (customMinutesText.toIntOrNull() ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Timer")
                    }
                }
            }
        },
        confirmButton = {
            if (currentTimerRemaining != null) {
                TextButton(
                    onClick = {
                        viewModel.cancelSleepTimer()
                        onDismiss()
                    }
                ) {
                    Text("Turn Off Timer", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun formatTimerTime(millis: Long): String {
    val totalSeconds = (millis + 999) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%02d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
