package com.nuvio.app.features.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

/** YouTube-style home used only by IvyPlay profiles whose content mode is YOUTUBE. */
@Composable
fun IvyPlayYouTubeHomeScreen(
    modifier: Modifier = Modifier,
    channels: List<YouTubeChannelSnapshot> = emptyList(),
    onVideoClick: (YouTubeVideo) -> Unit,
    onChannelClick: (YouTubeChannel) -> Unit,
    loading: Boolean = false,
    error: String? = null,
    hasSource: Boolean = false,
    onRetry: () -> Unit,
    onManageAddons: () -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("Home") }
    val videos = channels.flatMap { it.videos + it.live }.distinctBy { it.videoId }
    val shorts = channels.flatMap { it.shorts }.distinctBy { it.videoId }
    val homeVideos = channels.flatMap { snapshot ->
        val all = snapshot.videos + snapshot.shorts + snapshot.live
        val ordered = snapshot.homeSections.flatMap { section ->
            section.itemIds.mapNotNull { id -> all.firstOrNull { it.videoId == id } }
        }
        if (ordered.isNotEmpty()) ordered else snapshot.videos + snapshot.live
    }.distinctBy { it.videoId }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = { YouTubeTopBar(onClose, { showSearch = !showSearch }) },
        bottomBar = { YouTubeBottomBar(selectedTab, { selectedTab = it }, onClose) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            if (showSearch) item {
                OutlinedTextField(value = query, onValueChange = { query = it },
                    placeholder = { Text("Tìm trong video đã tải từ addon") },
                    modifier = Modifier.fillMaxWidth().padding(12.dp), singleLine = true)
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (error != null) item {
                Text(error, color = Color.White, modifier = Modifier.padding(16.dp))
                TextButton(onClick = onRetry) { Text("Thử lại") }
            }
            if (!loading && channels.isEmpty() && error == null) item {
                Column(Modifier.padding(24.dp)) {
                    Text(if (hasSource) "Addon chưa cung cấp nội dung." else "Chưa cài addon YouTube cho hồ sơ này.", color = Color.White)
                    TextButton(onClick = onManageAddons) { Text("Quản lý addon") }
                }
            }
            if (channels.isNotEmpty()) {
                if (selectedTab == "Channels") {
                    items(channels, key = { it.channel.channelId }) { snapshot ->
                        Row(Modifier.fillMaxWidth().clickable { onChannelClick(snapshot.channel) }.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AsyncImage(snapshot.channel.avatar, null, Modifier.size(44.dp).clip(CircleShape))
                            Spacer(Modifier.width(12.dp))
                            Text(snapshot.channel.displayName ?: snapshot.channel.name, color = Color.White)
                        }
                    }
                } else {
                    val visibleVideos = (if (selectedTab == "Shorts") shorts else homeVideos)
                        .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) || it.channelName.orEmpty().contains(query, ignoreCase = true) }
                    if (visibleVideos.isEmpty()) item {
                        Text("Không có video phù hợp trong dữ liệu addon.", color = Color.White, modifier = Modifier.padding(24.dp))
                    }
                    if (selectedTab == "Home" && shorts.isNotEmpty()) {
                        item { YouTubeShortsShelf(shorts, onVideoClick) }
                    }
                    items(visibleVideos, key = { it.videoId }) { video ->
                        val channel = channels.firstOrNull { it.channel.channelId == video.channelId }?.channel
                        YouTubeVideoCard(video, channel, onVideoClick, onChannelClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeTopBar(onClose: () -> Unit, onSearch: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClose) { Icon(Icons.Default.ArrowBack, "Về Nuvio", tint = Color.White) }
        Icon(Icons.Default.PlayCircle, null, tint = Color.Red, modifier = Modifier.size(29.dp))
        Spacer(Modifier.width(6.dp))
        Text("YouTube", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Tìm video", tint = Color.White) }
    }
}

@Composable
private fun YouTubeVideoCard(
    video: YouTubeVideo,
    channel: YouTubeChannel?,
    onVideoClick: (YouTubeVideo) -> Unit,
    onChannelClick: (YouTubeChannel) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).clickable { onVideoClick(video) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF202020))) {
            AsyncImage(
                model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            video.durationSeconds?.let { seconds ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.82f),
                ) {
                    Text(
                        formatDuration(seconds),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (channel?.avatar != null) {
                AsyncImage(
                    model = channel.avatar,
                    contentDescription = channel.displayName ?: channel.name,
                    modifier = Modifier.size(38.dp).clip(CircleShape).clickable { onChannelClick(channel) },
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF333333))
                        .then(if (channel != null) Modifier.clickable { onChannelClick(channel) } else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        (channel?.displayName ?: channel?.name ?: video.channelName ?: "Y").take(1),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listOfNotNull(
                        video.channelName,
                        video.viewCount?.let(::compactViews),
                        video.uploadDate,
                    ).joinToString(" • "),
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun YouTubeShortsShelf(shorts: List<YouTubeVideo>, onVideoClick: (YouTubeVideo) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SmartDisplay, null, tint = Color.Red)
            Spacer(Modifier.width(7.dp))
            Text("Shorts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        LazyRow(
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shorts.take(10), key = { it.videoId }) { video ->
                Column(Modifier.width(160.dp).clickable { onVideoClick(video) }) {
                    AsyncImage(
                        model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f)
                            .clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020)),
                        contentScale = ContentScale.Crop,
                    )
                    Text(
                        video.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun YouTubeBottomBar(selected: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(64.dp).background(Color.Black),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
        listOf("Home" to Icons.Default.Home, "Shorts" to Icons.Default.SmartDisplay, "Channels" to Icons.Default.Subscriptions).forEach { (label, icon) ->
            TextButton(onClick = { onSelect(label) }) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, label, tint = if (selected == label) Color.White else Color.Gray)
                    Text(label, color = if (selected == label) Color.White else Color.Gray, fontSize = 10.sp)
                }
            }
        }
        TextButton(onClick = onClose) { Text("Nuvio") }
    }
}

private fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = secs.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}

private fun compactNumber(value: Long, unit: Long, suffix: String): String {
    val whole = value / unit
    val tenth = (value % unit) * 10 / unit
    return if (tenth == 0L) "$whole$suffix" else "$whole.$tenth$suffix"
}

private fun compactViews(value: Long): String = when {
    value >= 1_000_000_000L -> "${compactNumber(value, 1_000_000_000L, "B")} views"
    value >= 1_000_000L -> "${compactNumber(value, 1_000_000L, "M")} views"
    value >= 1_000L -> "${compactNumber(value, 1_000L, "K")} views"
    else -> "$value views"
}
