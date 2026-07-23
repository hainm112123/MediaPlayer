package com.example.mediaplayer.ui

import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerContent(
    player: Player,
    title: String,
    isPlaying: Boolean,
    isBuffering: Boolean,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val registerInteraction: () -> Unit = { interactionTick++ }

    LaunchedEffect(controlsVisible, isPlaying, interactionTick) {
        if (controlsVisible && isPlaying) {
            delay(3500)
            controlsVisible = false
        }
    }

    var isFullscreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isFullscreen) {
        val window = activity?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.window?.let { window ->
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
                window.attributes = window.attributes.apply {
                    screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                }
            }
        }
    }

    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }

    // Transient gesture feedback: -1 = seek back, +1 = seek forward, 0 = none.
    var seekFeedback by remember { mutableIntStateOf(0) }
    var seekFeedbackTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(seekFeedbackTick) {
        if (seekFeedback != 0) {
            delay(700)
            seekFeedback = 0
        }
    }
    var volumeFraction by remember { mutableStateOf<Float?>(null) }
    var brightnessFraction by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).also { playerView ->
                    playerView.useController = false
                    playerView.keepScreenOn = true
                    playerView.player = player
                }
            },
            update = { playerView -> playerView.player = player },
            onRelease = { playerView -> playerView.player = null },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2f) {
                                player.seekBack()
                                seekFeedback = -1
                            } else {
                                player.seekForward()
                                seekFeedback = 1
                            }
                            seekFeedbackTick++
                        }
                    )
                }
                .pointerInput(Unit) {
                    var isRightSide = false
                    var startVolume = 0f
                    var startBrightness = 0f
                    var accumulated = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isRightSide = offset.x >= size.width / 2f
                            accumulated = 0f
                            if (isRightSide) {
                                startVolume = audioManager
                                    .getStreamVolume(AudioManager.STREAM_MUSIC)
                                    .toFloat()
                            } else {
                                val current = activity?.window?.attributes?.screenBrightness ?: -1f
                                startBrightness = if (current in 0f..1f) current else 0.5f
                            }
                        },
                        onDragEnd = {
                            volumeFraction = null
                            brightnessFraction = null
                        },
                        onDragCancel = {
                            volumeFraction = null
                            brightnessFraction = null
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        accumulated -= dragAmount
                        val fractionDelta = accumulated / size.height
                        if (isRightSide) {
                            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val target = (startVolume + fractionDelta * max)
                                .coerceIn(0f, max.toFloat())
                            audioManager.setStreamVolume(
                                AudioManager.STREAM_MUSIC, target.toInt(), 0
                            )
                            volumeFraction = target / max
                        } else {
                            val target = (startBrightness + fractionDelta).coerceIn(0.01f, 1f)
                            activity?.window?.let { window ->
                                window.attributes = window.attributes.apply {
                                    screenBrightness = target
                                }
                            }
                            brightnessFraction = target
                        }
                    }
                }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            CompositionLocalProvider(LocalContentColor provides Color.White) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            registerInteraction()
                            onOpenSpeed()
                        }) {
                            Icon(Icons.Default.Speed, contentDescription = "Playback speed")
                        }
                        IconButton(onClick = {
                            registerInteraction()
                            onOpenQueue()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue")
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        IconButton(
                            onClick = {
                                registerInteraction()
                                player.seekToPreviousMediaItem()
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(42.dp)
                            )
                        }
                        Spacer(Modifier.width(40.dp))
                        if (isBuffering) {
                            Spacer(Modifier.size(72.dp))
                        } else {
                            FilledIconButton(
                                onClick = {
                                    registerInteraction()
                                    if (player.isPlaying) player.pause() else player.play()
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = Color.White.copy(alpha = 0.25f),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(40.dp))
                        IconButton(
                            onClick = {
                                registerInteraction()
                                player.seekToNextMediaItem()
                            },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        SeekBarRow(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            onSeek = { player.seekTo(it) },
                            onInteraction = registerInteraction
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RepeatButton(repeatMode) {
                                registerInteraction()
                                onToggleRepeat()
                            }
                            ShuffleButton(shuffleEnabled) {
                                registerInteraction()
                                onToggleShuffle()
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = {
                                registerInteraction()
                                isFullscreen = !isFullscreen
                            }) {
                                Icon(
                                    if (isFullscreen) {
                                        Icons.Default.FullscreenExit
                                    } else {
                                        Icons.Default.Fullscreen
                                    },
                                    contentDescription = "Fullscreen"
                                )
                            }
                        }
                    }
                }
            }
        }

        if (seekFeedback != 0) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(if (seekFeedback < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 48.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Icon(
                        if (seekFeedback < 0) Icons.Default.FastRewind else Icons.Default.FastForward,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("10s")
                }
            }
        }

        volumeFraction?.let { fraction ->
            GestureValueIndicator(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                fraction = fraction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            )
        }
        brightnessFraction?.let { fraction ->
            GestureValueIndicator(
                icon = Icons.Default.BrightnessMedium,
                fraction = fraction,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 32.dp)
            )
        }

        if (isBuffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
