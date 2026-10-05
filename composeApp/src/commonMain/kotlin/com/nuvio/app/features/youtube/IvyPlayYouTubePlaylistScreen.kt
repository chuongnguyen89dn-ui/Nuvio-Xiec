package com.nuvio.app.features.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
fun IvyPlayYouTubePlaylistScreen(
    playlist: YouTubePlaylist,
    videos: List<YouTubeVideo>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onVideoClick: (YouTubeVideo) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = {
            Row(Modifier.fillMaxWidth().height(52.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Text(playlist.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                AsyncImage(
                    model = playlist.thumbnail,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF202020)),
                    contentScale = ContentScale.Crop,
                )
                Text(playlist.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            }
            if (videos.isEmpty()) {
                item { Text("Danh sách phát chưa có video từ addon.", color = Color(0xFFAAAAAA), modifier = Modifier.padding(24.dp)) }
            } else {
                items(videos, key = { it.videoId }) { video ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onVideoClick(video) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        AsyncImage(
                            model = video.thumbnail,
                            contentDescription = video.title,
                            modifier = Modifier.width(160.dp).aspectRatio(16f / 9f).background(Color(0xFF202020)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(video.title, color = Color.White, modifier = Modifier.weight(1f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
