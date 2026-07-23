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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _shuffleModeEnabled = MutableStateFlow(false)
    val shuffleModeEnabled: StateFlow<Boolean> = _shuffleModeEnabled

    private val _player = MutableStateFlow<Player?>(null)
    val player: StateFlow<Player?> = _player

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
    }

    fun loadMedia() {
        viewModelScope.launch {
            _audioFiles.value = mediaRepository.getAudioFiles()
            _videoFiles.value = mediaRepository.getVideoFiles()
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
                        // Lets the player screen pick the video or audio layout
                        // without re-querying the repository.
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
