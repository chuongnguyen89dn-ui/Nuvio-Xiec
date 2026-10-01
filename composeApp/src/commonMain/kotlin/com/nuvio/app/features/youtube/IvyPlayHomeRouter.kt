package com.nuvio.app.features.youtube

import com.nuvio.app.features.profiles.IvyPlayContentMode

/**
 * Keeps the existing IvyPlay home untouched for STANDARD profiles while giving
 * YOUTUBE profiles an isolated destination and resolver pipeline.
 */
enum class IvyPlayHomeDestination {
    STANDARD,
    YOUTUBE,
}

object IvyPlayHomeRouter {
    fun destination(contentMode: IvyPlayContentMode): IvyPlayHomeDestination =
        when (contentMode) {
            IvyPlayContentMode.STANDARD -> IvyPlayHomeDestination.STANDARD
            IvyPlayContentMode.YOUTUBE -> IvyPlayHomeDestination.YOUTUBE
        }
}
