package com.nuvio.app.features.youtube

import com.nuvio.app.features.profiles.IvyPlayContentMode

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
