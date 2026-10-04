package com.nuvio.app.features.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal enum class YouTubeSettingsPage { MAIN, QUALITY, AUDIO, SUBTITLES, SPEED }

@Composable
internal fun YouTubeMobileSettingsPanel(
    visible: Boolean,
    page: YouTubeSettingsPage,
    qualities: List<PlayerVideoQuality>,
    audioTracks: List<AudioTrack>,
    selectedAudioIndex: Int,
    subtitleTracks: List<SubtitleTrack>,
    selectedSubtitleIndex: Int,
    playbackSpeed: Float,
    onPage: (YouTubeSettingsPage) -> Unit,
    onQuality: (Int?) -> Unit,
    onAudio: (Int) -> Unit,
    onSubtitle: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSidePanel(visible = visible, onDismiss = onDismiss) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            PlayerPanelHeader(title = when (page) {
                YouTubeSettingsPage.MAIN -> "Settings"
                YouTubeSettingsPage.QUALITY -> "Quality"
                YouTubeSettingsPage.AUDIO -> "Audio track"
                YouTubeSettingsPage.SUBTITLES -> "Captions"
                YouTubeSettingsPage.SPEED -> "Playback speed"
            }) {
                PlayerDialogButton(label = if (page == YouTubeSettingsPage.MAIN) "Close" else "Back") {
                    if (page == YouTubeSettingsPage.MAIN) onDismiss() else onPage(YouTubeSettingsPage.MAIN)
                }
            }
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                when (page) {
                    YouTubeSettingsPage.MAIN -> {
                        SettingRow("Quality", qualities.firstOrNull { it.isSelected }?.label ?: "Auto") { onPage(YouTubeSettingsPage.QUALITY) }
                        SettingRow("Captions", subtitleTracks.firstOrNull { it.index == selectedSubtitleIndex }?.let { localizedTrackDisplayName(it.label, it.language, it.index) } ?: "Off") { onPage(YouTubeSettingsPage.SUBTITLES) }
                        SettingRow("Audio track", audioTracks.firstOrNull { it.index == selectedAudioIndex }?.let { localizedTrackDisplayName(it.label, it.language, it.index) } ?: "Default") { onPage(YouTubeSettingsPage.AUDIO) }
                        SettingRow("Playback speed", if (playbackSpeed == 1f) "Normal" else "${playbackSpeed}x") { onPage(YouTubeSettingsPage.SPEED) }
                    }
                    YouTubeSettingsPage.QUALITY -> qualities.forEach { q -> ChoiceRow(q.label, q.isSelected) { onQuality(q.height) } }
                    YouTubeSettingsPage.AUDIO -> audioTracks.forEach { t -> ChoiceRow(localizedTrackDisplayName(t.label, t.language, t.index), t.index == selectedAudioIndex) { onAudio(t.index) } }
                    YouTubeSettingsPage.SUBTITLES -> {
                        ChoiceRow("Off", selectedSubtitleIndex < 0) { onSubtitle(-1) }
                        subtitleTracks.forEach { t -> ChoiceRow(localizedTrackDisplayName(t.label, t.language, t.index), t.index == selectedSubtitleIndex) { onSubtitle(t.index) } }
                    }
                    YouTubeSettingsPage.SPEED -> listOf(0.25f,0.5f,0.75f,1f,1.25f,1.5f,1.75f,2f).forEach { speed ->
                        ChoiceRow(if (speed == 1f) "Normal" else "${speed}x", playbackSpeed == speed) { onSpeed(speed) }
                    }
                }
            }
        }
    }
}

@Composable private fun SettingRow(label:String, value:String, onClick:()->Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable(onClick=onClick), shape=RoundedCornerShape(12.dp), color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
            Text(label); Text(value, color=MaterialTheme.colorScheme.onSurface.copy(alpha=.7f))
        }
    }
}
@Composable private fun ChoiceRow(label:String, selected:Boolean, onClick:()->Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable(onClick=onClick), shape=RoundedCornerShape(12.dp), color=if(selected) MaterialTheme.colorScheme.primary.copy(alpha=.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment=Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f)); if(selected) Icon(Icons.Rounded.Check, null)
        }
    }
}
