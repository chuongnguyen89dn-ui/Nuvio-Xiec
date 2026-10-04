package com.nuvio.app.features.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

enum class YouTubeChannelTab(val label: String) {
    HOME("TRANG CHỦ"), VIDEOS("VIDEO"), SHORTS("SHORTS"), LIVE("LIVE"),
    PLAYLISTS("DANH SÁCH PHÁT"), CHANNELS("KÊNH"), SERIES("SERIES"),
}

@Composable
fun IvyPlayYouTubeChannelScreen(
    snapshot: YouTubeChannelSnapshot,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onVideoClick: (YouTubeVideo) -> Unit,
    onPlaylistClick: (YouTubePlaylist) -> Unit = {},
) {
    var selectedTab by remember(snapshot.channel.channelId) { mutableStateOf(YouTubeChannelTab.HOME) }
    val channel = snapshot.channel
    Scaffold(
        modifier = modifier.fillMaxSize(), containerColor = Color.Black,
        topBar = {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
                Spacer(Modifier.weight(1f))
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                channel.banner?.let {
                    AsyncImage(model = it, contentDescription = channel.displayName ?: channel.name,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 5f), contentScale = ContentScale.Crop)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = channel.avatar, contentDescription = channel.displayName ?: channel.name,
                            modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFF272727)), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(channel.displayName ?: channel.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            channel.handle?.let { Text(it, color = Color(0xFFAAAAAA), fontSize = 13.sp) }
                            val stats = listOfNotNull(
                                channel.subscriberCount?.let { compactYouTubeNumber(it) + " người đăng ký" },
                                snapshot.videos.size.takeIf { it > 0 }?.let { it.toString() + " video" },
                            ).joinToString(" • ")
                            if (stats.isNotBlank()) Text(stats, color = Color(0xFFAAAAAA), fontSize = 12.sp)
                        }
                    }
                    channel.description?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = Color(0xFFDDDDDD), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    YouTubeChannelTab.entries.forEach { tab ->
                        TextButton(onClick = { selectedTab = tab }) {
                            Text(tab.label, color = if (selectedTab == tab) Color.White else Color(0xFFAAAAAA),
                                fontSize = 12.sp, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
                HorizontalDivider(color = Color(0xFF272727))
            }
            when (selectedTab) {
                YouTubeChannelTab.HOME -> {
                    val all = snapshot.videos + snapshot.shorts + snapshot.live
                    val ordered = snapshot.homeSections.flatMap { section -> section.itemIds.mapNotNull { id -> all.firstOrNull { it.videoId == id } } }
                    val homeVideos = if (ordered.isNotEmpty()) ordered else snapshot.videos.take(20)
                    if (homeVideos.isEmpty()) item { EmptyChannelTab("Kênh này chưa có nội dung Trang chủ.") }
                    else items(homeVideos, key = { "home-" + it.videoId }) { ChannelVideoRow(it, onVideoClick) }
                }
                YouTubeChannelTab.VIDEOS -> {
                    if (snapshot.videos.isEmpty()) item { EmptyChannelTab("Chưa có video.") }
                    else items(snapshot.videos, key = { "video-" + it.videoId }) { ChannelVideoRow(it, onVideoClick) }
                }
                YouTubeChannelTab.SHORTS -> {
                    if (snapshot.shorts.isEmpty()) item { EmptyChannelTab("Chưa có Shorts trong nguồn addon.") }
                    else items(snapshot.shorts, key = { "short-" + it.videoId }) { ChannelVideoRow(it, onVideoClick) }
                }
                YouTubeChannelTab.LIVE -> {
                    if (snapshot.live.isEmpty()) item { EmptyChannelTab("Chưa có nội dung phát trực tiếp.") }
                    else items(snapshot.live, key = { "live-" + it.videoId }) { ChannelVideoRow(it, onVideoClick) }
                }
                YouTubeChannelTab.PLAYLISTS -> {
                    if (snapshot.playlists.isEmpty()) item { EmptyChannelTab("Chưa có danh sách phát.") }
                    else items(snapshot.playlists, key = { it.playlistId }) { playlist ->
                        Row(Modifier.fillMaxWidth().clickable { onPlaylistClick(playlist) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(model = playlist.thumbnail, contentDescription = playlist.title,
                                modifier = Modifier.width(150.dp).aspectRatio(16f / 9f).background(Color(0xFF202020)), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(playlist.title, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                Text(playlist.videoIds.size.toString() + " video", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                            }
                        }
                    }
                }
                YouTubeChannelTab.CHANNELS -> item { EmptyChannelTab("Chưa có dữ liệu Kênh liên quan trong addon.") }
                YouTubeChannelTab.SERIES -> item { EmptyChannelTab("Chưa có dữ liệu Series trong addon.") }
            }
        }
    }
}

@Composable
private fun ChannelVideoRow(video: YouTubeVideo, onClick: (YouTubeVideo) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick(video) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        AsyncImage(model = video.thumbnail ?: "https://i.ytimg.com/vi/" + video.videoId + "/hqdefault.jpg",
            contentDescription = video.title, modifier = Modifier.width(168.dp).aspectRatio(16f / 9f).background(Color(0xFF202020)), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(listOfNotNull(video.viewCount?.let { compactYouTubeNumber(it) + " lượt xem" }, video.uploadDate).joinToString(" • "),
                color = Color(0xFFAAAAAA), fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyChannelTab(message: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(message, color = Color(0xFFAAAAAA), fontSize = 14.sp)
    }
}

private fun compactYouTubeNumber(value: Long): String = when {
    value >= 1_000_000_000L -> (value / 1_000_000_000L).toString() + "B"
    value >= 1_000_000L -> (value / 1_000_000L).toString() + "M"
    value >= 1_000L -> (value / 1_000L).toString() + "K"
    else -> value.toString()
}
