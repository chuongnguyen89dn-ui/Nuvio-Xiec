package com.nuvio.app.features.youtube

/**
 * Native host switch for the iOS YouTube profile. The existing Compose YouTube
 * surface remains the fallback on platforms without the native SmartTube host.
 */
internal expect fun publishNativeYouTubeProfileVisible(visible: Boolean)
