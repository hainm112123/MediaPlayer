package com.example.mediaplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import coil.compose.AsyncImage

@Composable
fun AudioPlayerContent(
    player: Player,
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    playbackSpeed: Float,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenQueue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .padding(horizontal = 24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back")
            }
            Text(
                text = mediaMetadata.albumTitle?.toString() ?: "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            // Balances the leading icon so the label stays centered.
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.weight(1f))

        val artworkShape = RoundedCornerShape(20.dp)
        val artworkModifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .shadow(8.dp, artworkShape)
            .clip(artworkShape)
        val artworkData = mediaMetadata.artworkData
        if (artworkData != null) {
            AsyncImage(
                model = artworkData,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = artworkModifier
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = artworkModifier.background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                )
            ) {
                Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.size(96.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = mediaMetadata.title?.toString() ?: "Unknown",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.basicMarquee()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = mediaMetadata.artist?.toString() ?: "Unknown Artist",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.weight(1f))

        SeekBarRow(
            positionMs = positionMs,
            durationMs = durationMs,
            onSeek = { player.seekTo(it) }
        )

        TransportControls(
            isPlaying = isPlaying,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            onPlayPause = { if (player.isPlaying) player.pause() else player.play() },
            onPrevious = { player.seekToPreviousMediaItem() },
            onNext = { player.seekToNextMediaItem() },
            onToggleRepeat = onToggleRepeat,
            onToggleShuffle = onToggleShuffle
        )

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(onClick = onOpenSpeed) {
                Text(formatSpeed(playbackSpeed))
            }
            IconButton(onClick = onOpenQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
