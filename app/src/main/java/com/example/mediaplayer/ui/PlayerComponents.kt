package com.example.mediaplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SeekBarRow(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onInteraction: () -> Unit = {}
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = durationMs.coerceAtLeast(0L)
    val fraction = when {
        dragFraction != null -> dragFraction!!
        duration > 0L -> (positionMs.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }
    val shownPosition = dragFraction?.let { (it * duration).toLong() } ?: positionMs.coerceAtLeast(0L)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(formatDuration(shownPosition), style = MaterialTheme.typography.labelMedium)
        Slider(
            value = fraction,
            onValueChange = {
                dragFraction = it
                onInteraction()
            },
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * duration).toLong()) }
                dragFraction = null
            },
            enabled = duration > 0L,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(formatDuration(duration), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun RepeatButton(repeatMode: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        val icon = if (repeatMode == Player.REPEAT_MODE_ONE) {
            Icons.Default.RepeatOne
        } else {
            Icons.Default.Repeat
        }
        val tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
            LocalContentColor.current.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.primary
        }
        Icon(icon, contentDescription = "Repeat", tint = tint)
    }
}

@Composable
fun ShuffleButton(shuffleEnabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        val tint = if (shuffleEnabled) {
            MaterialTheme.colorScheme.primary
        } else {
            LocalContentColor.current.copy(alpha = 0.38f)
        }
        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = tint)
    }
}

@Composable
fun TransportControls(
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShuffleButton(shuffleEnabled, onToggleShuffle)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = onPlayPause, modifier = Modifier.size(72.dp)) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play/Pause",
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.weight(1f))
        RepeatButton(repeatMode, onToggleRepeat)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    player: Player,
    currentIndex: Int,
    queueVersion: Int,
    onDismiss: () -> Unit
) {
    val items = remember(queueVersion) {
        (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaMetadata }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Playing queue (${items.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(items) { index, metadata ->
                val isCurrent = index == currentIndex
                ListItem(
                    headlineContent = {
                        Text(
                            text = metadata.title?.toString() ?: "Unknown",
                            fontWeight = if (isCurrent) FontWeight.Bold else null,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                            maxLines = 1
                        )
                    },
                    supportingContent = {
                        metadata.artist?.let { Text(it.toString(), maxLines = 1) }
                    },
                    leadingContent = {
                        if (isCurrent) {
                            Icon(
                                Icons.Default.Equalizer,
                                contentDescription = "Now playing",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelLarge,
                                color = LocalContentColor.current.copy(alpha = 0.6f)
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        player.seekToDefaultPosition(index)
                        player.play()
                        onDismiss()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Playback speed",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            speeds.forEach { speed ->
                val selected = abs(speed - currentSpeed) < 0.01f
                ListItem(
                    headlineContent = {
                        Text(formatSpeed(speed) + if (speed == 1f) " (Normal)" else "")
                    },
                    leadingContent = {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Spacer(Modifier.size(24.dp))
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable { onSelectSpeed(speed) }
                )
            }
        }
    }
}

@Composable
fun GestureValueIndicator(
    icon: ImageVector,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(96.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .width(4.dp)
                        .fillMaxHeight(fraction.coerceIn(0f, 1f))
                        .background(Color.White)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

fun formatSpeed(speed: Float): String {
    return if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
}

internal tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
