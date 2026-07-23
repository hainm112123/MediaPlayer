package com.example.mediaplayer.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.example.mediaplayer.viewmodel.MediaViewModel
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(viewModel: MediaViewModel, onBack: () -> Unit) {
    val player by viewModel.player.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleModeEnabled by viewModel.shuffleModeEnabled.collectAsState()

    if (player == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val activePlayer = player!!

    var isPlaying by remember { mutableStateOf(activePlayer.isPlaying) }
    var playbackState by remember { mutableIntStateOf(activePlayer.playbackState) }
    var mediaMetadata by remember { mutableStateOf(activePlayer.mediaMetadata) }
    var currentIndex by remember { mutableIntStateOf(activePlayer.currentMediaItemIndex) }
    var playbackSpeed by remember { mutableFloatStateOf(activePlayer.playbackParameters.speed) }
    var queueVersion by remember { mutableIntStateOf(0) }

    DisposableEffect(activePlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                mediaMetadata = metadata
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                currentIndex = activePlayer.currentMediaItemIndex
            }

            override fun onPlaybackParametersChanged(parameters: PlaybackParameters) {
                playbackSpeed = parameters.speed
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                currentIndex = activePlayer.currentMediaItemIndex
                queueVersion++
            }
        }
        activePlayer.addListener(listener)
        isPlaying = activePlayer.isPlaying
        playbackState = activePlayer.playbackState
        mediaMetadata = activePlayer.mediaMetadata
        currentIndex = activePlayer.currentMediaItemIndex
        playbackSpeed = activePlayer.playbackParameters.speed
        onDispose { activePlayer.removeListener(listener) }
    }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(activePlayer) {
        while (true) {
            positionMs = activePlayer.currentPosition
            durationMs = activePlayer.duration
                .takeIf { it != C.TIME_UNSET }
                ?.coerceAtLeast(0L)
                ?: 0L
            delay(500)
        }
    }

    var showQueueSheet by rememberSaveable { mutableStateOf(false) }
    var showSpeedSheet by rememberSaveable { mutableStateOf(false) }

    val isVideo = mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO

    if (isVideo) {
        VideoPlayerContent(
            player = activePlayer,
            title = mediaMetadata.title?.toString() ?: "",
            isPlaying = isPlaying,
            isBuffering = playbackState == Player.STATE_BUFFERING,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleModeEnabled,
            positionMs = positionMs,
            durationMs = durationMs,
            onBack = onBack,
            onToggleRepeat = viewModel::toggleRepeatMode,
            onToggleShuffle = viewModel::toggleShuffleMode,
            onOpenSpeed = { showSpeedSheet = true },
            onOpenQueue = { showQueueSheet = true }
        )
    } else {
        AudioPlayerContent(
            player = activePlayer,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleModeEnabled,
            playbackSpeed = playbackSpeed,
            positionMs = positionMs,
            durationMs = durationMs,
            onBack = onBack,
            onToggleRepeat = viewModel::toggleRepeatMode,
            onToggleShuffle = viewModel::toggleShuffleMode,
            onOpenSpeed = { showSpeedSheet = true },
            onOpenQueue = { showQueueSheet = true }
        )
    }

    if (showQueueSheet) {
        QueueSheet(
            player = activePlayer,
            currentIndex = currentIndex,
            queueVersion = queueVersion,
            onDismiss = { showQueueSheet = false }
        )
    }
    if (showSpeedSheet) {
        SpeedSheet(
            currentSpeed = playbackSpeed,
            onSelectSpeed = { speed ->
                activePlayer.setPlaybackSpeed(speed)
                showSpeedSheet = false
            },
            onDismiss = { showSpeedSheet = false }
        )
    }
}
