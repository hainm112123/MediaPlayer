package com.example.mediaplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.mediaplayer.data.MediaFile
import com.example.mediaplayer.viewmodel.MediaViewModel
import com.example.mediaplayer.viewmodel.SortType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaListScreen(
    viewModel: MediaViewModel,
    onMediaClick: (MediaFile) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Video")

    val audioFiles by viewModel.filteredAudioFiles.collectAsState()
    val videoFiles by viewModel.filteredVideoFiles.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            placeholder = { Text("Search...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort")
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

        val currentList = if (selectedTab == 0) audioFiles else videoFiles

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = currentList,
                key = { it.id }
            ) { file ->
                MediaItemRow(file = file, onClick = { onMediaClick(file) })
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadMedia()
    }
}
