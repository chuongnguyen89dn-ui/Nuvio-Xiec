package com.nuvio.app.features.youtube

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.profiles.IvyPlayContentMode
import com.nuvio.app.features.profiles.ProfileRepository

/**
 * Single routing gate used by the mobile Home surface.
 * YouTube profiles never fall through to the Nuvio movie/TV home.
 */
@Composable
fun IvyPlayYouTubeHomeGate(
    youtubeContent: @Composable () -> Unit,
    standardContent: @Composable () -> Unit,
) {
    val profileState by ProfileRepository.state.collectAsStateWithLifecycle()
    when (profileState.activeProfile?.contentMode ?: IvyPlayContentMode.STANDARD) {
        IvyPlayContentMode.YOUTUBE -> youtubeContent()
        IvyPlayContentMode.STANDARD -> standardContent()
    }
}
