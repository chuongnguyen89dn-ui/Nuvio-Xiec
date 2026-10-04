package com.nuvio.app.features.youtube

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.fetchCatalogPage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

object IvyPlayYouTubeAddonRepository {
    fun hasYouTubeSource(addons: List<ManagedAddon>): Boolean = addons.any { addon ->
        addon.enabled && addon.manifest?.let { manifest ->
            manifest.catalogs.any { catalog ->
                isYouTubeCatalog(manifest.id, manifest.name, manifest.idPrefixes, catalog.id, catalog.name)
            }
        } == true
    }

    private fun isYouTubeCatalog(id: String, name: String, prefixes: List<String>, catalogId: String, catalogName: String): Boolean {
        val identity = (listOf(id, name, catalogId, catalogName) + prefixes).joinToString(" ").lowercase()
        return "youtube" in identity || "khoai" in identity || "hoa-ban" in identity || "hoaban" in identity
    }

    suspend fun loadChannels(addons: List<ManagedAddon>, forceRefresh: Boolean = false): List<YouTubeChannelSnapshot> = coroutineScope {
        val sources = addons.enabledAddons()
            .flatMap { addon ->
                val manifest = addon.manifest ?: return@flatMap emptyList()
                manifest.catalogs
                    .map { catalog -> Triple(addon, manifest, catalog) }
            }
            .filter { (_, manifest, catalog) ->
                isYouTubeCatalog(manifest.id, manifest.name, manifest.idPrefixes, catalog.id, catalog.name)
            }

        sources.map { (addon, manifest, catalog) ->
            async {
                run {
                    val page = fetchCatalogPage(
                        manifestUrl = addon.manifestUrl,
                        type = catalog.type,
                        catalogId = catalog.id,
                        forceRefresh = forceRefresh,
                    )
                    val channel = YouTubeChannel(
                        channelId = "${addon.manifestUrl}|${catalog.type}|${catalog.id}",
                        name = catalog.name,
                        displayName = catalog.name,
                        avatar = manifest.logoUrl,
                    )
                    val videos = page.items.map { item ->
                        YouTubeVideo(
                            videoId = item.id,
                            addonType = catalog.type,
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
                }
            }
        }.awaitAll()
    }
}
