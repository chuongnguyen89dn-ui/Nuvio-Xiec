package com.nuvio.app.features.player

data class PlayerVideoQuality(
    val height: Int?,
    val label: String,
    val isSelected: Boolean = false,
) {
    val isAuto: Boolean get() = height == null
}

internal fun youtubeQualityLabel(height: Int?): String = when {
    height == null -> "Auto"
    height >= 2160 -> "2160p"
    height >= 1440 -> "1440p"
    height >= 1080 -> "1080p"
    height >= 720 -> "720p"
    height >= 480 -> "480p"
    height >= 360 -> "360p"
    else -> height.toString() + "p"
}

internal fun normalizeVideoQualities(
    heights: List<Int>,
    selectedHeight: Int?,
): List<PlayerVideoQuality> {
    val distinct = heights.filter { it > 0 }.distinct().sortedDescending()
    return buildList {
        add(PlayerVideoQuality(null, "Auto", selectedHeight == null))
        distinct.forEach { height ->
            add(PlayerVideoQuality(height, youtubeQualityLabel(height), selectedHeight == height))
        }
    }
}
