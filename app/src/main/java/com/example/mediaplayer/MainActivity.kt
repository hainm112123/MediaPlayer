package com.example.mediaplayer

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.mediaplayer.ui.*
import com.example.mediaplayer.ui.theme.MediaPlayerTheme
import com.example.mediaplayer.viewmodel.MediaViewModel
import androidx.media3.common.MediaMetadata

class MainActivity : ComponentActivity() {
    private val viewModel: MediaViewModel by viewModels()
    private var isInPipMode = mutableStateOf(false)
    private var currentRoute: String? = null
    private var intentTrigger = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        if (intent?.hasExtra("NAVIGATE_TO") == true) {
            intentTrigger.intValue++
        }

        setContent {
            MediaPlayerTheme {
                MainScreen(
                    viewModel = viewModel,
                    isInPipMode = isInPipMode.value,
                    intentTrigger = intentTrigger.intValue,
                    onRouteChanged = { currentRoute = it }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("NAVIGATE_TO")) {
            intentTrigger.intValue++
        }
    }

    override fun onUserLeaveHint() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val player = viewModel.player.value
            if (player != null && player.isPlaying && 
                player.mediaMetadata.mediaType == MediaMetadata.MEDIA_TYPE_VIDEO &&
                currentRoute == "player" &&
                viewModel.isPipEnabled.value) {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(rational())
                    .build()
                enterPictureInPictureMode(params)
            }
        }
    }

    private fun rational(): Rational {
        return Rational(16, 9)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }
}

@Composable
fun MainScreen(
    viewModel: MediaViewModel,
    isInPipMode: Boolean,
    intentTrigger: Int,
    onRouteChanged: (String?) -> Unit
) {
    val navController = rememberNavController()
    var hasPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isBackgroundPlayEnabled by viewModel.isBackgroundPlayEnabled.collectAsState()
    val player by viewModel.player.collectAsState()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (!isBackgroundPlayEnabled) {
                    player?.pause()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO, Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        val allGranted = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allGranted) {
            hasPermission = true
        } else {
            launcher.launch(permissionsToRequest)
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    LaunchedEffect(currentDestination, intentTrigger) {
        onRouteChanged(currentDestination?.route)
        
        val intent = (context as? MainActivity)?.intent
        if (intent?.getStringExtra("NAVIGATE_TO") == "player" && currentDestination != null) {
            navController.navigate("player") {
                launchSingleTop = true
            }
            intent.removeExtra("NAVIGATE_TO")
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            val showBottomBar = currentDestination?.route in listOf("home", "list", "settings")
            if (hasPermission && showBottomBar && !isInPipMode) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentDestination?.route == "home",
                        onClick = {
                            navController.navigate("home") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") }
                    )
                    NavigationBarItem(
                        selected = currentDestination?.route == "list",
                        onClick = {
                            navController.navigate("list") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.List, contentDescription = "Library") },
                        label = { Text("Library") }
                    )
                    NavigationBarItem(
                        selected = currentDestination?.route == "settings",
                        onClick = {
                            navController.navigate("settings") {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (hasPermission) {
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.fillMaxSize()
            ) {
                composable("home") {
                    Box(Modifier.padding(innerPadding)) {
                        HomeScreen(
                            viewModel = viewModel,
                            onMediaClick = { mediaFile, playlist ->
                                viewModel.playMedia(mediaFile, playlist)
                                navController.navigate("player")
                            }
                        )
                    }
                }
                composable("list") {
                    Box(Modifier.padding(innerPadding)) {
                        MediaListScreen(
                            viewModel = viewModel,
                            onMediaClick = { mediaFile, playlist ->
                                viewModel.playMedia(mediaFile, playlist)
                                navController.navigate("player")
                            },
                            onAlbumClick = { albumId ->
                                navController.navigate("album_detail/$albumId")
                            }
                        )
                    }
                }
                composable("settings") {
                    Box(Modifier.padding(innerPadding)) {
                        SettingsScreen(viewModel = viewModel)
                    }
                }
                composable("player") {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        isInPipMode = isInPipMode
                    )
                }
                composable(
                    route = "album_detail/{albumId}",
                    arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val albumId = backStackEntry.arguments?.getLong("albumId") ?: 0L
                    Box(Modifier.padding(innerPadding)) {
                        AlbumDetailScreen(
                            viewModel = viewModel,
                            albumId = albumId,
                            onBack = { navController.popBackStack() },
                            onMediaClick = { mediaFile, playlist ->
                                viewModel.playMedia(mediaFile, playlist)
                                navController.navigate("player")
                            }
                        )
                    }
                }
            }
        }
    }
}
