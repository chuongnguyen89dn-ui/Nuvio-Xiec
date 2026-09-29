# NovaPlay iPhone native v0.2

Native iOS prototype (SwiftUI + AVPlayer). All traffic is either from iPhone directly to sources or over an on-device loopback HTTP server; there is no Render backend. This is intentionally isolated from the old Nuvio-Xiec source.

Provider code:
- XemXiec: GitHub Raw movie dataset, only main #1/#2 (never trailer).
- PhimHD: known HLS in prior browser playback verification; URLs may expire.
- MissAV: on-device HLS proxy applies Referer/Origin to playlist, keys, maps, and segments.
- IkiSoda: attempts to obtain a fresh source-page get_file 1080p redirect with on-device URLSession, rejecting expired saved tokens; if no source_page is in the dataset, fails explicitly.
- TLS/DRM/cookies/changes on upstream remain real limitations. No bypass of access rights.
- Playback logs include upstream status per path but redact signed query strings.

CI builds an UNSIGNED iOS IPA on macOS. It cannot be installed on a stock iPhone without user-provided code signing; use an authorized signing method. Unsigned compilation does not demonstrate playback on a physical device.
