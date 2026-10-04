package com.nuvio.app.features.youtube

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException

@Composable
fun IvyPlayYouTubeHomeRoute(
    modifier: Modifier = Modifier,
    onVideoClick: (YouTubeVideo) -> Unit,
    onClose: () -> Unit,
    onManageAddons: () -> Unit,
    onContentReady: () -> Unit,
) {
    val addonsState by AddonRepository.uiState.collectAsStateWithLifecycle()
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val addons = addonsState.addons
    // Reset immediately when the installed sources/profile change, including removal.
    var channels by remember(profileState.activeProfile, addons) { mutableStateOf<List<YouTubeChannelSnapshot>>(emptyList()) }
    var selectedChannelId by remember(profileState.activeProfile, addons) { mutableStateOf<String?>(null) }
    var loading by remember(profileState.activeProfile, addons) { mutableStateOf(true) }
    var error by remember(profileState.activeProfile, addons) { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) { AddonRepository.initialize() }
    // A rendered empty/loading/error screen is ready for touch; network is not a launch gate.
    LaunchedEffect(Unit) { onContentReady() }
    LaunchedEffect(profileState.activeProfile, addons, retry) {
        loading = true
        error = null
        try {
            channels = IvyPlayYouTubeAddonRepository.loadChannels(addons, forceRefresh = retry > 0)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = "Không tải được dữ liệu từ addon. Hãy thử lại."
        } finally {
            loading = false
        }
    }
    val selected = channels.firstOrNull { it.channel.channelId == selectedChannelId }
    PlatformBackHandler(enabled = true) {
        if (selected != null) selectedChannelId = null else onClose()
    }
    if (selected != null) {
        IvyPlayYouTubeChannelScreen(
            snapshot = selected,
            modifier = modifier,
            onBack = { selectedChannelId = null },
            onVideoClick = onVideoClick,
        )
    } else {
        IvyPlayYouTubeHomeScreen(
            modifier = modifier,
            channels = channels,
            loading = loading || addons.any { it.enabled && it.isRefreshing },
            error = error,
            hasSource = IvyPlayYouTubeAddonRepository.hasYouTubeSource(addons),
            onRetry = { retry++ },
            onManageAddons = onManageAddons,
            onClose = onClose,
            onVideoClick = onVideoClick,
            onChannelClick = { selectedChannelId = it.channelId },
        )
    }
}
