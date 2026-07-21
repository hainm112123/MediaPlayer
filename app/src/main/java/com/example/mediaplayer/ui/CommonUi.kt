package com.example.mediaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mediaplayer.data.MediaFile
import com.example.mediaplayer.data.MediaType

@Composable
fun MediaItemRow(file: MediaFile, onClick: () -> Unit) {
    val subtitle = buildString {
        append(file.artist ?: "Unknown Artist")
        file.album?.let { append(" • $it") }
        file.year?.let { append(" • $it") }
        append(" • ${formatSize(file.size)}")
    }

    val fallbackIcon = if (file.type == MediaType.AUDIO) {
        Icons.Default.MusicNote
    } else {
        Icons.Default.VideoFile
    }

    ListItem(
        headlineContent = { Text(file.title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Text(formatDuration(file.duration)) },
        leadingContent = {
            AsyncImage(
                model = file.uri,
                contentDescription = null,
                modifier = Modifier.size(50.dp),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(fallbackIcon),
                error = rememberVectorPainter(fallbackIcon)
            )
        },
        modifier = Modifier.clickable { onClick() }
    )
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(sizeBytes.toDouble()) / Math.log10(1024.0)).toInt()
    return "%.1f %s".format(sizeBytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}
