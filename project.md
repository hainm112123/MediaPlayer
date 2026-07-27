# Project Context: MediaPlayer

## High-Level Summary
(Based on document.md)

A modern, high-performance media player for Android built with Jetpack Compose and Android Media3.

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Playback Engine**: Media3 1.10.1 (ExoPlayer, MediaSession)
- **Persistence**: Room (History, Albums), DataStore (Settings)
- **Image Loading**: Coil
- **Architecture**: MVVM with Coroutines + StateFlow

### Key Features
- **Media Discovery**: Scans device for audio and video via MediaStore.
- **Playback Service**: ExoPlayer lives in a `MediaSessionService` for background playback.
- **Video Player**: YouTube-style controls with gestures (double-tap seek, vertical swipe for volume/brightness) and PiP support.
- **Audio Player**: Spotify-style Now Playing layout with artwork and metadata.
- **System Integration**: MediaStyle notifications, lockscreen controls, and Picture-in-Picture.
- **Library Management**: Custom albums, recently played history, and reactive search/sorting.

---

## Project Structure
```
MediaPlayer/
├── document.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   └── libs.versions.toml
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/mediaplayer/
        │   ├── data/
        │   │   ├── Album.kt
        │   │   ├── AppDatabase.kt
        │   │   ├── MediaItem.kt
        │   │   ├── MediaStoreRepository.kt
        │   │   ├── RecentMedia.kt
        │   │   └── SettingsRepository.kt
        │   ├── service/
        │   │   └── PlaybackService.kt
        │   ├── ui/
        │   │   ├── theme/
        │   │   │   ├── Color.kt
        │   │   │   ├── Theme.kt
        │   │   │   └── Type.kt
        │   │   ├── AddToAlbumDialog.kt
        │   │   ├── AlbumDetailScreen.kt
        │   │   ├── AudioPlayer.kt
        │   │   ├── CommonUi.kt
        │   │   ├── HomeScreen.kt
        │   │   ├── MediaListScreen.kt
        │   │   ├── PlayerComponents.kt
        │   │   ├── PlayerScreen.kt
        │   │   ├── SettingsScreen.kt
        │   │   └── VideoPlayer.kt
        │   ├── viewmodel/
        │   │   └── MediaViewModel.kt
        │   ├── MainActivity.kt
        │   └── MediaPlayerApplication.kt
        └── res/
            ├── values/
            │   ├── colors.xml
            │   ├── strings.xml
            │   └── themes.xml
            └── xml/
                ├── backup_rules.xml
                └── data_extraction_rules.xml
```

---

## Key Source Files

### MainActivity.kt
`app/src/main/java/com/example/mediaplayer/MainActivity.kt`
```kotlin
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
```

### MediaPlayerApplication.kt
`app/src/main/java/com/example/mediaplayer/MediaPlayerApplication.kt`
```kotlin
package com.example.mediaplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

class MediaPlayerApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
            }
            .crossfade(true)
            .build()
    }
}
```

### Album.kt
`app/src/main/java/com/example/mediaplayer/data/Album.kt`
```kotlin
package com.example.mediaplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "album_media_cross_ref",
    primaryKeys = ["albumId", "mediaId"]
)
data class AlbumMediaCrossRef(
    val albumId: Long,
    val mediaId: Long
)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY timestamp DESC")
    fun getAllAlbums(): Flow<List<Album>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: Album): Long

    @Delete
    suspend fun deleteAlbum(album: Album)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMediaToAlbum(crossRef: AlbumMediaCrossRef)

    @Delete
    suspend fun removeMediaFromAlbum(crossRef: AlbumMediaCrossRef)

    @Query("SELECT * FROM album_media_cross_ref WHERE albumId = :albumId")
    fun getMediaIdsForAlbum(albumId: Long): Flow<List<AlbumMediaCrossRef>>

    @Query("SELECT name FROM albums WHERE id = :albumId")
    fun getAlbumName(albumId: Long): Flow<String>
}
```

### AppDatabase.kt
`app/src/main/java/com/example/mediaplayer/data/AppDatabase.kt`
```kotlin
package com.example.mediaplayer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [RecentMedia::class, Album::class, AlbumMediaCrossRef::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recentMediaDao(): RecentMediaDao
    abstract fun albumDao(): AlbumDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "media_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
```

### MediaItem.kt
`app/src/main/java/com/example/mediaplayer/data/MediaItem.kt`
```kotlin
package com.example.mediaplayer.data

import android.net.Uri

enum class MediaType {
    AUDIO, VIDEO
}

data class MediaFile(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val year: Int?,
    val duration: Long,
    val size: Long,
    val dateModified: Long,
    val uri: Uri,
    val type: MediaType
)
```

### MediaStoreRepository.kt
`app/src/main/java/com/example/mediaplayer/data/MediaStoreRepository.kt`
```kotlin
package com.example.mediaplayer.data

import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class MediaStoreRepository(private val contentResolver: ContentResolver) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAudioFiles(): Flow<List<MediaFile>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer)
        
        trySend(Unit)

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.onStart { emit(Unit) }
    .mapLatest { getAudioFiles() }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeVideoFiles(): Flow<List<MediaFile>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        
        trySend(Unit)

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.onStart { emit(Unit) }
    .mapLatest { getVideoFiles() }

    suspend fun getAudioFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<MediaFile>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val yearColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val artist = cursor.getString(artistColumn)
                val album = cursor.getString(albumColumn)
                val year = cursor.getInt(yearColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)

                files.add(
                    MediaFile(
                        id, title, artist, album, if (year > 0) year else null, duration, size, date, contentUri, MediaType.AUDIO
                    )
                )
            }
        }
        files
    }

    suspend fun getVideoFiles(): List<MediaFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<MediaFile>()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_MODIFIED
        )

        contentResolver.query(collection, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val title = cursor.getString(titleColumn)
                val duration = cursor.getLong(durationColumn)
                val size = cursor.getLong(sizeColumn)
                val date = cursor.getLong(dateColumn)
                val contentUri = ContentUris.withAppendedId(collection, id)

                files.add(
                    MediaFile(
                        id, title, null, null, null, duration, size, date, contentUri, MediaType.VIDEO
                    )
                )
            }
        }
        files
    }
}
```

### RecentMedia.kt
`app/src/main/java/com/example/mediaplayer/data/RecentMedia.kt`
```kotlin
package com.example.mediaplayer.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "recent_media")
data class RecentMedia(
    @PrimaryKey val mediaId: Long,
    val mediaUri: String,
    val timestamp: Long,
    val mediaType: String
)

@Dao
interface RecentMediaDao {
    @Query("SELECT * FROM recent_media ORDER BY timestamp DESC LIMIT 20")
    fun getRecentMedia(): Flow<List<RecentMedia>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentMedia)

    @Query("DELETE FROM recent_media WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: Long)
}
```

### SettingsRepository.kt
`app/src/main/java/com/example/mediaplayer/data/SettingsRepository.kt`
```kotlin
package com.example.mediaplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val BACKGROUND_PLAY_KEY = booleanPreferencesKey("background_play_enabled")
    private val PIP_ENABLED_KEY = booleanPreferencesKey("pip_enabled")

    val isBackgroundPlayEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[BACKGROUND_PLAY_KEY] ?: true
        }

    val isPipEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[PIP_ENABLED_KEY] ?: true
        }

    suspend fun setBackgroundPlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_PLAY_KEY] = enabled
        }
    }

    suspend fun setPipEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PIP_ENABLED_KEY] = enabled
        }
    }
}
```

### PlaybackService.kt
`app/src/main/java/com/example/mediaplayer/service/PlaybackService.kt`
```kotlin
package com.example.mediaplayer.service

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

    private val callback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val availablePlayerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SET_REPEAT_MODE)
                .add(Player.COMMAND_SET_SHUFFLE_MODE)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailablePlayerCommands(availablePlayerCommands)
                .build()
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            // "Previous" on every surface (notification, lockscreen, headset/Bluetooth)
            // always jumps to the previous media item instead of restarting the
            // current one after 3 seconds of playback.
            .setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)
            // Matches the double-tap gesture in the video player UI.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            putExtra("NAVIGATE_TO", "player")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(callback)
            .setSessionActivity(pendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        stopSelf()
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
```

### AddToAlbumDialog.kt
`app/src/main/java/com/example/mediaplayer/ui/AddToAlbumDialog.kt`
```kotlin
package com.example.mediaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.data.Album
import com.example.mediaplayer.viewmodel.MediaViewModel

@Composable
fun AddToAlbumDialog(
    viewModel: MediaViewModel,
    mediaId: Long,
    onDismiss: () -> Unit
) {
    val albums by viewModel.albums.collectAsState()
    var showCreateAlbumDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Album") },
        text = {
            Column {
                if (albums.isEmpty()) {
                    Text("No albums created yet.", modifier = Modifier.padding(bottom = 8.dp))
                }
                
                Button(
                    onClick = { showCreateAlbumDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Album")
                }

                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(albums) { album ->
                        ListItem(
                            headlineContent = { Text(album.name) },
                            modifier = Modifier.clickable {
                                viewModel.addMediaToAlbum(mediaId, album.id)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    if (showCreateAlbumDialog) {
        var newAlbumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false },
            title = { Text("New Album") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAlbumName.isNotBlank()) {
                            viewModel.createAlbum(newAlbumName)
                            showCreateAlbumDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
```

### AlbumDetailScreen.kt
`app/src/main/java/com/example/mediaplayer/ui/AlbumDetailScreen.kt`
```kotlin
package com.example.mediaplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.data.MediaFile
import com.example.mediaplayer.viewmodel.MediaViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: MediaViewModel,
    albumId: Long,
    onBack: () -> Unit,
    onMediaClick: (MediaFile, List<MediaFile>) -> Unit
) {
    val albumMedia by viewModel.getAlbumWithMedia(albumId).collectAsState(initial = emptyList())
    val albumName by viewModel.getAlbumName(albumId).collectAsState(initial = "Album")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(albumName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (albumMedia.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No media in this album.")
                }
            } else {
                LazyColumn {
                    items(albumMedia) { file ->
                        MediaItemRow(
                            file = file,
                            onClick = { onMediaClick(file, albumMedia) },
                            trailingContent = {
                                IconButton(onClick = { viewModel.removeMediaFromAlbum(file.id, albumId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove from Album")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
```

### AudioPlayer.kt
`app/src/main/java/com/example/mediaplayer/ui/AudioPlayer.kt`
```kotlin
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
import androidx.compose.foundation.layout.systemBarsPadding
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
            .systemBarsPadding()
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
```

### CommonUi.kt
`app/src/main/java/com/example/mediaplayer/ui/CommonUi.kt`
```kotlin
package com.example.mediaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.mediaplayer.data.MediaFile
import com.example.mediaplayer.data.MediaType

@Composable
fun MediaItemRow(
    file: MediaFile,
    onClick: () -> Unit,
    trailingContent: @Composable (() -> Unit)? = null
) {
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
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDuration(file.duration))
                trailingContent?.invoke()
            }
        },
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
```

### HomeScreen.kt
`app/src/main/java/com/example/mediaplayer/ui/HomeScreen.kt`
```kotlin
package com.example.mediaplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.data.MediaFile
import com.example.mediaplayer.viewmodel.MediaViewModel

@Composable
fun HomeScreen(
    viewModel: MediaViewModel,
    onMediaClick: (MediaFile, List<MediaFile>) -> Unit
) {
    val recentMedia by viewModel.recentMediaFiles.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Recently Played",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (recentMedia.isEmpty()) {
            Text("No recently played media")
        } else {
            LazyColumn {
                items(
                    items = recentMedia,
                    key = { it.id }
                ) { file ->
                    MediaItemRow(file = file, onClick = { onMediaClick(file, recentMedia) })
                }
            }
        }
    }
}
```

### MediaListScreen.kt
`app/src/main/java/com/example/mediaplayer/ui/MediaListScreen.kt`
```kotlin
package com.example.mediaplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.data.MediaFile
import com.example.mediaplayer.viewmodel.MediaViewModel
import com.example.mediaplayer.viewmodel.SortOrder
import com.example.mediaplayer.viewmodel.SortType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    viewModel: MediaViewModel,
    onMediaClick: (MediaFile, List<MediaFile>) -> Unit,
    onAlbumClick: (Long) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Video", "Albums")

    val audioFiles by viewModel.filteredAudioFiles.collectAsState()
    val videoFiles by viewModel.filteredVideoFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    val albums by viewModel.albums.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var mediaToAddToAlbum by remember { mutableStateOf<MediaFile?>(null) }
    var showCreateAlbumDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            if (selectedTab == 2) {
                FloatingActionButton(onClick = { showCreateAlbumDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Album")
                }
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                placeholder = { Text("Search...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row {
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            val icon = if (sortOrder == SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward
                            Icon(icon, contentDescription = "Toggle Sort Order")
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort Type")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                SortType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.name) },
                                        onClick = {
                                            viewModel.setSortType(type)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            )

            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0, 1 -> {
                    val currentList = if (selectedTab == 0) audioFiles else videoFiles
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = currentList,
                            key = { it.id }
                        ) { file ->
                            MediaItemRow(
                                file = file,
                                onClick = { onMediaClick(file, currentList) },
                                trailingContent = {
                                    var showOptionsMenu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { showOptionsMenu = true }) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                        }
                                        DropdownMenu(
                                            expanded = showOptionsMenu,
                                            onDismissRequest = { showOptionsMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Add to Album") },
                                                onClick = {
                                                    mediaToAddToAlbum = file
                                                    showOptionsMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                2 -> {
                    if (albums.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            Text("No albums yet. Create one!")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(albums) { album ->
                                ListItem(
                                    headlineContent = { Text(album.name) },
                                    modifier = Modifier.clickable { onAlbumClick(album.id) },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.deleteAlbum(album) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Album")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    mediaToAddToAlbum?.let { file ->
        AddToAlbumDialog(
            viewModel = viewModel,
            mediaId = file.id,
            onDismiss = { mediaToAddToAlbum = null }
        )
    }

    if (showCreateAlbumDialog) {
        var newAlbumName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateAlbumDialog = false },
            title = { Text("New Album") },
            text = {
                OutlinedTextField(
                    value = newAlbumName,
                    onValueChange = { newAlbumName = it },
                    label = { Text("Album Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newAlbumName.isNotBlank()) {
                            viewModel.createAlbum(newAlbumName)
                            showCreateAlbumDialog = false
                        }
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAlbumDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


}
```

### PlayerComponents.kt
`app/src/main/java/com/example/mediaplayer/ui/PlayerComponents.kt`
```kotlin
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
```

### PlayerScreen.kt
`app/src/main/java/com/example/mediaplayer/ui/PlayerScreen.kt`
```kotlin
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
fun PlayerScreen(
    viewModel: MediaViewModel,
    onBack: () -> Unit,
    isInPipMode: Boolean = false
) {
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
            onOpenQueue = { showQueueSheet = true },
            isInPipMode = isInPipMode
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
```

### SettingsScreen.kt
`app/src/main/java/com/example/mediaplayer/ui/SettingsScreen.kt`
```kotlin
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

        val isPipEnabled by viewModel.isPipEnabled.collectAsState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Picture-in-Picture",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Enable PiP when navigating home during video playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = isPipEnabled,
                onCheckedChange = { viewModel.togglePipMode(it) }
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
```

### VideoPlayer.kt
`app/src/main/java/com/example/mediaplayer/ui/VideoPlayer.kt`
```kotlin
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
    onOpenQueue: () -> Unit,
    isInPipMode: Boolean = false
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

    LaunchedEffect(controlsVisible, isPlaying, interactionTick, isInPipMode) {
        if (isInPipMode) {
            controlsVisible = false
            return@LaunchedEffect
        }
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
            window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
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
                .pointerInput(isInPipMode) {
                    if (isInPipMode) return@pointerInput
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
                .pointerInput(isInPipMode) {
                    if (isInPipMode) return@pointerInput
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
            visible = controlsVisible && !isInPipMode,
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
                            .statusBarsPadding()
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
                            .navigationBarsPadding()
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

        if (seekFeedback != 0 && !isInPipMode) {
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

        volumeFraction?.takeIf { !isInPipMode }?.let { fraction ->
            GestureValueIndicator(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                fraction = fraction,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp)
            )
        }
        brightnessFraction?.takeIf { !isInPipMode }?.let { fraction ->
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
```

### Color.kt
`app/src/main/java/com/example/mediaplayer/ui/theme/Color.kt`
```kotlin
package com.example.mediaplayer.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

### Theme.kt
`app/src/main/java/com/example/mediaplayer/ui/theme/Theme.kt`
```kotlin
package com.example.mediaplayer.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun MediaPlayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

### Type.kt
`app/src/main/java/com/example/mediaplayer/ui/theme/Type.kt`
```kotlin
package com.example.mediaplayer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
```

### MediaViewModel.kt
`app/src/main/java/com/example/mediaplayer/viewmodel/MediaViewModel.kt`
```kotlin
package com.example.mediaplayer.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.mediaplayer.data.*
import com.example.mediaplayer.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortType {
    NAME, SIZE, DATE, DURATION, ARTIST, ALBUM
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val mediaRepository = MediaStoreRepository(application.contentResolver)
    private val settingsRepository = SettingsRepository(application)
    private val db = AppDatabase.getDatabase(application)
    private val recentDao = db.recentMediaDao()
    private val albumDao = db.albumDao()

    private val _audioFiles = MutableStateFlow<List<MediaFile>>(emptyList())
    private val _videoFiles = MutableStateFlow<List<MediaFile>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _sortType = MutableStateFlow(SortType.NAME)
    val sortType: StateFlow<SortType> = _sortType

    private val _sortOrder = MutableStateFlow(SortOrder.ASCENDING)
    val sortOrder: StateFlow<SortOrder> = _sortOrder

    val filteredAudioFiles = combine(_audioFiles, _searchQuery, _sortType, _sortOrder) { files, query, sort, order ->
        filterAndSort(files, query, sort, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredVideoFiles = combine(_videoFiles, _searchQuery, _sortType, _sortOrder) { files, query, sort, order ->
        filterAndSort(files, query, sort, order)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentMediaFiles = combine(recentDao.getRecentMedia(), _audioFiles, _videoFiles) { recentList, audios, videos ->
        val allMedia = audios + videos
        recentList.mapNotNull { recent ->
            allMedia.find { it.id == recent.mediaId }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albums = albumDao.getAllAlbums()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isBackgroundPlayEnabled = settingsRepository.isBackgroundPlayEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val isPipEnabled = settingsRepository.isPipEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player

    private val _sleepTimerRemainingMillis = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingMillis: StateFlow<Long?> = _sleepTimerRemainingMillis.asStateFlow()

    private var sleepTimerJob: Job? = null

    private var controllerFuture: ListenableFuture<MediaController>? = null

    init {
        val sessionToken = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            val controller = controllerFuture?.get()
            _player.value = controller
            controller?.let {
                _repeatMode.value = it.repeatMode
                _shuffleModeEnabled.value = it.shuffleModeEnabled
                it.addListener(object : Player.Listener {
                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _repeatMode.value = repeatMode
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _shuffleModeEnabled.value = shuffleModeEnabled
                    }
                })
            }
        }, MoreExecutors.directExecutor())
        
        viewModelScope.launch {
            mediaRepository.observeAudioFiles().collect {
                _audioFiles.value = it
            }
        }
        viewModelScope.launch {
            mediaRepository.observeVideoFiles().collect {
                _videoFiles.value = it
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortType(sortType: SortType) {
        _sortType.value = sortType
    }

    fun toggleSortOrder() {
        _sortOrder.value = if (_sortOrder.value == SortOrder.ASCENDING) {
            SortOrder.DESCENDING
        } else {
            SortOrder.ASCENDING
        }
    }

    fun toggleRepeatMode() {
        val currentPlayer = _player.value ?: return
        val nextMode = when (currentPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        currentPlayer.repeatMode = nextMode
    }

    fun toggleShuffleMode() {
        val currentPlayer = _player.value ?: return
        currentPlayer.shuffleModeEnabled = !currentPlayer.shuffleModeEnabled
    }

    fun toggleBackgroundPlay(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setBackgroundPlayEnabled(enabled)
        }
    }

    fun togglePipMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setPipEnabled(enabled)
        }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return

        val totalMillis = minutes * 60 * 1000L
        _sleepTimerRemainingMillis.value = totalMillis

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMillis
            val interval = 1000L
            while (remaining > 0) {
                delay(interval)
                remaining -= interval
                if (remaining <= 0) {
                    _sleepTimerRemainingMillis.value = null
                    _player.value?.pause()
                    break
                } else {
                    _sleepTimerRemainingMillis.value = remaining
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingMillis.value = null
    }

    fun createAlbum(name: String) {
        viewModelScope.launch {
            albumDao.insertAlbum(Album(name = name))
        }
    }

    fun deleteAlbum(album: Album) {
        viewModelScope.launch {
            albumDao.deleteAlbum(album)
        }
    }

    fun addMediaToAlbum(mediaId: Long, albumId: Long) {
        viewModelScope.launch {
            albumDao.insertMediaToAlbum(AlbumMediaCrossRef(albumId, mediaId))
        }
    }

    fun removeMediaFromAlbum(mediaId: Long, albumId: Long) {
        viewModelScope.launch {
            albumDao.removeMediaFromAlbum(AlbumMediaCrossRef(albumId, mediaId))
        }
    }

    fun getAlbumWithMedia(albumId: Long): Flow<List<MediaFile>> {
        return combine(albumDao.getMediaIdsForAlbum(albumId), _audioFiles, _videoFiles) { crossRefs, audios, videos ->
            val allMedia = audios + videos
            crossRefs.mapNotNull { crossRef ->
                allMedia.find { it.id == crossRef.mediaId }
            }
        }
    }

    fun getAlbumName(albumId: Long): Flow<String> {
        return albumDao.getAlbumName(albumId)
    }

    fun playMedia(mediaFile: MediaFile, playlist: List<MediaFile>) {
        val currentPlayer = _player.value ?: return
        val mediaItems = playlist.map { file ->
            MediaItem.Builder()
                .setUri(file.uri)
                .setMediaId(file.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(file.title)
                        .setArtist(file.artist)
                        .setAlbumTitle(file.album)
                        .setMediaType(
                            if (file.type == MediaType.AUDIO) {
                                MediaMetadata.MEDIA_TYPE_MUSIC
                            } else {
                                MediaMetadata.MEDIA_TYPE_VIDEO
                            }
                        )
                        .build()
                )
                .build()
        }
        val startIndex = playlist.indexOfFirst { it.id == mediaFile.id }
        
        currentPlayer.setMediaItems(mediaItems)
        if (startIndex != -1) {
            currentPlayer.seekTo(startIndex, 0L)
        }
        currentPlayer.prepare()
        currentPlayer.play()

        viewModelScope.launch {
            recentDao.insertRecent(
                RecentMedia(
                    mediaId = mediaFile.id,
                    mediaUri = mediaFile.uri.toString(),
                    timestamp = System.currentTimeMillis(),
                    mediaType = mediaFile.type.name
                )
            )
        }
    }

    private fun filterAndSort(files: List<MediaFile>, query: String, sort: SortType, order: SortOrder): List<MediaFile> {
        val filtered = if (query.isBlank()) files else {
            files.filter { it.title.contains(query, ignoreCase = true) || it.artist?.contains(query, ignoreCase = true) == true }
        }
        
        val sorted = when (sort) {
            SortType.NAME -> filtered.sortedBy { it.title.lowercase() }
            SortType.SIZE -> filtered.sortedBy { it.size }
            SortType.DATE -> filtered.sortedBy { it.dateModified }
            SortType.DURATION -> filtered.sortedBy { it.duration }
            SortType.ARTIST -> filtered.sortedBy { (it.artist ?: "").lowercase() }
            SortType.ALBUM -> filtered.sortedBy { (it.album ?: "").lowercase() }
        }

        return if (order == SortOrder.DESCENDING) sorted.reversed() else sorted
    }

    override fun onCleared() {
        super.onCleared()
        controllerFuture?.let {
            MediaController.releaseFuture(it)
        }
    }
}
```

### AndroidManifest.xml
`app/src/main/AndroidManifest.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" android:maxSdkVersion="32" />
    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />
    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

    <application
        android:name=".MediaPlayerApplication"
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.MediaPlayer">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:launchMode="singleTop"
            android:supportsPictureInPicture="true"
            android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation"
            android:theme="@style/Theme.MediaPlayer"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.PlaybackService"
            android:foregroundServiceType="mediaPlayback"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.media3.session.MediaSessionService" />
                <action android:name="android.media.browse.MediaBrowserService" />
            </intent-filter>
        </service>

        <receiver android:name="androidx.media3.session.MediaButtonReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MEDIA_BUTTON" />
            </intent-filter>
        </receiver>
    </application>

</manifest>
```

### colors.xml
`app/src/main/res/values/colors.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
</resources>
```

### strings.xml
`app/src/main/res/values/strings.xml`
```xml
<resources>
    <string name="app_name">MediaPlayer</string>
</resources>
```

### themes.xml
`app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MediaPlayer" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

### backup_rules.xml
`app/src/main/res/xml/backup_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
</full-backup-content>
```

### data_extraction_rules.xml
`app/src/main/res/xml/data_extraction_rules.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
    </cloud-backup>
</data-extraction-rules>
```

### build.gradle.kts (root)
`build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

### build.gradle.kts (app)
`app/build.gradle.kts`
```kotlin
plugins {
    alias(libs.plugins.android.application)
    kotlin("android")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.mediaplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mediaplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

### settings.gradle.kts
`settings.gradle.kts`
```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MediaPlayer"
include(":app")
```

### libs.versions.toml
`gradle/libs.versions.toml`
```toml
[versions]
agp = "8.13.0"
coreKtx = "1.10.1"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycleRuntimeKtx = "2.6.1"
activityCompose = "1.8.0"
kotlin = "2.1.10"
composeBom = "2024.10.01"
media3 = "1.10.1"
navigationCompose = "2.9.8"
lifecycleViewmodelCompose = "2.9.4"
materialIconsExtended = "1.7.8"
room = "2.7.0-alpha11"
coil = "2.7.0"
datastore = "1.2.1"
ksp = "2.1.10-1.0.30"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx-junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx-espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycleRuntimeKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx-compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
androidx-media3-session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigationCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycleViewmodelCompose" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended", version.ref = "materialIconsExtended" }
androidx-room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx-room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx-room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
coil-compose = { group = "io.coil-kt", name = "coil-compose", version.ref = "coil" }
androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
coil-video = { group = "io.coil-kt", name = "coil-video", version.ref = "coil" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```
