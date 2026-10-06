package com.nuvio.app.features.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

data class YouTubeChannel(
    val channelId: String,
    val handle: String? = null,
    val name: String,
    val displayName: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val description: String? = null,
    val subscriberCount: Long? = null,
)

enum class YouTubeSectionType { FEATURED, VIDEOS, SHORTS, LIVE, PLAYLISTS, CUSTOM }

data class YouTubeHomeSection(
    val id: String,
    val title: String,
    val type: YouTubeSectionType,
    val itemIds: List<String>,
)

data class YouTubeVideo(
    val videoId: String,
    val addonMetaId: String? = null,
    val addonType: String = "movie",
    val addonManifestUrl: String? = null,
    val title: String,
    val url: String,
    val thumbnail: String? = null,
    val durationSeconds: Long? = null,
    val uploadDate: String? = null,
    val viewCount: Long? = null,
    val channelId: String? = null,
    val channelName: String? = null,
)

data class YouTubePlaylist(
    val playlistId: String,
    val title: String,
    val thumbnail: String? = null,
    val videoIds: List<String> = emptyList(),
)

data class YouTubeChannelSnapshot(
    val channel: YouTubeChannel,
    val homeSections: List<YouTubeHomeSection>,
    val videos: List<YouTubeVideo>,
    val shorts: List<YouTubeVideo>,
    val live: List<YouTubeVideo>,
    val playlists: List<YouTubePlaylist>,
) {
    val hasAuthoritativeHomeLayout: Boolean get() = homeSections.isNotEmpty()
}

object YouTubeChannelResolver {
    private val json = Json { ignoreUnknownKeys = true }

    fun resolve(payload: String): YouTubeChannelSnapshot {
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: error("YouTube resolver payload must be a JSON object")
        val channelObject = root.obj("channel")
        val channelId = channelObject.string("channelId").orEmpty()
        val name = channelObject.string("name").orEmpty()
        require(channelId.isNotBlank()) { "YouTube resolver channelId is required" }
        require(name.isNotBlank()) { "YouTube resolver channel name is required" }

        val channel = YouTubeChannel(
            channelId = channelId,
            handle = channelObject.string("handle"),
            name = name,
            displayName = channelObject.string("displayName"),
            avatar = channelObject.string("avatar"),
            banner = channelObject.string("banner"),
            description = channelObject.string("description"),
            subscriberCount = channelObject.long("subscriberCount"),
        )

        val homeSections = root.obj("home").array("sections").mapNotNull { raw ->
            val section = raw as? JsonObject ?: return@mapNotNull null
            val id = section.string("id").orEmpty()
            if (id.isBlank()) return@mapNotNull null
            YouTubeHomeSection(
                id = id,
                title = section.string("title").orEmpty(),
                type = section.string("type").toSectionType(),
                itemIds = section.stringArray("items"),
            )
        }

        return YouTubeChannelSnapshot(
            channel = channel,
            homeSections = homeSections,
            videos = root.parseVideos("videos", channel),
            shorts = root.parseVideos("shorts", channel),
            live = root.parseVideos("live", channel),
            playlists = root.array("playlists").mapNotNull { raw ->
                val item = raw as? JsonObject ?: return@mapNotNull null
                val id = item.string("playlistId").orEmpty()
                if (id.isBlank()) return@mapNotNull null
                YouTubePlaylist(
                    playlistId = id,
                    title = item.string("title").orEmpty(),
                    thumbnail = item.string("thumbnail"),
                    videoIds = item.stringArray("videoIds"),
                )
            },
        )
    }

    private fun JsonObject.parseVideos(key: String, channel: YouTubeChannel): List<YouTubeVideo> =
        array(key).mapNotNull { raw ->
            val item = raw as? JsonObject ?: return@mapNotNull null
            val id = item.string("videoId").orEmpty()
            if (id.isBlank()) return@mapNotNull null
            YouTubeVideo(
                videoId = id,
                addonMetaId = item.string("addonMetaId") ?: item.string("metaId") ?: item.string("id"),
                title = item.string("title").orEmpty(),
                url = item.string("url") ?: "https://www.youtube.com/watch?v=$id",
                thumbnail = item.string("thumbnail"),
                durationSeconds = item.long("duration"),
                uploadDate = item.string("uploadDate"),
                viewCount = item.long("viewCount"),
                channelId = item.string("channelId") ?: channel.channelId,
                channelName = item.string("channelName") ?: item.string("channel") ?: channel.displayName ?: channel.name,
            )
        }

    private fun JsonObject.obj(name: String): JsonObject = this[name] as? JsonObject ?: JsonObject(emptyMap())
    private fun JsonObject.array(name: String): JsonArray = this[name] as? JsonArray ?: JsonArray(emptyList())
    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()
    private fun JsonObject.stringArray(name: String): List<String> =
        array(name).mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }

    private fun String?.toSectionType(): YouTubeSectionType = when (this?.lowercase()) {
        "featured" -> YouTubeSectionType.FEATURED
        "videos" -> YouTubeSectionType.VIDEOS
        "shorts" -> YouTubeSectionType.SHORTS
        "live" -> YouTubeSectionType.LIVE
        "playlists" -> YouTubeSectionType.PLAYLISTS
        else -> YouTubeSectionType.CUSTOM
    }
}
