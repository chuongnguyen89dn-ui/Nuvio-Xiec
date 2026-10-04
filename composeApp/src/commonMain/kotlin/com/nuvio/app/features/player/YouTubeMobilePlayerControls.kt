package com.nuvio.app.features.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun YouTubeMobilePlayerControls(
    title: String,
    snapshot: PlayerPlaybackSnapshot,
    displayedPositionMs: Long,
    onBack: () -> Unit,
    onTogglePlayback: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeek: (Long) -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = .38f))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White, modifier = Modifier.size(34.dp).clickable(onClick = onBack).padding(5.dp))
            Text(title, color = Color.White, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1)
            Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(34.dp).clickable(onClick = onSettings).padding(5.dp))
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(38.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint=Color.White, modifier=Modifier.size(44.dp).clickable(onClick=onSeekBack))
            Icon(if(snapshot.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if(snapshot.isPlaying) "Pause" else "Play", tint=Color.White, modifier=Modifier.size(58.dp).clickable(onClick=onTogglePlayback))
            Icon(Icons.Rounded.Forward10, "Forward 10 seconds", tint=Color.White, modifier=Modifier.size(44.dp).clickable(onClick=onSeekForward))
        }
        val duration = snapshot.durationMs.coerceAtLeast(1L)
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=18.dp, vertical=16.dp)) {
            Slider(value=displayedPositionMs.coerceIn(0L,duration).toFloat(), onValueChange={ onSeek(it.toLong()) }, valueRange=0f..duration.toFloat())
            Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                Text(formatYoutubeTime(displayedPositionMs), color=Color.White)
                Text(formatYoutubeTime(snapshot.durationMs), color=Color.White)
            }
        }
    }
}
private fun formatYoutubeTime(ms:Long):String {
    val total=(ms.coerceAtLeast(0L)/1000L); val h=total/3600; val m=(total%3600)/60; val s=total%60
    return if(h>0) "${h}:${m.toString().padStart(2,'0')}:${s.toString().padStart(2,'0')}" else "${m}:${s.toString().padStart(2,'0')}"
}
