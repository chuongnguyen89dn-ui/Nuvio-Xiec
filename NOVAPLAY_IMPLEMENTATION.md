# NovaPlay iPhone — original Nuvio UI, independent playback pipeline

## Source of truth
The iPhone product is **iosApp/** + **composeApp/src/** from the GPL-3.0 Nuvio Mobile source in this repository. Keep their screens, navigation, metadata, add-on model, library, search, stream selection, subtitle and player controls intact. Do not replace them with the `novaplay-ios/` SwiftUI proof-of-concept or `novaplay-pwa/` HTML prototype. Those directories are experiments, not the deliverable.

## Scope
iPhone/iOS only. Do not divert effort to Android mobile/TV. No NovaPlay deployment on Render; GitHub Actions builds the app. Installing an add-on which itself runs on Render can still consume that add-on's external service resources.

## UI contract
Keep original Nuvio home catalogs, posters/backdrops, search, details metadata/cast/season/episode lists, streams, add-on manager, library, watch progress, settings and player UI. Never substitute fake poster cards or page URLs for video links. Do not claim screenshot parity from AI-generated mockups.

## Video boundary
Nuvio already constructs a PlayerLaunch with selected stream URL, sourceHeaders, sourceResponseHeaders, subtitles, content type, resume state and metadata in StreamDestination.kt. PlayerDestination.kt passes that to PlayerScreen and the iOS MPV bridge. Preserve those fields. Any custom resolver/network/player work should be confined to the stream-to-player handoff and platform playback layer, not spread across screen widgets. A missing URL is a provider status, not a reason to remove screens/fields. Avoid Render proxies.

## iOS distributions
The `appstore` build retains the Nuvio main UI and regular add-ons, but disables JS plugins and P2P. The `full` build requires a locally built `nuvio-engine/platform/apple/NuvioEngine.xcframework`, plus MPVKit; do not claim Full support from an appstore build.

## Verification
.github/workflows/novaplay-nuvio-ios.yml builds the original iOS app, boots an iPhone simulator, captures a real screenshot, and then produces an unsigned iPhone IPA. Compilation alone is not proof of actual video playback. Check all CI steps and inspect artifacts and device logs before reporting ready.

## License
Continue respecting upstream NuvioMobile GPL-3.0 and third-party licenses and preserve attribution/source availability when distributing modified builds.
