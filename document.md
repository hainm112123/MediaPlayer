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
      jumps to the previous playlist item. Without this, ExoPlayer's `seekToPrevious()`
      restarts the current item once more than 3 seconds have been played; the lockscreen
      and Bluetooth "previous" buttons route through `seekToPrevious()`, so this keeps
      every surface consistent.
    - `setSeekBackIncrementMs(10_000)` / `setSeekForwardIncrementMs(10_000)` — `seekBack()`
      and `seekForward()` move exactly 10 seconds, matching the double-tap gesture in the
      video UI.
- `MediaViewModel` creates a `SessionToken` for the service and connects a
  `MediaController` asynchronously (`MediaController.Builder(...).buildAsync()`). When the
  future resolves, the controller is published through `StateFlow<Player?>` and the UI
  recomposes with live controls.
- `playMedia(mediaFile, playlist)` converts the currently visible (filtered/sorted) list
  into Media3 `MediaItem`s. Each item carries `MediaMetadata` with title, artist, album
  **and `setMediaType(MEDIA_TYPE_MUSIC | MEDIA_TYPE_VIDEO)`** — the player screen uses the
  media type to decide which layout to show (see feature 6). It then sets the playlist on
  the controller, seeks to the tapped item's index, and calls `prepare()` + `play()`.

### 4. Playlist Navigation (Previous / Next)

- Because `playMedia()` loads the whole visible list as the player's playlist, skipping is a
  playlist operation, not a file reload.
- All in-app previous/next buttons call `seekToPreviousMediaItem()` /
  `seekToNextMediaItem()`, which move strictly between playlist entries (respecting
  shuffle order and repeat mode). System surfaces behave the same (see feature 13).

### 5. Repeat Modes & Shuffle (`MediaViewModel.kt`, `ui/PlayerComponents.kt`)

- `toggleRepeatMode()` cycles `Player.REPEAT_MODE_OFF → ONE → ALL` on the controller;
  `toggleShuffleMode()` flips `shuffleModeEnabled`. Both are plain Media3 `Player` APIs.
- The ViewModel registers a `Player.Listener` and mirrors the values into `StateFlow`s, so
  button highlighting always reflects the *actual* player state.
- The shared `RepeatButton` / `ShuffleButton` composables tint the active state with the
  Material 3 primary color, dim the inactive state (38% alpha), and switch the repeat icon
  between `Repeat` and `RepeatOne`.

### 6. Player Screen — Two Layouts (`ui/PlayerScreen.kt`) — redesigned

`PlayerScreen` is now a thin coordinator that follows the pattern of popular players:
**videos get a YouTube/MX Player-style screen, audio gets a Spotify-style Now Playing
screen.**

- It registers **one** `Player.Listener` (in a `DisposableEffect`) that mirrors player
  state into Compose state: `isPlaying`, `playbackState` (buffering), `mediaMetadata`,
  `currentMediaItemIndex`, playback speed (`onPlaybackParametersChanged`), and a queue
  version counter (`onTimelineChanged`).
- Position/duration are polled every 500 ms in a `LaunchedEffect` (guarding
  `C.TIME_UNSET`), which only runs while the screen is visible.
- The layout choice reads `mediaMetadata.mediaType` (set in `playMedia()`, feature 3):
  `MEDIA_TYPE_VIDEO` → `VideoPlayerContent`, otherwise → `AudioPlayerContent`.
- It also hosts the two shared bottom sheets: the **playing queue** and the **playback
  speed** picker (features 10–11).

### 7. Video Player — YouTube Style + Gestures (`ui/VideoPlayer.kt`) — new

A full-bleed black `Box` stacking, bottom to top:

1. **Video surface**: Media3 `PlayerView` via `AndroidView` with `useController = false`
   — it only renders video/subtitles (plus `keepScreenOn`); every control is custom
   Compose. `onRelease` detaches the player from the view.
2. **Gesture layer** (`Modifier.pointerInput`):
    - *Single tap* toggles the controls overlay.
    - *Double-tap* left/right half → `player.seekBack()` / `seekForward()` (±10s, feature
      3) with a transient "10s" chip that fades after ~0.7s.
    - *Vertical swipe* on the **right** half adjusts media volume via
      `AudioManager.setStreamVolume(STREAM_MUSIC, …)`; on the **left** half adjusts screen
      brightness via `window.attributes.screenBrightness` (clamped 0.01–1). A vertical
      level indicator (icon + bar + %) is shown while dragging. Brightness is reset to
      `BRIGHTNESS_OVERRIDE_NONE` when leaving the screen. A full-height drag spans the
      whole range, MX Player/VLC style.
3. **Controls overlay** (`AnimatedVisibility` fade, white content on gradient scrims):
    - **Auto-hide**: visible controls disappear after 3.5s while playing; any button press
      bumps an `interactionTick` that restarts the timer. They never hide while paused.
    - **Top bar** (top scrim): back button, title (ellipsized), playback-speed button,
      queue button.
    - **Center**: previous (`seekToPreviousMediaItem()`) — large translucent play/pause —
      next (`seekToNextMediaItem()`), YouTube-style. This is the *only* set of transport
      buttons, so there is no duplication with a bottom bar.
    - **Bottom** (bottom scrim): `current time — Slider — total time` row, then
      repeat + shuffle toggles (left) and the fullscreen toggle (right).
4. **Status layers**: a buffering `CircularProgressIndicator` in the center (the play
   button yields its spot while buffering), the seek-feedback chip, and the
   volume/brightness indicators.

### 8. Audio Player — Spotify-Style Now Playing (`ui/AudioPlayer.kt`) — new

A vertical layout on a dark gradient (`Brush.verticalGradient` from `surfaceVariant` to
`background`), always visible (no auto-hide), no `PlayerView` needed — audio keeps playing
in the service regardless of what is rendered:

- **Top bar**: collapse chevron (`KeyboardArrowDown` → back) and the album title (or "Now
  Playing") centered.
- **Artwork**: a large rounded 1:1 card. The bytes come from
  `mediaMetadata.artworkData` — ExoPlayer automatically extracts embedded album art from
  the file's tags during playback — rendered with Coil's `AsyncImage` using
  `ContentScale.Crop` + center alignment, so the art is always center-cropped to fill the
  entire square edge-to-edge regardless of the source aspect ratio (the square sizing and
  rounded-corner clip are applied directly on the image). Files without embedded art get
  a gradient placeholder with a `MusicNote` icon.
- **Metadata**: title (bold, `basicMarquee()` scroll for long names) and artist (dimmed).
- **Seek**: shared `SeekBarRow` (times + slider).
- **Transport row**: shuffle — previous — large circular `FilledIconButton` play/pause
  (72dp) — next — repeat, in the Spotify order.
- **Bottom row**: playback-speed text button (e.g. "1x") on the left, queue button on the
  right.

### 9. Fullscreen Mode (`ui/VideoPlayer.kt`)

- The fullscreen toggle lives at the bottom-right of the video controls
  (`Fullscreen`/`FullscreenExit` icons); state is held in `rememberSaveable`.
- A `LaunchedEffect` applies it with `WindowInsetsControllerCompat`:
    - **Enter**: `hide(WindowInsetsCompat.Type.systemBars())` with
      `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` — immersive mode; bars peek back with a
      swipe. Because `MainActivity` uses `enableEdgeToEdge()` and the Scaffold consumes
      window insets, content automatically expands to the whole screen.
    - **Exit**: `show(systemBars())` restores the bars.
- `BackHandler` intercepts the back gesture while fullscreen — the first press exits
  fullscreen instead of leaving the screen — and a `DisposableEffect` restores the system
  bars (and screen brightness) if the user navigates away while fullscreen.
- The app's bottom navigation bar is already hidden on the player route.

### 10. Playback Speed (`ui/PlayerComponents.kt` — `SpeedSheet`) — new

- A `ModalBottomSheet` listing 0.25x / 0.5x / 0.75x / 1x / 1.25x / 1.5x / 2x; the current
  speed is checked. Selecting calls `player.setPlaybackSpeed()` — a standard Media3
  `Player` command executed by ExoPlayer in the service (`COMMAND_SET_SPEED_AND_PITCH`).
- The current speed is tracked live via `Player.Listener.onPlaybackParametersChanged`, so
  the audio screen's speed label and the sheet's checkmark stay accurate even if speed is
  changed elsewhere.

### 11. Playing Queue (`ui/PlayerComponents.kt` — `QueueSheet`) — new

- A `ModalBottomSheet` that renders the player's actual playlist by reading
  `player.getMediaItemAt(i).mediaMetadata` for `0 until mediaItemCount` (recomputed when
  the timeline changes).
- The current item is highlighted (primary color, bold, `Equalizer` icon); other rows show
  their queue number. Tapping a row calls `player.seekToDefaultPosition(index)` +
  `play()` — jumping within the playlist, not reloading it.

### 12. Background Playback (`service/PlaybackService.kt`, `MainActivity.kt`)

- `PlaybackService` is declared in the manifest with
  `foregroundServiceType="mediaPlayback"` and the `MediaSessionService` intent filter.
  While playing, Media3 automatically promotes the service to a foreground service with a
  media notification, so playback survives the app being minimized or the screen locking.
- The "Background Playback" toggle (Settings tab) is persisted in DataStore. `MainScreen`
  observes the Activity lifecycle with a `LifecycleEventObserver`; on `ON_STOP`, if the
  toggle is off, it pauses the player.
- `onTaskRemoved()` stops the service if nothing is playing so no orphan notification is
  left after swiping the app away.

### 13. Notification & Lockscreen Controls (`service/PlaybackService.kt`)

- Media3's `MediaSessionService` posts a `MediaStyle` notification automatically through
  its `DefaultMediaNotificationProvider`. The notification shows the current item's
  metadata plus exactly three action buttons: **Previous media item · Play/Pause · Next
  media item** — the provider wires prev/next to `COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM` /
  `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`, i.e. real playlist navigation.
- On the lockscreen and in Android 13+ system media controls, buttons are derived from the
  platform `PlaybackState` and route through `seekToPrevious()`/`seekToNext()`; thanks to
  `setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)` (feature 3), "previous" there also
  always skips to the previous media item.
- `MediaSession.Callback.onConnect()` explicitly adds the seek-to-previous/next (item)
  commands plus `COMMAND_SET_REPEAT_MODE` / `COMMAND_SET_SHUFFLE_MODE` to the available
  player commands. The same session handles hardware media keys from Bluetooth headsets.

### 14. Recently Played History (`data/RecentMedia.kt`, `data/AppDatabase.kt`)

- Every call to `playMedia()` inserts a `RecentMedia` row (media id, URI, timestamp, type)
  into a Room database (`media_database`, schema v2, destructive migration fallback).
- `RecentMediaDao.getRecentMedia()` returns a `Flow`, which the ViewModel `combine`s with
  the scanned audio/video lists to resolve ids back to full `MediaFile` objects; the Home
  tab updates reactively.

### 15. Custom Albums (`data/Album.kt`, `ui/AlbumDetailScreen.kt`, `ui/AddToAlbumDialog.kt`)

- Modeled with two Room entities: `Album` and an `AlbumMediaCrossRef` join table
  (albumId ↔ mediaId), enabling many-to-many relations between albums and media files.
- Users can create/delete albums and add/remove any audio or video file via
  `AddToAlbumDialog`. `getAlbumWithMedia(albumId)` combines the cross-references with the
  scanned media lists to produce the album's contents as a `Flow<List<MediaFile>>`.
- Tapping an item inside an album plays it with the album's contents as the playlist.

### 16. Search & Sorting (`viewmodel/MediaViewModel.kt`, `ui/MediaListScreen.kt`)

- The Library lists are exposed as `filteredAudioFiles` / `filteredVideoFiles`:
  `combine(files, searchQuery, sortType, sortOrder)` recomputes reactively whenever any
  input changes.
- Search matches title or artist, case-insensitively. Sorting supports Name, Size, Date,
  Duration, Artist, Album, each with an Ascending/Descending toggle.
- Because playback always uses the currently filtered list as the playlist, Previous/Next
  and the queue navigate within exactly what the user saw.

### 17. Thumbnails (`MediaPlayerApplication.kt`, `ui/CommonUi.kt`)

- The application class implements Coil's `ImageLoaderFactory` and registers a
  `VideoFrameDecoder`, so `AsyncImage` composables can render a frame extracted from any
  video URI as its thumbnail, with crossfade. Audio items fall back to vector icons.

### 18. Settings (`data/SettingsRepository.kt`, `ui/SettingsScreen.kt`)

- Preferences are stored in Jetpack DataStore (`preferencesDataStore("settings")`) and
  exposed as `Flow`s — currently the Background Playback switch. Writes are transactional
  `suspend` calls (`dataStore.edit`).

### 19. Navigation & App Shell (`MainActivity.kt`)

- A single-activity app: `NavHost` with routes `home`, `list`, `settings`, `player`, and
  `album_detail/{albumId}` (typed `Long` nav argument). The player route passes
  `onBack = { navController.popBackStack() }` so the player's back/collapse buttons pop
  navigation.
- A Material 3 `NavigationBar` is shown only on the three top-level tabs; `player` and
  `album_detail` render full-screen. Tab switching uses `popUpTo` + `saveState`/
  `restoreState` + `launchSingleTop` so each tab keeps its state.
- `enableEdgeToEdge()` lets content draw behind the system bars, which fullscreen relies on.

## What Changed in the Latest Update — Player Redesign

- **Two dedicated layouts**, following the most common player designs:
  **video → YouTube/MX Player style** (auto-hiding overlay: top bar with back/title/
  speed/queue, center prev–play/pause–next, bottom seek bar with repeat/shuffle/fullscreen),
  **audio → Spotify-style Now Playing** (large album art from embedded tags, title/artist,
  slider, always-visible shuffle–prev–play–next–repeat row).
- The Media3 `PlayerView` controller is fully disabled (`useController = false`); all
  controls are custom Compose (`VideoPlayer.kt`, `AudioPlayer.kt`, `PlayerComponents.kt`).
- **Gestures on video**: double-tap left/right to seek ±10s; vertical swipe right/left to
  change volume/brightness with on-screen level indicators.
- **Playback speed menu** (0.25x–2x) and **playing queue bottom sheet** (tap to jump,
  current item highlighted) available from both layouts.
- Layout selection is driven by `MediaMetadata.mediaType` set in `playMedia()`.
- Buffering spinner, marquee titles, gradient scrims, and edge-to-edge fullscreen retained
  from/improved over the previous design.

### Previous update

- Removed `PlayerView`'s default center prev/next buttons; previous/next switched to
  `seekToPreviousMediaItem()` / `seekToNextMediaItem()`.
- "Previous" made to always skip to the previous media item on notification, lockscreen,
  and headset controls via `setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`.
- Added the first fullscreen button and the floating auto-hiding control bar (now
  superseded by the redesign above).

## Project Structure

- `app/src/main/java/com/example/mediaplayer/`
    - `data/`: `MediaFile` models, `MediaStoreRepository`, `SettingsRepository`, Room
      entities/DAOs (`RecentMedia`, `Album`) and `AppDatabase`.
    - `service/`: `PlaybackService` (MediaSessionService) hosting ExoPlayer + MediaSession.
    - `ui/`: Compose screens (`HomeScreen`, `MediaListScreen`, `PlayerScreen`,
      `AlbumDetailScreen`, `SettingsScreen`), the player layouts (`VideoPlayer.kt`,
      `AudioPlayer.kt`) and shared player widgets (`PlayerComponents.kt`: seek bar,
      transport controls, queue sheet, speed sheet, gesture indicators), dialogs, and
      shared components (`CommonUi.kt`).
    - `viewmodel/`: `MediaViewModel` — owns the `MediaController` connection and all UI state.
    - `MainActivity.kt`: entry point with navigation host, lifecycle observation, and
      permission handling.
    - `MediaPlayerApplication.kt`: application class configuring Coil's `ImageLoader`.

## Setup & Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Build System**: Android Studio Narwhal 3 (2025.1.3) compatible, AGP 8.13.0
- **Java Version**: 21

### Permissions

- `READ_EXTERNAL_STORAGE` (legacy storage access, Android 12 and below)
- `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` (Android 13+ granular media access)
- `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (background playback)

## Usage

1. **Permissions**: on first launch, grant media access.
2. **Browsing**: switch between **Home**, **Library**, and **Settings** with the bottom bar.
3. **Playback**: tap any item to play it with the visible list as the playlist.
4. **Video controls**: tap the video to show/hide controls (they auto-hide after ~3.5s
   while playing); double-tap left/right to seek ±10s; swipe vertically on the right for
   volume and on the left for brightness; use the bottom-right button for **fullscreen**
   (back exits fullscreen first).
5. **Audio controls**: the Now Playing screen keeps its controls visible — drag the slider
   to seek, use shuffle/prev/play/next/repeat, and swipe down via the chevron to go back.
6. **Speed & queue**: open the speed menu (0.25x–2x) or the playing queue from the video
   top bar or the audio bottom row; tap a queue entry to jump to it.
7. **System controls**: manage playback from the notification or lockscreen — previous
   media, play/pause, next media.
8. **Background Play**: toggle "Background Playback" in Settings to choose whether music
   keeps playing when the app is minimized.
