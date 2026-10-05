package com.nuvio.app.features.youtube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.profiles.IvyPlayContentMode
import com.nuvio.app.features.profiles.ProfileRepository

/**
 * Single routing gate used by the mobile Home surface.
 * YouTube profiles never fall through to the movie/TV home.
 *
 * On iOS the YouTube profile also activates the native SmartTube host. The
 * existing Compose surface stays mounted underneath as the fallback and is
 * still the active implementation on Android.
 */
@Composable
fun IvyPlayYouTubeHomeGate(
    youtubeContent: @Composable () -> Unit,
    standardContent: @Composable () -> Unit,
) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    val contentMode = profileState.activeProfile?.contentMode ?: IvyPlayContentMode.STANDARD
    val isYouTubeProfile = contentMode == IvyPlayContentMode.YOUTUBE

    DisposableEffect(isYouTubeProfile) {
        publishNativeYouTubeProfileVisible(isYouTubeProfile)
        onDispose {
            if (isYouTubeProfile) {
                publishNativeYouTubeProfileVisible(false)
            }
        }
    }

    when (contentMode) {
        IvyPlayContentMode.YOUTUBE -> youtubeContent()
        IvyPlayContentMode.STANDARD -> standardContent()
    }
}
