package com.nuvio.app.features.player

/**
 * App-owned handoff from a selected Nuvio stream to the playback layer.
 * Keep the entire original UI/metadata and addon flow upstream of this point.
 * URL resolution and player/network policy can change here without rebuilding
 * the Home, Search, Details, Episode or Stream selection screens.
 */
data class NovaPlayVideoRequest(
    val url: String,
    val audioUrl: String?,
    val requestHeaders: Map<String, String>,
    val responseHeaders: Map<String, String>,
    val streamType: String?,
)

object NovaPlayVideoRouter {
    /**
     * Deliberate pass-through for the UI baseline. No hardcoded providers,
     * trailer substitutions, fabricated links or remote proxy.
     */
    fun route(source: NovaPlayVideoRequest): NovaPlayVideoRequest = source
}
