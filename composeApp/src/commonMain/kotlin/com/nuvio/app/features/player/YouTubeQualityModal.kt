package com.nuvio.app.features.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun YouTubeQualityModal(
    visible: Boolean,
    qualities: List<PlayerVideoQuality>,
    onSelect: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    PlayerSidePanel(visible = visible, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PlayerPanelHeader(title = "Chất lượng") {
                PlayerDialogButton(label = "Đóng", onClick = onDismiss)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                qualities.forEach { quality ->
                    AddonFilterChip(
                        label = quality.label,
                        isSelected = quality.isSelected,
                        onClick = { onSelect(quality.height) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
