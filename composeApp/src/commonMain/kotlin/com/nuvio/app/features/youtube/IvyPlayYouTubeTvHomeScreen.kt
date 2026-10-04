package com.nuvio.app.features.youtube

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * Android TV / large-screen presentation of the same IvyPlay YouTube profile data used on mobile.
 * The information architecture stays aligned with mobile while interaction is D-pad/focus first.
 */
@Composable
fun IvyPlayYouTubeTvHomeScreen(
    modifier: Modifier = Modifier,
    channels: List<YouTubeChannelSnapshot> = emptyList(),
    onVideoClick: (YouTubeVideo) -> Unit = {},
    onChannelClick: (YouTubeChannel) -> Unit = {},
) {
    val videos = channels.flatMap { it.videos + it.live }.distinctBy { it.videoId }
    val shorts = channels.flatMap { it.shorts }.distinctBy { it.videoId }

    Row(modifier.fillMaxSize().background(Color.Black)) {
        TvNavRail()
        Column(Modifier.fillMaxSize()) {
            TvTopBar()
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 28.dp, end = 32.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item { TvTopicRow() }
                if (channels.isEmpty()) {
                    item { TvLoadingChannels() }
                } else {
                    if (videos.isNotEmpty()) {
                        item {
                            TvShelf(
                                title = "Recommended",
                                videos = videos.take(12),
                                channels = channels,
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick,
                            )
                        }
                    }
                    channels.forEach { snapshot ->
                        val channelVideos = (snapshot.videos + snapshot.live).distinctBy { it.videoId }
                        if (channelVideos.isNotEmpty()) {
                            item(key = snapshot.channel.channelId) {
                                TvShelf(
                                    title = snapshot.channel.displayName ?: snapshot.channel.name,
                                    videos = channelVideos.take(10),
                                    channels = listOf(snapshot),
                                    onVideoClick = onVideoClick,
                                    onChannelClick = onChannelClick,
                                )
                            }
                        }
                    }
                    if (shorts.isNotEmpty()) {
                        item { TvShortsShelf(shorts.take(10), onVideoClick) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvNavRail() {
    Column(
        Modifier.width(116.dp).fillMaxHeight().padding(top = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(Icons.Default.PlayCircle, null, tint = Color.Red, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(12.dp))
        TvNavItem(Icons.Default.Home, "Home", true)
        TvNavItem(Icons.Default.SmartDisplay, "Shorts", false)
        TvNavItem(Icons.Default.Subscriptions, "Subscriptions", false)
        Spacer(Modifier.weight(1f))
        TvNavItem(Icons.Default.AccountCircle, "You", false)
    }
}

@Composable
private fun TvNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Color.White
        selected -> Color(0xFF272727)
        else -> Color.Transparent
    }
    val foreground = if (focused) Color.Black else Color.White
    Column(
        Modifier.width(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, label, tint = foreground, modifier = Modifier.size(25.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = foreground, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun TvTopBar() {
    Row(
        Modifier.fillMaxWidth().height(82.dp).padding(horizontal = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("YouTube", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF272727)) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Search, "Search", tint = Color.White, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text("Search", color = Color.White, fontSize = 15.sp)
            }
        }
        Spacer(Modifier.width(20.dp))
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF1565C0)),
            contentAlignment = Alignment.Center,
        ) {
            Text("I", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TvTopicRow() {
    val topics = listOf("All", "Travel", "Food", "Live", "Music", "Recently uploaded")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(topics) { topic -> TvChip(topic, topic == "All") }
    }
}

@Composable
private fun TvChip(label: String, selected: Boolean) {
    var focused by remember { mutableStateOf(false) }
    val background = if (focused || selected) Color.White else Color(0xFF272727)
    val foreground = if (focused || selected) Color.Black else Color.White
    Surface(
        modifier = Modifier.onFocusChanged { focused = it.isFocused }.focusable(),
        shape = RoundedCornerShape(9.dp),
        color = background,
    ) {
        Text(label, color = foreground, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp))
    }
}

@Composable
private fun TvLoadingChannels() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Channels", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Chưa có dữ liệu kênh từ addon.", color = Color(0xFFCCCCCC), fontSize = 18.sp)
    }
}

@Composable
private fun TvShelf(
    title: String,
    videos: List<YouTubeVideo>,
    channels: List<YouTubeChannelSnapshot>,
    onVideoClick: (YouTubeVideo) -> Unit,
    onChannelClick: (YouTubeChannel) -> Unit,
) {
    Column {
        Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            items(videos, key = { it.videoId }) { video ->
                val channel = channels.firstOrNull { it.channel.channelId == video.channelId }?.channel
                TvVideoCard(video, channel, onVideoClick, onChannelClick)
            }
        }
    }
}

@Composable
private fun TvVideoCard(
    video: YouTubeVideo,
    channel: YouTubeChannel?,
    onVideoClick: (YouTubeVideo) -> Unit,
    onChannelClick: (YouTubeChannel) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.width(300.dp)
            .scale(if (focused) 1.06f else 1f)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable { onVideoClick(video) },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (focused) Color.White else Color(0xFF202020)),
        ) {
            AsyncImage(
                model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize().padding(if (focused) 3.dp else 0.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            video.durationSeconds?.let { duration ->
                Surface(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp),
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.84f),
                ) {
                    Text(tvFormatDuration(duration), color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                }
            }
        }
        Spacer(Modifier.height(9.dp))
        Text(
            video.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            channel?.displayName ?: channel?.name ?: video.channelName.orEmpty(),
            color = Color(0xFFAAAAAA),
            fontSize = 13.sp,
            maxLines = 1,
            modifier = if (channel != null) Modifier.clickable { onChannelClick(channel) } else Modifier,
        )
    }
}

@Composable
private fun TvShortsShelf(shorts: List<YouTubeVideo>, onVideoClick: (YouTubeVideo) -> Unit) {
    Column {
        Text("Shorts", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(shorts, key = { it.videoId }) { video ->
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier.width(150.dp)
                        .scale(if (focused) 1.06f else 1f)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onVideoClick(video) },
                ) {
                    AsyncImage(
                        model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg",
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(Color(0xFF202020)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(video.title, color = Color.White, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private fun tvFormatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = secs.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$minutes:$ss"
}
