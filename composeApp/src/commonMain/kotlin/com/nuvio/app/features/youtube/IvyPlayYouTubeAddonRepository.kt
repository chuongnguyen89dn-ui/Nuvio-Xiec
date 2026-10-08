package com.nuvio.app.features.youtube

import com.nuvio.app.features.addons.ManagedAddon
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.catalog.fetchCatalogPage
import com.nuvio.app.features.catalog.mergeCatalogItems
import com.nuvio.app.features.catalog.nextCatalogPaginationState
import com.nuvio.app.features.catalog.supportsPagination
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException

object IvyPlayYouTubeAddonRepository {
    fun hasYouTubeSource(addons: List<ManagedAddon>): Boolean = addons.any { addon ->
        addon.enabled && addon.manifest?.let(::isYouTubeAddon) == true
    }

    private fun isYouTubeAddon(manifest: com.nuvio.app.features.addons.AddonManifest): Boolean {
        val manifestIdentity = (listOf(manifest.id, manifest.name) + manifest.idPrefixes)
            .joinToString(" ")
            .lowercase()
        return "youtube" in manifestIdentity ||
            "khoai" in manifestIdentity ||
            "hoa-ban" in manifestIdentity ||
            "hoaban" in manifestIdentity ||
            manifest.catalogs.any { catalog ->
                val catalogIdentity = "${catalog.id} ${catalog.name}".lowercase()
                "youtube" in catalogIdentity ||
                    "khoai" in catalogIdentity ||
                    "hoa-ban" in catalogIdentity ||
                    "hoaban" in catalogIdentity
            }
    }

    suspend fun loadChannels(addons: List<ManagedAddon>, forceRefresh: Boolean = false): List<YouTubeChannelSnapshot> = coroutineScope {
        // The YouTube profile is presentation only: derive the same addon catalog targets
        // Nuvio uses instead of maintaining a second channel/source registry.
        val sources = addons.enabledAddons()
            .flatMap { addon ->
                val manifest = addon.manifest ?: return@flatMap emptyList()
                if (!isYouTubeAddon(manifest)) return@flatMap emptyList()
                manifest.catalogs.map { catalog ->
                    NuvioYouTubeCatalogSource(
                        addon = addon,
                        manifest = manifest,
                        catalog = catalog,
                        target = CatalogTarget.Addon(
                            manifestUrl = addon.manifestUrl,
                            contentType = catalog.type,
                            catalogId = catalog.id,
                            supportsPagination = catalog.supportsPagination(),
                        ),
                    )
                }
            }

        val channels = sources.map { source ->
            val addon = source.addon
            val manifest = source.manifest
            val catalog = source.catalog
            val target = source.target
            async {
                try {
                    val firstPage = fetchCatalogPage(
                        manifestUrl = target.manifestUrl,
                        type = target.contentType,
                        catalogId = target.catalogId,
                        forceRefresh = forceRefresh,
                    )
                    var items = firstPage.items
                    var pagination = nextCatalogPaginationState(
                        supportsPagination = target.supportsPagination || firstPage.rawItemCount >= 20,
                        requestedSkip = 0,
                        page = firstPage,
                        loadedNewItems = items.isNotEmpty(),
                        consecutiveDuplicatePages = 0,
                    )
                    while (pagination.nextSkip != null) {
                        val skip = pagination.nextSkip ?: break
                        val page = try {
                            fetchCatalogPage(
                                manifestUrl = target.manifestUrl,
                                type = target.contentType,
                                catalogId = target.catalogId,
                                skip = skip,
                                forceRefresh = forceRefresh,
                            )
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Exception) {
                            // Keep the successfully loaded pages when a later page is unavailable.
                            break
                        }
                        val merged = mergeCatalogItems(items, page.items)
                        pagination = nextCatalogPaginationState(
                            supportsPagination = true,
                            requestedSkip = skip,
                            page = page,
                            loadedNewItems = merged.size > items.size,
                            consecutiveDuplicatePages = pagination.consecutiveDuplicatePages,
                        )
                        items = merged
                    }
                    val channel = YouTubeChannel(
                        channelId = "${addon.manifestUrl}|${catalog.type}|${catalog.id}",
                        name = catalog.name,
                        displayName = catalog.name,
                        avatar = manifest.logoUrl,
                    )
                    val videos = items.distinctBy { it.id }.map { item ->
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
    private data class NuvioYouTubeCatalogSource(
        val addon: ManagedAddon,
        val manifest: com.nuvio.app.features.addons.AddonManifest,
        val catalog: com.nuvio.app.features.addons.AddonCatalog,
        val target: CatalogTarget.Addon,
    )

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
