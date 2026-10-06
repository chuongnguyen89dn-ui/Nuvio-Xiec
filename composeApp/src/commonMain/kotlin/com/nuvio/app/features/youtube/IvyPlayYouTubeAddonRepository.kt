package com.nuvio.app.features.youtube

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.fetchCatalogPage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException

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

        val channels = sources.map { (addon, manifest, catalog) ->
            async {
                try {
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
                    val videos = page.items.distinctBy { it.id }.map { item ->
                        YouTubeVideo(
                            videoId = item.id,
                            addonType = catalog.type,
                            addonMetaId = item.id,
                            addonManifestUrl = addon.manifestUrl,
                            title = item.name,
                            url = "",
                            thumbnail = item.poster ?: item.banner,
                            uploadDate = item.rawReleaseDate ?: item.releaseInfo,
                            channelId = channel.channelId,
                            channelName = channel.displayName ?: channel.name,
                        )
                    }
                    val sectionType = catalogYouTubeSectionType(catalog.id, catalog.name)
                    YouTubeChannelSnapshot(
                        channel = channel,
                        homeSections = listOf(
                            YouTubeHomeSection(
                                id = "${catalog.id}:${sectionType.name.lowercase()}",
                                title = catalog.name,
                                type = sectionType,
                                itemIds = videos.map(YouTubeVideo::videoId),
                            ),
                        ),
                        videos = if (sectionType == YouTubeSectionType.VIDEOS || sectionType == YouTubeSectionType.FEATURED || sectionType == YouTubeSectionType.CUSTOM) videos else emptyList(),
                        shorts = if (sectionType == YouTubeSectionType.SHORTS) videos else emptyList(),
                        live = if (sectionType == YouTubeSectionType.LIVE) videos else emptyList(),
                        playlists = emptyList(),
                    )
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Exception) {
                    // One unavailable catalog must not blank the whole YouTube profile.
                    null
                }
            }
        }.awaitAll().filterNotNull()
        // Preserve partial results, but distinguish total network failure from an empty catalog.
        if (sources.isNotEmpty() && channels.isEmpty()) {
            error("All YouTube catalogs failed to load")
        }
        channels
    }
    private fun catalogYouTubeSectionType(catalogId: String, catalogName: String): YouTubeSectionType {
        val identity = "$catalogId $catalogName".lowercase()
        return when {
            "short" in identity -> YouTubeSectionType.SHORTS
            "live" in identity || "trực tiếp" in identity -> YouTubeSectionType.LIVE
            "featured" in identity || "nổi bật" in identity -> YouTubeSectionType.FEATURED
            else -> YouTubeSectionType.VIDEOS
        }
    }

}
