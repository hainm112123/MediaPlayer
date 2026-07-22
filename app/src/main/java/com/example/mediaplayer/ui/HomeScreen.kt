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
