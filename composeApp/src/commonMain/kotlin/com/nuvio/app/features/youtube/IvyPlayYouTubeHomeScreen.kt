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
import androidx.compose.runtime.Composable
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

/** IvyPlay's YouTube-mode home. Deliberately independent from Nuvio's movie/TV home. */
@Composable
fun IvyPlayYouTubeHomeScreen(
    modifier: Modifier = Modifier,
    channels: List<YouTubeChannelSnapshot> = emptyList(),
    onVideoClick: (YouTubeVideo) -> Unit = {},
    onChannelClick: (YouTubeChannel) -> Unit = {},
) {
    val videos = channels.flatMap { it.videos + it.live }.distinctBy { it.videoId }
    val shorts = channels.flatMap { it.shorts }.distinctBy { it.videoId }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Black,
        topBar = { YouTubeTopBar() },
        bottomBar = { YouTubeBottomBar() },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 12.dp)) {
            item { YouTubeTopicChips() }
            if (channels.isEmpty()) item { IvyPlayChannelPlaceholders() }
            else {
                items(videos, key = { it.videoId }) { video ->
                    val channel = channels.firstOrNull { it.channel.channelId == video.channelId }?.channel
                    YouTubeVideoCard(video, channel, onVideoClick, onChannelClick)
                }
                if (shorts.isNotEmpty()) item { YouTubeShortsShelf(shorts, onVideoClick) }
            }
        }
    }
}

@Composable private fun YouTubeTopBar() {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.PlayCircle, null, tint = Color.Red, modifier = Modifier.size(29.dp)); Spacer(Modifier.width(6.dp))
        Text("YouTube", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
        Icon(Icons.Default.Cast, "Cast", tint = Color.White); Spacer(Modifier.width(22.dp)); Icon(Icons.Default.NotificationsNone, "Notifications", tint = Color.White)
        Spacer(Modifier.width(22.dp)); Icon(Icons.Default.Search, "Search", tint = Color.White); Spacer(Modifier.width(20.dp))
        Box(Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF1565C0)), contentAlignment = Alignment.Center) { Text("I", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable private fun YouTubeTopicChips() {
    val topics = listOf("All", "Travel", "Food", "Live", "Music", "Recently uploaded")
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(topics) { topic -> Surface(shape = RoundedCornerShape(8.dp), color = if (topic == "All") Color.White else Color(0xFF272727)) {
            Text(topic, color = if (topic == "All") Color.Black else Color.White, modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp), fontSize = 14.sp)
        } }
    }
}

@Composable private fun IvyPlayChannelPlaceholders() {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Text("Channels", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        listOf("Khoai Lang Thang", "HOA BAN FOOD").forEachIndexed { index, name ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(if (index == 0) Color(0xFF6D4C41) else Color(0xFF2E7D32)), contentAlignment = Alignment.Center) { Text(name.take(1), color = Color.White, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(12.dp)); Column { Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold); Text("Loading YouTube channel feed…", color = Color(0xFFAAAAAA), fontSize = 13.sp) }
            }
        }
    }
}

@Composable private fun YouTubeVideoCard(video: YouTubeVideo, channel: YouTubeChannel?, onVideoClick: (YouTubeVideo) -> Unit, onChannelClick: (YouTubeChannel) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp).clickable { onVideoClick(video) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color(0xFF202020))) {
            AsyncImage(model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg", contentDescription = video.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            video.durationSeconds?.let { seconds ->
                Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp), shape = RoundedCornerShape(4.dp), color = Color.Black.copy(alpha = .82f)) {
                    Text(formatDuration(seconds), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            if (channel?.avatar != null) AsyncImage(model = channel.avatar, contentDescription = channel.displayName ?: channel.name, modifier = Modifier.size(38.dp).clip(CircleShape).clickable { onChannelClick(channel) }, contentScale = ContentScale.Crop)
            else Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0xFF333333)).then(if (channel != null) Modifier.clickable { onChannelClick(channel) } else Modifier), contentAlignment = Alignment.Center) { Text((channel?.displayName ?: channel?.name ?: video.channelName ?: "Y").take(1), color = Color.White, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) {
                Text(video.title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(3.dp))
                Text(listOfNotNull(video.channelName, video.viewCount?.let(::compactViews), video.uploadDate).joinToString(" • "), color = Color(0xFFAAAAAA), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }; Icon(Icons.Default.MoreVert, "More", tint = Color.White)
        }
    }
}

@Composable private fun YouTubeShortsShelf(shorts: List<YouTubeVideo>, onVideoClick: (YouTubeVideo) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.SmartDisplay, null, tint = Color.Red); Spacer(Modifier.width(7.dp)); Text("Shorts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        LazyRow(contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(shorts.take(10), key = { it.videoId }) { video ->
            Column(Modifier.width(160.dp).clickable { onVideoClick(video) }) { AsyncImage(model = video.thumbnail ?: "https://i.ytimg.com/vi/${video.videoId}/hqdefault.jpg", contentDescription = video.title, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF202020)), contentScale = ContentScale.Crop); Text(video.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
        } }
    }
}

@Composable private fun YouTubeBottomBar() { Row(Modifier.fillMaxWidth().height(64.dp).background(Color.Black), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) { YouTubeNavItem(Icons.Default.Home, "Home"); YouTubeNavItem(Icons.Default.SmartDisplay, "Shorts"); Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, "Create", tint = Color.Black) }; YouTubeNavItem(Icons.Default.Subscriptions, "Subscriptions"); YouTubeNavItem(Icons.Default.AccountCircle, "You") } }
@Composable private fun YouTubeNavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp)); Text(label, color = Color.White, fontSize = 10.sp) } }

private fun formatDuration(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${secs.toString().padStart(2, '0')}"
}
private fun oneDecimal(value: Double): String { val scaled = (value * 10.0).toLong(); return "${scaled / 10}.${scaled % 10}" }
private fun compactViews(value: Long): String = when {
    value >= 1_000_000_000 -> "${oneDecimal(value / 1_000_000_000.0)}B views"
    value >= 1_000_000 -> "${oneDecimal(value / 1_000_000.0)}M views"
    value >= 1_000 -> "${oneDecimal(value / 1_000.0)}K views"
    else -> "$value views"
}
