package com.nuvio.app.features.youtube

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults

private const val nativeYouTubeProfileVisibleKey = "NuvioNativeYouTubeProfileVisible"
private const val nativeYouTubeProfileVisibilityDidChange = "NuvioNativeYouTubeProfileVisibilityDidChange"

internal actual fun publishNativeYouTubeProfileVisible(visible: Boolean) {
    NSUserDefaults.standardUserDefaults.setBool(visible, forKey = nativeYouTubeProfileVisibleKey)
    NSNotificationCenter.defaultCenter.postNotificationName(
        aName = nativeYouTubeProfileVisibilityDidChange,
        `object` = null,
    )
}
