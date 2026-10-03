package com.nuvio.app.features.youtube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun IvyPlayYouTubeHomeRoute(
    modifier: Modifier = Modifier,
    onVideoClick: (YouTubeVideo) -> Unit = {},
    onChannelClick: (YouTubeChannel) -> Unit = {},
) {
    var channels by remember { mutableStateOf<List<YouTubeChannelSnapshot>>(emptyList()) }

    LaunchedEffect(Unit) {
        val loaded = IvyPlayYouTubeFeedRepository.loadDefaultChannels()
        if (loaded.isNotEmpty()) channels = loaded
    }

    IvyPlayYouTubeHomeScreen(
        modifier = modifier,
        channels = channels,
        onVideoClick = onVideoClick,
        onChannelClick = onChannelClick,
    )
}
