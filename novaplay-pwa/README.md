# NovaPlay iPhone — PWA prototype v0.1

A separate static web prototype, isolated from the Nuvio-Xiec application source. Open `index.html` on an HTTPS static host. This is NOT a modified Nuvio fork or a native IPA.

Features: iPhone-friendly interface, direct Stremio catalog/meta/stream API calls, add-on manifest storage on device, search when supported by catalog, Safari native HTTPS video player, public HLS test stream. No custom backend or proxy, no Render dependency in this application.

Limitations: an add-on hosted on Render still consumes its own Render quota when selected. Browser CORS, cookies, protected streams and HTTP restrictions may prevent playback. Does not bypass DRM or authentication. Tested only at code/static-source level, not yet on physical iPhone. GitHub Pages needs a site configuration with this folder as root or a deployment workflow. Do not claim a published website before Pages is enabled and verified.

Roadmap: move to a dedicated NovaPlay repository, enable Pages, add app icons, instrument stream diagnostics and verify Safari playback. Do not change the existing Nuvio-Xiec code.