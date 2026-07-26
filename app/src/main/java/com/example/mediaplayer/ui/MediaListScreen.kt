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
