package com.example.mediaplayer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.mediaplayer.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortType {
    NAME, SIZE, DATE, DURATION, ARTIST, ALBUM
}

enum class SortOrder {
    ASCENDING, DESCENDING
}

class MediaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaStoreRepository(application.contentResolver)
    private val db = AppDatabase.getDatabase(application)
    private val recentDao = db.recentMediaDao()

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

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode: StateFlow<Int> = _repeatMode

    private val _currentPlayerState = MutableStateFlow<Player?>(null)
    val currentPlayer: StateFlow<Player?> = _currentPlayerState

    val player: ExoPlayer by lazy {
        ExoPlayer.Builder(application).build().also {
            it.repeatMode = _repeatMode.value
            _currentPlayerState.value = it
        }
    }

    fun loadMedia() {
        viewModelScope.launch {
            _audioFiles.value = repository.getAudioFiles()
            _videoFiles.value = repository.getVideoFiles()
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
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
            Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
        _repeatMode.value = nextMode
    }

    fun playMedia(mediaFile: MediaFile, playlist: List<MediaFile>) {
        val mediaItems = playlist.map { MediaItem.fromUri(it.uri) }
        val startIndex = playlist.indexOfFirst { it.id == mediaFile.id }
        
        player.setMediaItems(mediaItems)
        if (startIndex != -1) {
            player.seekTo(startIndex, 0L)
        }
        player.prepare()
        player.play()

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
        player.release()
    }
}
