package com.nuvio.app.features.details

/**
 * App-owned metadata entity identity.
 *
 * This layer is deliberately independent from TMDB so addon/crawler metadata
 * can become navigable even when no third-party person/company ID exists.
 * Build 1 only introduces identity/model preparation; UI navigation is added
 * in a later checkpoint.
 */
enum class MetadataEntityType(val key: String) {
    ACTOR("person"),
    DIRECTOR("director"),
    WRITER("writer"),
    STUDIO("studio"),
    PRODUCER("producer"),
    GENRE("genre"),
    COUNTRY("country"),
    NETWORK("network"),
}

data class MetadataEntity(
    val id: String,
    val type: MetadataEntityType,
    val name: String,
    val source: String? = null,
    val sourceId: String? = null,
    val image: String? = null,
)

/**
 * Generates a deterministic app-owned ID.
 *
 * Priority:
 * 1. Explicit source + source ID (for example av01 actor IDs).
 * 2. TMDB/source IDs when supplied by an adapter.
 * 3. Normalized display name as a safe fallback.
 *
 * The fallback is intentionally deterministic so existing metadata that only
 * contains names is usable without requiring a crawler migration first.
 */
object MetadataEntityIdResolver {
    fun resolve(
        type: MetadataEntityType,
        name: String,
        source: String? = null,
        sourceId: String? = null,
    ): String {
        val cleanSource = source?.normalizeEntityPart()?.takeIf { it.isNotEmpty() }
        val cleanSourceId = sourceId?.trim()?.takeIf { it.isNotEmpty() }

        if (cleanSource != null && cleanSourceId != null) {
            return "${type.key}:$cleanSource:${cleanSourceId.encodeEntityPart()}"
        }

        return "${type.key}:name:${name.normalizeEntityPart().ifEmpty { "unknown" }}"
    }

    private fun String.normalizeEntityPart(): String {
        val input = trim().lowercase()
        val out = StringBuilder(input.length)
        var pendingDash = false

        for (char in input) {
            if (char.isLetterOrDigit()) {
                if (pendingDash && out.isNotEmpty()) out.append('-')
                out.append(char)
                pendingDash = false
            } else if (out.isNotEmpty()) {
                pendingDash = true
            }
        }
        return out.toString().trim('-')
    }

    private fun String.encodeEntityPart(): String =
        trim().lowercase().map { char ->
            when {
                char.isLetterOrDigit() || char == '-' || char == '_' || char == '.' -> char.toString()
                else -> "_"
            }
        }.joinToString("")
}

fun MetaPerson.toMetadataEntity(
    type: MetadataEntityType = MetadataEntityType.ACTOR,
    source: String? = tmdbId?.let { "tmdb" },
    sourceId: String? = tmdbId?.toString(),
): MetadataEntity = MetadataEntity(
    id = MetadataEntityIdResolver.resolve(type, name, source, sourceId),
    type = type,
    name = name,
    source = source,
    sourceId = sourceId,
    image = photo,
)

fun MetaCompany.toMetadataEntity(
    type: MetadataEntityType,
    source: String? = tmdbId?.let { "tmdb" },
    sourceId: String? = tmdbId?.toString(),
): MetadataEntity = MetadataEntity(
    id = MetadataEntityIdResolver.resolve(type, name, source, sourceId),
    type = type,
    name = name,
    source = source,
    sourceId = sourceId,
    image = logo,
)

fun String.toMetadataEntity(
    type: MetadataEntityType,
    source: String? = null,
    sourceId: String? = null,
): MetadataEntity = MetadataEntity(
    id = MetadataEntityIdResolver.resolve(type, this, source, sourceId),
    type = type,
    name = this,
    source = source,
    sourceId = sourceId,
)
