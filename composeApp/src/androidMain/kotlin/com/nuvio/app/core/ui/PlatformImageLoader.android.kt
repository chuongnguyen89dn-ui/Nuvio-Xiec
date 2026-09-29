package com.nuvio.app.core.ui

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import coil3.ImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder =
    apply {
        // TV boxes commonly have a much smaller memory budget than phones.
        // Keep Coil bounded so poster browsing cannot evict the player or trigger GC storms.
        memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, percent = if (context.isIvyPlayTv()) 0.08 else 0.20)
                .build()
        }
        components {
            // Animated posters cost CPU/GPU and provide little value on a 10-foot UI.
            if (!context.isIvyPlayTv()) {
                if (Build.VERSION.SDK_INT >= 28) add(AnimatedImageDecoder.Factory())
                else add(GifDecoder.Factory())
            }
        }
    }

internal fun Context.isIvyPlayTv(): Boolean =
    packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature("android.software.leanback")

internal fun Context.isIvyPlayLowRamDevice(): Boolean =
    (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
