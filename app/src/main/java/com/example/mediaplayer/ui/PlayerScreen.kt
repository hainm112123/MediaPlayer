package com.example.mediaplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import com.example.mediaplayer.viewmodel.MediaViewModel

@Composable
fun PlayerScreen(viewModel: MediaViewModel) {
    val player = viewModel.player
    val repeatMode by viewModel.repeatMode.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    this.player = player
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Surface(
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    val icon = when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    }
                    val tint = if (repeatMode == Player.REPEAT_MODE_OFF) {
                        LocalContentColor.current.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                    Icon(icon, contentDescription = "Repeat", tint = tint)
                }

                IconButton(onClick = { player.seekToPrevious() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }

                IconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.size(64.dp)
                ) {
                    var isPlaying by remember { mutableStateOf(player.isPlaying) }
                    
                    DisposableEffect(player) {
                        val listener = object : Player.Listener {
                            override fun onIsPlayingChanged(playing: Boolean) {
                                isPlaying = playing
                            }
                        }
                        player.addListener(listener)
                        onDispose { player.removeListener(listener) }
                    }

                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        modifier = Modifier.size(48.dp)
                    )
                }

                IconButton(onClick = { player.seekToNext() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
                
                // Shuffle button could be added here for symmetry if needed
                IconButton(onClick = { /* Shuffle logic */ }, enabled = false) {
                     Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = LocalContentColor.current.copy(alpha = 0.38f))
                }
            }
        }
    }
}
