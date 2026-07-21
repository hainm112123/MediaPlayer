# MediaPlayer Android Project

A modern, high-performance media player for Android built with Jetpack Compose and Android Media3.

## Features

- **Media Discovery**: Automatically scans local storage for audio and video files using `MediaStore`.
- **High-Performance Playback**: Powered by **Android Media3 (ExoPlayer)** for seamless audio and video streaming.
- **Rich Metadata**: Displays detailed information including title, artist, album, year, size, and duration.
- **Intelligent Thumbnails**: Uses **Coil** to extract frames from videos and display them as thumbnails, with beautiful vector fallbacks for audio.
- **History Tracking**: Keeps a persistent "Recently Played" list using a **Room Database**.
- **Organization**:
    - **Tabbed Interface**: Separate views for Home (Recent), Audio Library, and Video Library.
    - **Search**: Real-time filtering of media items by title or artist.
    - **Sorting**: Flexible sorting options by Name, Size, Date, Duration, Artist, and Album.
- **Responsive UI**: Fully declarative UI built with **Jetpack Compose** and **Material 3**.

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Playback Engine**: Media3 / ExoPlayer
- **Persistence**: Room Database
- **Image Loading**: Coil (with Video support)
- **Navigation**: Jetpack Navigation Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Build System**: Gradle Kotlin DSL with Version Catalogs (libs.versions.toml) and KSP

## Project Structure

- `app/src/main/java/com/example/mediaplayer/`
    - `data/`: Contains `MediaFile` models, `MediaStoreRepository`, and Room database configuration (`AppDatabase`, `RecentMedia`).
    - `ui/`: Compose screens (`HomeScreen`, `MediaListScreen`, `PlayerScreen`) and shared components.
    - `viewmodel/`: `MediaViewModel` handling the business logic, playback control, and UI state.
    - `MainActivity.kt`: Entry point with navigation host and permission handling.
    - `MediaPlayerApplication.kt`: Custom application class for initializing Coil's `ImageLoader`.

## Setup & Requirements

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 36
- **Compile SDK**: 36
- **Build System**: Android Studio Narwhal 3 (2025.1.3) compatible, AGP 8.13.0.
- **Java Version**: 21

### Permissions
The app requests the following permissions at runtime:
- `READ_EXTERNAL_STORAGE` (Legacy)
- `READ_MEDIA_AUDIO` (Android 13+)
- `READ_MEDIA_VIDEO` (Android 13+)

## Usage

1. **Permissions**: On first launch, grant media access permissions.
2. **Browsing**: Navigate between the **Home** and **Library** tabs using the bottom navigation bar.
3. **Search & Sort**: Use the search bar in the Library tab to filter files or the sort icon to reorder them.
4. **Playback**: Tap any item to open the player screen. Video files will show a preview, and playback controls are available on-screen.
