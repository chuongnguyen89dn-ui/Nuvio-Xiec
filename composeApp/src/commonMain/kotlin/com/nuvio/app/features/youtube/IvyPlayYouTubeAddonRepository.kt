package com.nuvio.app.features.youtube

import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.fetchCatalogPage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object IvyPlayYouTubeAddonRepository {
    suspend fun loadChannels(): List<YouTubeChannelSnapshot> = coroutineScope {
        AddonRepository.initialize()
        AddonRepository.awaitManifestsLoaded()
        val sources = AddonRepository.uiState.value.addons.enabledAddons()
            .flatMap { addon ->
                val manifest = addon.manifest ?: return@flatMap emptyList()
                manifest.catalogs
                    .filter { catalog -> catalog.type == "movie" }
                    .map { catalog -> Triple(addon, manifest, catalog) }
            }
            .filter { (_, manifest, catalog) ->
                val haystack = listOf(manifest.id, manifest.name, catalog.id, catalog.name, manifest.idPrefixes.joinToString(" ")).joinToString(" ").lowercase()
                "youtube" in haystack || "khoai" in haystack || "hoa-ban" in haystack || "hoaban" in haystack
            }

        sources.map { (addon, manifest, catalog) ->
            async {
                runCatching {
                    val page = fetchCatalogPage(
                        manifestUrl = addon.manifestUrl,
                        type = catalog.type,
                        catalogId = catalog.id,
                        maxItems = 2000,
                    )
                    val channel = YouTubeChannel(
                        channelId = catalog.id,
                        name = catalog.name,
                        displayName = catalog.name,
                        avatar = manifest.logoUrl,
                    )
                    val videos = page.items.map { item ->
                        YouTubeVideo(
                            videoId = item.id.substringAfterLast('_'),
                            addonMetaId = item.id,
                            title = item.name,
                            url = "",
                            thumbnail = item.poster ?: item.banner,
                            uploadDate = item.rawReleaseDate ?: item.releaseInfo,
                            channelId = channel.channelId,
                            channelName = channel.displayName ?: channel.name,
                        )
                    }
                    YouTubeChannelSnapshot(
                        channel = channel,
                        homeSections = listOf(
                            YouTubeHomeSection(
                                id = "${catalog.id}:videos",
                                title = "Videos",
                                type = YouTubeSectionType.VIDEOS,
                                itemIds = videos.map(YouTubeVideo::videoId),
                            ),
                        ),
                        videos = videos,
                        shorts = emptyList(),
                        live = emptyList(),
                        playlists = emptyList(),
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().filter { it.videos.isNotEmpty() }
    }
}
