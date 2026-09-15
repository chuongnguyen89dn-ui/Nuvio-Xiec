from pathlib import Path


def patch(path, old, new):
    p = Path(path)
    s = p.read_text()
    if new in s:
        print(f'already patched: {path}')
        return
    if old not in s:
        raise SystemExit(f'patch anchor not found: {path}')
    p.write_text(s.replace(old, new, 1))
    print(f'patched: {path}')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsModels.kt',
'''data class MetaPerson(\n    val name: String,\n    val role: String? = null,\n    val photo: String? = null,\n    val tmdbId: Int? = null,\n)''',
'''data class MetaPerson(\n    val name: String,\n    val role: String? = null,\n    val photo: String? = null,\n    val tmdbId: Int? = null,\n    // Nuvio-Xiec: optional addon catalog link for people not represented by TMDB.\n    val addonCatalogUrl: String? = null,\n)''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsParser.kt',
'''        }.map { link ->\n            MetaPerson(name = link.name)\n        }''',
'''        }.map { link ->\n            MetaPerson(name = link.name, addonCatalogUrl = link.url)\n        }''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/MetaDetailsParser.kt',
'''                        photo = existing.photo ?: person.photo,\n                    )''',
'''                        photo = existing.photo ?: person.photo,\n                        addonCatalogUrl = existing.addonCatalogUrl ?: person.addonCatalogUrl,\n                    )''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/details/components/DetailCastSection.kt',
'''                        onClick = if (onCastClick != null && person.tmdbId != null && person.tmdbId > 0) {\n                            { onCastClick(person, sharedTransitionKey) }\n                        } else {\n                            null\n                        },''',
'''                        onClick = if (onCastClick != null &&\n                            ((person.tmdbId != null && person.tmdbId > 0) || !person.addonCatalogUrl.isNullOrBlank())\n                        ) {\n                            { onCastClick(person, sharedTransitionKey) }\n                        } else {\n                            null\n                        },''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogTarget.kt',
'''        val catalogId: String,\n        val genre: String? = null,\n        override val supportsPagination: Boolean = false,''',
'''        val catalogId: String,\n        val genre: String? = null,\n        val actor: String? = null,\n        override val supportsPagination: Boolean = false,''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogData.kt',
'''    genre: String? = null,\n    search: String? = null,\n    skip: Int? = null,''',
'''    genre: String? = null,\n    actor: String? = null,\n    search: String? = null,\n    skip: Int? = null,''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogData.kt',
'''        genre = genre,\n        search = search,\n        skip = skip,''',
'''        genre = genre,\n        actor = actor,\n        search = search,\n        skip = skip,''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogData.kt',
'''    genre: String?,\n    search: String?,\n    skip: Int?,\n): String {''',
'''    genre: String?,\n    actor: String?,\n    search: String?,\n    skip: Int?,\n): String {''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogData.kt',
'''        if (!genre.isNullOrBlank()) add("genre=${genre.encodeCatalogExtra()}")\n        if (skip != null && skip > 0) add("skip=$skip")''',
'''        if (!genre.isNullOrBlank()) add("genre=${genre.encodeCatalogExtra()}")\n        if (!actor.isNullOrBlank()) add("actor=${actor.encodeCatalogExtra()}")\n        if (skip != null && skip > 0) add("skip=$skip")''')

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/features/catalog/CatalogRepository.kt',
'''                        catalogId = target.catalogId,\n                        genre = target.genre,\n                        skip = requestedSkip.takeIf { it > 0 },''',
'''                        catalogId = target.catalogId,\n                        genre = target.genre,\n                        actor = target.actor,\n                        skip = requestedSkip.takeIf { it > 0 },''')

p = Path('composeApp/src/commonMain/kotlin/com/nuvio/app/DetailsDestinations.kt')
s = p.read_text()
for imp in [
    'import com.nuvio.app.features.addons.AddonRepository\n',
    'import com.nuvio.app.features.catalog.CatalogTarget\n',
    'import com.nuvio.app.features.addons.enabledAddons\n',
    'import com.nuvio.app.navigation.CatalogRoute\n',
]:
    if imp not in s:
        s = s.replace('import com.nuvio.app.features.details.MetaDetailsScreen\n', imp + 'import com.nuvio.app.features.details.MetaDetailsScreen\n', 1)
p.write_text(s)

patch(
'composeApp/src/commonMain/kotlin/com/nuvio/app/DetailsDestinations.kt',
'''            if (tmdbId != null && tmdbId > 0) {\n                navController.navigate(\n                    PersonDetailRoute(''',
'''            if (tmdbId != null && tmdbId > 0) {\n                navController.navigate(\n                    PersonDetailRoute(''')

# Insert the addon fallback immediately after the native TMDB branch.
p = Path('composeApp/src/commonMain/kotlin/com/nuvio/app/DetailsDestinations.kt')
s = p.read_text()
old = '''                )\n            }\n        },\n        onCompanyClick ='''
new = '''                )\n            } else if (!person.addonCatalogUrl.isNullOrBlank()) {\n                val xiecManifest = AddonRepository.uiState.value.addons\n                    .enabledAddons()\n                    .mapNotNull { it.manifest }\n                    .firstOrNull { it.id == "community.xiec.catalog" }\n                if (xiecManifest != null) {\n                    val launchId = CatalogLaunchStore.put(\n                        CatalogLaunch(\n                            title = person.name,\n                            subtitle = "Xiec • Filmography",\n                            target = CatalogTarget.Addon(\n                                manifestUrl = xiecManifest.transportUrl,\n                                contentType = "movie",\n                                catalogId = "xiec_latest_movies",\n                                actor = person.name,\n                                supportsPagination = true,\n                            ),\n                        ),\n                    )\n                    navController.navigate(\n                        CatalogRoute(\n                            launchId = launchId,\n                            title = person.name,\n                            subtitle = "Xiec • Filmography",\n                        ),\n                    )\n                }\n            }\n        },\n        onCompanyClick ='''
if new not in s:
    if old not in s:
        raise SystemExit('fallback anchor not found: DetailsDestinations.kt')
    s = s.replace(old, new, 1)
p.write_text(s)
print('patched: DetailsDestinations.kt fallback')
