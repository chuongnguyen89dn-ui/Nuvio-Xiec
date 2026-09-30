package com.nuvio.app.features.profiles

/**
 * IvyPlay profile-level content mode.
 * STANDARD preserves the existing movie/series/add-on experience.
 * YOUTUBE enables the isolated YouTube catalog/presentation pipeline.
 */
enum class IvyPlayContentMode {
    STANDARD,
    YOUTUBE;

    companion object {
        fun fromStorage(value: String?): IvyPlayContentMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: STANDARD
    }
}

enum class IvyPlaySourceType {
    YOUTUBE_VIDEO,
    YOUTUBE_PLAYLIST,
    YOUTUBE_CHANNEL,
    YOUTUBE_SHORT,
    HLS,
    DASH,
    DIRECT,
    STREMIO,
    UNKNOWN
}

data class IvyPlaySourceDescriptor(
    val type: IvyPlaySourceType,
    val originalUrl: String,
    val youtubeVideoId: String? = null,
    val youtubePlaylistId: String? = null,
    val youtubeChannelHandle: String? = null
)

object IvyPlaySourceClassifier {
    fun classify(url: String, declaredType: String? = null): IvyPlaySourceDescriptor {
        val raw = url.trim()
        val declared = declaredType?.trim()?.lowercase()
        if (declared == "youtube") return classifyYouTube(raw)
        if (declared == "hls") return IvyPlaySourceDescriptor(IvyPlaySourceType.HLS, raw)
        if (declared == "dash") return IvyPlaySourceDescriptor(IvyPlaySourceType.DASH, raw)
        if (declared == "direct") return IvyPlaySourceDescriptor(IvyPlaySourceType.DIRECT, raw)
        if (declared == "stremio") return IvyPlaySourceDescriptor(IvyPlaySourceType.STREMIO, raw)

        val lower = raw.lowercase()
        return when {
            isYouTubeUrl(lower) -> classifyYouTube(raw)
            lower.substringBefore('?').endsWith(".m3u8") -> IvyPlaySourceDescriptor(IvyPlaySourceType.HLS, raw)
            lower.substringBefore('?').endsWith(".mpd") -> IvyPlaySourceDescriptor(IvyPlaySourceType.DASH, raw)
            else -> IvyPlaySourceDescriptor(IvyPlaySourceType.UNKNOWN, raw)
        }
    }

    private fun isYouTubeUrl(value: String): Boolean =
        value.contains("youtube.com/") || value.contains("youtu.be/")

    private fun classifyYouTube(url: String): IvyPlaySourceDescriptor {
        val lower = url.lowercase()
        val playlistId = queryValue(url, "list")
        if (lower.contains("/playlist") || (playlistId != null && !lower.contains("/watch"))) {
            return IvyPlaySourceDescriptor(IvyPlaySourceType.YOUTUBE_PLAYLIST, url, youtubePlaylistId = playlistId)
        }
        if (lower.contains("/shorts/")) {
            return IvyPlaySourceDescriptor(
                IvyPlaySourceType.YOUTUBE_SHORT,
                url,
                youtubeVideoId = pathValue(url, "/shorts/")
            )
        }
        if (lower.contains("youtu.be/")) {
            return IvyPlaySourceDescriptor(
                IvyPlaySourceType.YOUTUBE_VIDEO,
                url,
                youtubeVideoId = url.substringAfter("youtu.be/").substringBefore('?').substringBefore('/').takeIf { it.isNotBlank() },
                youtubePlaylistId = playlistId
            )
        }
        if (lower.contains("/watch")) {
            return IvyPlaySourceDescriptor(
                IvyPlaySourceType.YOUTUBE_VIDEO,
                url,
                youtubeVideoId = queryValue(url, "v"),
                youtubePlaylistId = playlistId
            )
        }
        if (lower.contains("/@")) {
            return IvyPlaySourceDescriptor(
                IvyPlaySourceType.YOUTUBE_CHANNEL,
                url,
                youtubeChannelHandle = url.substringAfter("/@").substringBefore('/').substringBefore('?').takeIf { it.isNotBlank() }
            )
        }
        return IvyPlaySourceDescriptor(IvyPlaySourceType.YOUTUBE_CHANNEL, url)
    }

    private fun pathValue(url: String, marker: String): String? =
        url.substringAfter(marker, "").substringBefore('/').substringBefore('?').takeIf { it.isNotBlank() }

    private fun queryValue(url: String, key: String): String? {
        val query = url.substringAfter('?', "")
        return query.split('&')
            .mapNotNull { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size == 2 && pieces[0].equals(key, ignoreCase = true)) pieces[1] else null
            }
            .firstOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}
