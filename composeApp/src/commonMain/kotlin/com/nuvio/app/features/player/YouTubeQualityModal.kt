package com.nuvio.app.features.player

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
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
        ) {
            PlayerPanelHeader(title = "Chất lượng") {
                PlayerDialogButton(label = "Đóng", onClick = onDismiss)
            }
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            ) {
                qualities.forEach { quality ->
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { onSelect(quality.height) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (quality.isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = quality.label,
                                modifier = Modifier.weight(1f),
                                fontWeight = if (quality.isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                            if (quality.isSelected) {
                                Icon(Icons.Rounded.Check, contentDescription = null)
                            }
                        }
                    }
                }
            }
        }
    }
}
