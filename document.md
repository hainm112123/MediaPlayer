# MediaPlayer Android Project

A modern, high-performance media player for Android built with Jetpack Compose and Android Media3.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3), with `AndroidView` interop for the Media3 `PlayerView` video surface
- **Playback Engine**: Media3 1.10.1 — ExoPlayer, MediaSession, MediaSessionService, MediaController
- **Persistence**:
    - **Database**: Room (playback history, custom albums)
    - **Preferences**: Jetpack DataStore (user settings)
- **Image Loading**: Coil (with `VideoFrameDecoder` for video thumbnails; also renders embedded album art)
- **Navigation**: Jetpack Navigation Compose
- **Architecture**: MVVM (Model-View-ViewModel) with Kotlin Coroutines + `StateFlow`
- **Build System**: Gradle Kotlin DSL with Version Catalogs (`libs.versions.toml`) and KSP

## Architecture Overview

```
UI (Compose screens)
   │  collects StateFlow / sends user actions
   ▼
MediaViewModel ────────────────► MediaController (Media3)
   │                                   │ IPC (binder)
   │                                   ▼
   │                             PlaybackService (MediaSessionService)
   │                                   │
   │                                   ├── ExoPlayer  (actual playback)
   │                                   └── MediaSession (notification, lockscreen,
   │                                        Bluetooth/headset buttons, other apps)
   ▼
Repositories: MediaStoreRepository (device media), Room DAOs (history, albums),
              SettingsRepository (DataStore preferences)
```

Playback does **not** run inside the Activity. The `ExoPlayer` instance lives in
`PlaybackService`, and the UI talks to it through a `MediaController` that is connected
asynchronously in `MediaViewModel`. Because the `MediaController` implements the Media3
`Player` interface, the Compose UI can use it exactly as if it were the player itself,
while every command actually travels to the service. This is what makes background
playback and system integrations work with a single code path.

## Features & How They Work

### 1. Media Discovery (`data/MediaStoreRepository.kt`)

- Scans the device for audio and video with two `ContentResolver.query()` calls against
  `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` and `MediaStore.Video.Media.EXTERNAL_CONTENT_URI`.
- Reads a projection of columns (`_ID`, `TITLE`, `ARTIST`, `ALBUM`, `YEAR`, `DURATION`,
  `SIZE`, `DATE_MODIFIED`) and maps every row to a `MediaFile` model, building a playable
  `content://` URI with `ContentUris.withAppendedId()`.
- Queries run on `Dispatchers.IO` inside `suspend` functions so the UI thread is never blocked.
- **Immediate Loading**: The `MediaViewModel` calls `loadMedia()` in its `init` block, ensuring that media is indexed and available (e.g., for the "Recently Played" list) as soon as the app starts.

### 2. Permissions Handling (`MainActivity.kt`)

- On Android 13+ (`TIRAMISU`) requests the granular `READ_MEDIA_AUDIO` and
  `READ_MEDIA_VIDEO` permissions; on older versions it falls back to `READ_EXTERNAL_STORAGE`.
- Uses the Compose Activity Result API (`rememberLauncherForActivityResult` with
  `RequestMultiplePermissions`). The navigation host and bottom bar are only shown once
  permission is granted.

### 3. Playback with ExoPlayer (`service/PlaybackService.kt`, `viewmodel/MediaViewModel.kt`)

- `PlaybackService` extends `MediaSessionService`. In `onCreate()` it builds one `ExoPlayer`
  and wraps it in a `MediaSession`, which is handed to any controller via `onGetSession()`.
- The player is configured with:
    - `setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)` — a "previous" command **always**
      jumps to the previous playlist item.
    - `setSeekBackIncrementMs(10_000)` / `setSeekForwardIncrementMs(10_000)` — standard increments for UI seeking.
- `MediaViewModel` creates a `SessionToken` and connects a `MediaController` asynchronously.

### 4. Playlist Navigation (Previous / Next)

- Skipping is a playlist operation within Media3. In-app buttons and system surfaces use `seekToPreviousMediaItem()` / `seekToNextMediaItem()` to move strictly between playlist entries.

### 5. Repeat Modes & Shuffle (`MediaViewModel.kt`, `ui/PlayerComponents.kt`)

- Cycles `Player.REPEAT_MODE` and toggles `shuffleModeEnabled`.
- ViewModel registeres a `Player.Listener` to sync UI state reactively with the actual player state.

### 6. Player Screen — Two Layouts (`ui/PlayerScreen.kt`)

- COORDINATOR: Registers a `Player.Listener` to mirror player state.
- Layout choice: `MEDIA_TYPE_VIDEO` → `VideoPlayerContent`, otherwise → `AudioPlayerContent`.
- Hosts shared bottom sheets for **playing queue** and **playback speed**.

### 7. Video Player — YouTube Style + Gestures (`ui/VideoPlayer.kt`)

- **Video surface**: Media3 `PlayerView` via `AndroidView`.
- **Gesture layer**:
    - *Single tap* toggles controls.
    - *Double-tap* left/right to seek ±10s.
    - *Vertical swipe* on right for volume, left for brightness. level indicators are shown during drag.
- **Auto-hide**: Controls disappear after 3.5s while playing (restarted by user interaction).
- **PiP Integration**: When in Picture-in-Picture mode, all control overlays, gesture inputs, and status indicators are automatically hidden and disabled.

### 8. Audio Player — Spotify-Style Now Playing (`ui/AudioPlayer.kt`)

- A vertical layout on a dark gradient.
- **Artwork**: Large rounded card using `ContentScale.Crop`. The image is strictly clipped to its `RoundedCornerShape(20.dp)` boundary to ensure a clean visual fit without overflow.
- **Metadata**: Bold title with `basicMarquee()` scroll and artist name.

### 9. Fullscreen Mode (`ui/VideoPlayer.kt`)

- Toggle in bottom-right of video controls.
- **Immersive Mode**: Uses `WindowInsetsControllerCompat` to hide system bars with `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
- **API 28 Support**: Uses `FLAG_LAYOUT_NO_LIMITS` window flag to ensure the video expands to the physical edges of the screen on older API levels.
- **Edge-to-Edge**: The root `NavHost` padding is removed during player navigation, allowing child screens to occupy the full display area.

### 10. Playback Speed (`ui/PlayerComponents.kt` — `SpeedSheet`)

- `ModalBottomSheet` for 0.25x to 2x speed. selecting calls `player.setPlaybackSpeed()`.

### 11. Playing Queue (`ui/PlayerComponents.kt` — `QueueSheet`)

- `ModalBottomSheet` showing the player's actual playlist. Current item is highlighted with an equalizer icon.

### 12. Picture-in-Picture (PiP) (`MainActivity.kt`, `VideoPlayer.kt`)

- **Manifest**: `MainActivity` is declared with `android:supportsPictureInPicture="true"` and handles `configChanges` to prevent recreation.
- **Automatic Entry**: On API 26+, `onUserLeaveHint()` triggers PiP mode if a video is playing and the user is on the player screen.
- **Conditional Trigger**: The `currentRoute` is tracked to ensure PiP only activates when leaving from the player screen, not from the library or settings.
- **User Preference**: A toggle in Settings allows users to disable automatic PiP entry.
- **UI adaptation**: The `isInPipMode` state is passed down to hide all controls and navigation bars.

### 13. Notification & Lockscreen Controls (`service/PlaybackService.kt`)

- **MediaStyle Notification**: Automatically posted by Media3.
- **Tap Action**: `MediaSession.Builder.setSessionActivity()` is used with a `PendingIntent` pointing to `MainActivity`.
- **Navigation Extra**: The intent includes a `NAVIGATE_TO="player"` extra. `MainActivity` checks this in `onNewIntent` or `onResume` and navigates the `navController` directly to the player screen.

### 14. Recently Played History (`data/RecentMedia.kt`, `data/AppDatabase.kt`)

- Persistent Room history updated on every `playMedia()` call. The Home tab resolves these IDs reactively.

### 15. Custom Albums (`data/Album.kt`, `ui/AlbumDetailScreen.kt`)

- Room entities supporting many-to-many relations between albums and media files.

### 16. Search & Sorting (`viewmodel/MediaViewModel.kt`)

- Reactive filtering and sorting of the media list based on user input.

### 17. Thumbnails (`MediaPlayerApplication.kt`)

- Coil configured with `VideoFrameDecoder` for video thumbnails.

### 18. Settings (`data/SettingsRepository.kt`, `ui/SettingsScreen.kt`)

- Persisted preferences in DataStore:
    - **Background Playback**: Whether playback continues when the app is minimized.
    - **Picture-in-Picture**: Whether PiP is triggered automatically.

### 19. Navigation & Lifecycle (`MainActivity.kt`, `PlaybackService.kt`)

- **Single Activity**: `NavHost` with `singleTop` launch mode to handle notification taps correctly.
- **Lifecycle Observation**: The app monitors `ON_STOP` to pause if background play is disabled.
- **App Closure**: `onTaskRemoved()` in `PlaybackService` calls `stopSelf()`, ensuring that swiping the app away from Recents stops playback and clears the notification immediately.

## What Changed in the Recent Updates

- **Notification Navigation**: Tapping the notification now opens the app directly to the current player screen instead of the home screen.
- **PiP Fixes**: PiP now only triggers when leaving from the player screen and respects a user setting. The PiP window hides all UI controls.
- **Audio Image Fix**: Artwork is now center-cropped and strictly clipped to prevent overflow in the Spotify-style layout.
- **Fullscreen Fix**: Video now truly expands to fill the entire screen on API 28 by using `FLAG_LAYOUT_NO_LIMITS`.
- **App Close Logic**: Swiping the app away from Recents now stops all playback and removes the notification.
- **Startup Logic**: Media is loaded immediately on app start to ensure "Recently Played" is visible right away.

## Project Structure

- `app/src/main/java/com/example/mediaplayer/`
    - `data/`: `MediaFile`, `MediaStoreRepository`, `SettingsRepository`, Room entities/DAOs.
    - `service/`: `PlaybackService` (ExoPlayer + MediaSession).
    - `ui/`: Compose screens, `VideoPlayer.kt`, `AudioPlayer.kt`, `PlayerComponents.kt`, `CommonUi.kt`.
    - `viewmodel/`: `MediaViewModel`.
    - `MainActivity.kt`: Entry point with navigation, lifecycle, and PiP logic.

## Setup & Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Java Version**: 21

## Usage

1. **Permissions**: Grant media access on first launch.
2. **Browsing**: Home (Recent), Library (All/Albums), and Settings.
3. **Playback**: Tap to play. Tapping the notification in the tray returns you to the player.
4. **PiP**: Swipe up to go home while watching a video to enter Picture-in-Picture (can be toggled in Settings).
5. **Video Gestures**: Double-tap to seek, vertical swipe for volume/brightness.
6. **Closing**: Swipe the app away from Recents to stop playback completely.
