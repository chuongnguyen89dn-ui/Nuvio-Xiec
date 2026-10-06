package com.nuvio.app.features.youtube

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.PlatformBackHandler
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.profiles.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

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
    // Profile changes reset state; source changes are handled by the collector below.
    var channels by remember(profileState.activeProfile) { mutableStateOf<List<YouTubeChannelSnapshot>>(emptyList()) }
    var selectedChannelId by remember(profileState.activeProfile) { mutableStateOf<String?>(null) }
    var selectedPlaylistId by remember(profileState.activeProfile) { mutableStateOf<String?>(null) }
    var loading by remember(profileState.activeProfile) { mutableStateOf(true) }
    var error by remember(profileState.activeProfile) { mutableStateOf<String?>(null) }
    var retry by remember(profileState.activeProfile) { mutableStateOf(0) }

    // A rendered empty/loading/error screen is ready for touch; network is not a launch gate.
    LaunchedEffect(Unit) { onContentReady() }

    // Hydrate -> wait for manifests -> load catalogs in one coroutine. Keeping these
    // steps sequential prevents the catalog loader from observing the transient empty
    // addon state while a secondary YouTube profile is still inheriting Profile 1.
    LaunchedEffect(profileState.activeProfile, retry) {
        loading = true
        error = null
        try {
            AddonRepository.initialize()
            if (AddonRepository.uiState.value.addons.isEmpty()) {
                val addonProfileId = if (profileState.activeProfile?.usesPrimaryAddons == true) {
                    1
                } else {
                    profileState.activeProfile?.profileIndex ?: ProfileRepository.activeProfileId
                }
                AddonRepository.pullFromServer(addonProfileId)
            }
            if (retry > 0) {
                AddonRepository.uiState.value.addons
                    .filter { it.enabled && it.errorMessage != null }
                    .forEach { AddonRepository.refreshAddon(it.manifestUrl, forceRefresh = true) }
            }
            // Observe source changes for the entire lifetime of this route. collectLatest
            // cancels the old catalog request before a removed/disabled source can reappear.
            AddonRepository.uiState.map { it.addons }.distinctUntilChanged().collectLatest { currentAddons ->
                channels = emptyList()
                selectedChannelId = null
                selectedPlaylistId = null
                error = null
                loading = true
                if (currentAddons.any { it.enabled && it.isRefreshing }) return@collectLatest
                try {
                    channels = IvyPlayYouTubeAddonRepository.loadChannels(
                        currentAddons,
                        forceRefresh = retry > 0,
                    )
                    if (channels.isEmpty() && currentAddons.any { it.enabled && it.errorMessage != null }) {
                        error = "Không tải được addon. Hãy thử lại."
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Exception) {
                    error = "Không tải được dữ liệu từ addon. Hãy thử lại."
                } finally {
                    loading = false
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Exception) {
            error = "Không tải được dữ liệu từ addon. Hãy thử lại."
        } finally {
            loading = false
        }
    }
    val selected = channels.firstOrNull { it.channel.channelId == selectedChannelId }
    val selectedPlaylist = selected?.playlists?.firstOrNull { it.playlistId == selectedPlaylistId }
    PlatformBackHandler(enabled = true) {
        when {
            selectedPlaylist != null -> selectedPlaylistId = null
            selected != null -> selectedChannelId = null
            else -> onClose()
        }
    }
    if (selected != null && selectedPlaylist != null) {
        val playlistVideos = selectedPlaylist.videoIds.mapNotNull { id ->
            (selected.videos + selected.shorts + selected.live).firstOrNull { it.videoId == id }
        }
        IvyPlayYouTubePlaylistScreen(
            playlist = selectedPlaylist,
            videos = playlistVideos,
            modifier = modifier,
            onBack = { selectedPlaylistId = null },
            onVideoClick = onVideoClick,
        )
    } else if (selected != null) {
        IvyPlayYouTubeChannelScreen(
            snapshot = selected,
            modifier = modifier,
            onBack = { selectedChannelId = null },
            onVideoClick = onVideoClick,
            onPlaylistClick = { selectedPlaylistId = it.playlistId },
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
