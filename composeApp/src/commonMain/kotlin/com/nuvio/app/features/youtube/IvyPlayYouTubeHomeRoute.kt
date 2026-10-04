package com.nuvio.app.features.youtube

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IvyPlayYouTubeHomeRoute(
    modifier: Modifier = Modifier,
    onVideoClick: (YouTubeVideo) -> Unit = {},
    onChannelClick: (YouTubeChannel) -> Unit = {},
) {
    var channels by remember { mutableStateOf<List<YouTubeChannelSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        val loaded = IvyPlayYouTubeAddonRepository.loadChannels()
        if (loaded.isNotEmpty()) channels = loaded
    }

    BoxWithConstraints(modifier = modifier) {
        val useTvSurface = maxWidth >= 900.dp
        if (useTvSurface) {
            IvyPlayYouTubeTvHomeScreen(
                modifier = Modifier,
                channels = channels,
                onVideoClick = onVideoClick,
                onChannelClick = onChannelClick,
            )
        } else {
            IvyPlayYouTubeHomeScreen(
                modifier = Modifier,
                channels = channels,
                onVideoClick = onVideoClick,
                onChannelClick = onChannelClick,
            )
        }
    }
}
