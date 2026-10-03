package com.nuvio.app.features.youtube

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object IvyPlayYouTubeFeedRepository {
    private val client = HttpClient()

    private val defaultChannels = listOf(
        DefaultChannel(
            channelId = "UCZE88kYvCKUKjM-G0uc8Duw",
            handle = "@khoailangthang",
            name = "Khoai Lang Thang",
        ),
        DefaultChannel(
            channelId = "UCBhgBmuPFbLLxnejr09lnAQ",
            handle = "@hoabanfood",
            name = "HOA BAN FOOD",
        ),
    )

    suspend fun loadDefaultChannels(): List<YouTubeChannelSnapshot> = coroutineScope {
        defaultChannels.map { channel ->
            async { runCatching { loadChannel(channel) }.getOrNull() }
        }.mapNotNull { it.await() }
    }

    private suspend fun loadChannel(source: DefaultChannel): YouTubeChannelSnapshot {
        val response: HttpResponse = client.get(
            "https://www.youtube.com/feeds/videos.xml?channel_id=${source.channelId}",
        )
        val xml: String = response.body()
        val videos = parseEntries(xml, source)
        return YouTubeChannelSnapshot(
            channel = YouTubeChannel(
                channelId = source.channelId,
                handle = source.handle,
                name = source.name,
                displayName = source.name,
            ),
            homeSections = listOf(
                YouTubeHomeSection(
                    id = "${source.channelId}:uploads",
                    title = "Latest",
                    type = YouTubeSectionType.VIDEOS,
                    itemIds = videos.map { it.videoId },
                ),
            ),
            videos = videos,
            shorts = emptyList(),
            live = emptyList(),
            playlists = emptyList(),
        )
    }

    private fun parseEntries(xml: String, source: DefaultChannel): List<YouTubeVideo> =
        ENTRY_REGEX.findAll(xml).mapNotNull { match ->
            val entry = match.groupValues[1]
            val videoId = entry.tagValue("yt:videoId") ?: return@mapNotNull null
            val title = entry.tagValue("media:title") ?: entry.tagValue("title") ?: "YouTube video"
            val thumbnail = THUMBNAIL_REGEX.find(entry)?.groupValues?.getOrNull(1)
                ?.decodeXmlEntities()
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            val published = entry.tagValue("published")?.substringBefore('T')
            val views = VIEWS_REGEX.find(entry)?.groupValues?.getOrNull(1)?.toLongOrNull()
            YouTubeVideo(
                videoId = videoId,
                title = title.decodeXmlEntities(),
                url = "https://www.youtube.com/watch?v=$videoId",
                thumbnail = thumbnail,
                uploadDate = published,
                viewCount = views,
                channelId = source.channelId,
                channelName = source.name,
            )
        }.toList()

    private fun String.tagValue(tag: String): String? {
        val escapedTag = Regex.escape(tag)
        return Regex("<$escapedTag>(.*?)</$escapedTag>", setOf(RegexOption.DOT_MATCHES_ALL))
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
    }

    private fun String.decodeXmlEntities(): String = this
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private data class DefaultChannel(
        val channelId: String,
        val handle: String,
        val name: String,
    )

    private val ENTRY_REGEX = Regex("<entry>(.*?)</entry>", setOf(RegexOption.DOT_MATCHES_ALL))
    private val THUMBNAIL_REGEX = Regex("<media:thumbnail[^>]*url=\"([^\"]+)\"")
    private val VIEWS_REGEX = Regex("<media:statistics[^>]*views=\"(\\d+)\"")
}
