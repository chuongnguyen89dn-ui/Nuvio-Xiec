# Nuvio-Xiec — Work Handoff / Source of Truth

Updated: 2026-10-04
Branch: `main`
Repository: `chuongnguyen89dn-ui/Nuvio-Xiec`

## 1. Project identity — DO NOT MIX APPS

This repository/workstream is now **NUVIO**.

- Keep and continue the current Nuvio iOS app/source (`composeApp` + `iosApp`).
- Current Nuvio iOS bundle identity previously observed: `com.nuvio.app`.
- Do **not** treat an IPA as IvyPlay merely because a workflow/artifact filename says IvyPlay.
- Do **not** restore or reintroduce IvyPlay or the obsolete NovaPlay prototype/PWA into this workstream.
- Before handing off any IPA, verify the built `.app`/bundle identity is Nuvio and report the actual commit/run/artifact.

## 2. Cleanup already decided

Old/unrelated workflows have been removed so they cannot be confused with the active Nuvio iOS build.

Known removed during cleanup:
- `.github/workflows/ivyplay-tv-android.yml`
- `.github/workflows/xiec-actor-build.yml`
- `.github/workflows/fix-ios-compat-once.yml`
- `.github/workflows/novaplay-pages.yml`
- `.github/workflows/import-upstream.yml`
- `.github/workflows/novaplay-ios.yml`

The active Nuvio iOS workflow to preserve is/was:
- `.github/workflows/novaplay-nuvio-ios.yml`

Its historical name contains `novaplay`, but it builds the Nuvio `iosApp`. Do not confuse the workflow filename with app identity. It may be renamed later if useful, but do not break the active build merely for naming cleanup.

## 3. Preserve existing Nuvio work

Do NOT restart from upstream and do NOT discard the current Nuvio source.

The current Nuvio contains AV01/player-related modifications made during this work. Preserve them while diagnosing the remaining playback failure. Do not blame the AV01 failure on the earlier IvyPlay/Nuvio naming confusion: the AV01 changes were already present in the Nuvio build that was tested and AV01 still failed.

## 4. Highest-priority current task: YouTube UI inside Nuvio

The current YouTube-looking screen is NOT accepted as complete.

Observed failures from the installed Nuvio build:
- YouTube profile/UI appears even when the user has not added the relevant addon.
- It showed channel names such as `Khoai Lang Thang` and `HÒA BAN FOOD` before the addon was installed.
- No visible control on that YouTube UI responded to taps in the user's test.
- Channel/profile items could not be opened.
- The user could not navigate back to Nuvio; the screen effectively trapped the user.
- A screenshot/render that resembles YouTube is not proof that the feature works.

### Required behavior

YouTube must be an operational route/screen **inside Nuvio**, styled closely to the original YouTube mobile experience where appropriate, while remaining Nuvio functionality.

Required flow:

`Nuvio -> installed addon -> addon-provided YouTube profile/channel data -> YouTube-mobile-style UI -> selectable channel/video -> Nuvio playback -> Back/Close returns to Nuvio`

Rules:
- **No addon/profile source installed = no addon channel/video data should magically appear.**
- Do not hard-code sample channels to make the screen look populated.
- Remove/avoid stale/demo/fallback channel state that creates fake content.
- Touch/hit-testing must actually work. Inspect overlays/containers/pointer handling/navigation instead of only changing visuals.
- Channel/video/profile elements that look interactive must have real actions/routes.
- Provide a reliable Back/Close path to the parent Nuvio UI.
- Do not declare this fixed merely because CI builds or a screenshot looks correct.
- Inspect the actual Compose/iOS navigation/data flow and make the smallest correct architectural fix.

## 5. AV01 playback — OPEN BUG, NOT FIXED

**Critical status: AV01 is still NOT playable in the tested Nuvio iOS build.** Do not report the existing AV01 commits as a successful fix. Two Nuvio-side fix attempts were already made and the on-device result still failed.

### 5.1 Known-good reference: VLC/local playback

Reference test video: **AV01 video `221293`**.

A working local/VLC flow was demonstrated and must be treated as the behavioral reference instead of inventing a new resolver path.

Known-good sequence:

1. Fetch `https://files.iw01.xyz/edge/geo.js?json` using the AV01-compatible `User-Agent` and `Referer`.
2. Read the returned `token_v2`, expiry and observed public IP.
3. Call AV01 CDN-access for the selected video: `https://customers.iw01.xyz/api/v1/videos/<id>/cdn-access` with the geo/token parameters.
4. Fetch the AV01 HLS rendition/manifest; the proven test used the 1080p-style rendition `index90-sv3-v1-a1.m3u8`.
5. Rewrite `iw01.xyz` HLS URLs and `URI="..."` attributes so the fresh `access_token` is attached to the media/init/segment requests.
6. VLC receives the playlist and then requests the signed CDN media directly. The local helper does **not** proxy every media segment.
7. VLC was supplied AV01-compatible HTTP identity, including `User-Agent` and `Referer: https://www.av01.media/`.

The local design resolves the signed playlist **when Play is selected**, not hours earlier. This matters because the token expires and is tied to the network identity used to obtain it.

The successful probe for video `221293` reached AV01/geo endpoints with HTTP 200, and the resolver's signed media/segment probe also returned HTTP 200. VLC playback was reported fast/stable. This is the known-good path.

### 5.2 Critical discovery: token/IP binding

AV01 CDN access is not a portable static token. `cdn-access` binds the JWT/access token to the **observed public IP**; supplying an arbitrary requested IP does not make the token transferable.

During the successful reference flow, the observed geo IP and JWT IP matched (`118.69.28.122` in the captured test). Therefore a token minted by Render/server cannot be assumed to work on an iPhone using a different public IP.

Consequence: the correct Nuvio architecture is **client-direct token acquisition on the iPhone/network that will fetch the media**, or an equivalent same-egress design. Do not return to a design where Render obtains a token and the iPhone then tries to use that token from another IP.

The local cross-device helper also required PC/iPhone to be on the same Wi-Fi/public egress for this reason.

### 5.3 Nuvio fix attempt #1 — already tried, still not sufficient

Commit: `3f59e0c8554b01c36f891d99df0f74f1d50526e9`

Historical commit title contains `ivyplay-ios`, but the work belongs to the Nuvio iOS code path; do not use the old naming to split the project again.

Change made in `iosApp/iosApp/Player/AV01DirectResolver.swift`:
- extended `AV01ResolvedPlayback` with `requestHeaders`;
- returned the resolver's AV01 `User-Agent`;
- returned `Referer: https://www.av01.media/`.

Reason for the attempt: the local/VLC resolver could obtain a working signed playlist/media probe, but the player path was not necessarily using the same HTTP identity for the subsequent HLS requests.

**Result: this alone did not constitute a runtime fix.**

### 5.4 Nuvio fix attempt #2 — already tried, on-device playback still failed

Commit: `0242a63be11aa0867d2d644c9cecea25102a6164`

Change made in `iosApp/iosApp/Player/MPVPlayerBridge.swift`:
- merge `AV01ResolvedPlayback.requestHeaders` into the player request headers;
- replace case-insensitive duplicate keys;
- pass the merged headers to `player.loadFile(...)` for the local AV01 playlist.

This was specifically intended to make MPV use the same AV01 UA/Referer identity as the resolver/VLC path.

**Observed result after these fixes: AV01 still did not play on the user's iPhone/Nuvio build.** The player/FFmpeg path reached errors equivalent to:
- `error reading header`
- `avformat_open_input() failed`
- `unrecognized file format`

Therefore **DO NOT make a third blind UA/Referer patch**. Both propagation steps already exist and have failed to solve the complete runtime problem.

### 5.5 Current AV01 diagnosis plan — evidence before code change

The next AV01 task is to locate the **first concrete divergence between Nuvio and the known-good VLC/local flow**.

Instrument the Nuvio AV01 path on the actual client side and record, without exposing full reusable secrets in logs:

- geo request status and observed public IP;
- cdn-access status;
- token expiry and a safe token fingerprint/short prefix only;
- master/rendition playlist URL and redirect chain;
- HTTP status and `Content-Type` for playlist fetches;
- rewritten media/init/segment URL host/path and whether `access_token` is present;
- HTTP status and `Content-Type` for at least the first init/media object;
- `User-Agent`, `Referer`, `Origin` and any required cookie/header names actually applied by MPV;
- whether MPV is reading the intended local rewritten playlist or accidentally opening an HTML/error response;
- enough response metadata/body prefix to distinguish HLS/media from HTML/anti-bot/error output;
- compare the public IP used when the token is minted with the public IP used for the CDN object request.

Compare those checkpoints in order against the proven VLC flow:

`geo.js -> cdn-access -> fresh access_token -> master/rendition HLS -> rewritten signed init/media URLs -> CDN object HTTP 200 -> decoder`

**Fix only the first failing/different checkpoint.** Preserve the current AV01 resolver/player work unless evidence proves a specific piece is wrong.

Do not use `/av01/play/<id>.m3u8` through a remote Render token-minting path as proof of correctness if the media fetch leaves from a different public IP. The successful local helper's `/play/<id>.m3u8` behavior was valid because resolution happened on the same public egress used by the client test.

### 5.6 AV01 acceptance criteria

AV01 is fixed only when all of the following are true:
- Nuvio itself obtains/uses a token valid for the same client/network path that fetches media;
- first init/media request is a real media response, not HTML/error content;
- MPV opens the HLS successfully without the header/format errors above;
- video `221293` actually plays on the user's iPhone in Nuvio;
- a fresh Play after token expiry can resolve a new token and play again.

A successful CI build, successful resolver log, successful playlist fetch, or VLC-only playback **does not by itself close AV01**.

## 6. Build/CI acceptance criteria

For future iOS builds:

1. Build the actual Nuvio `iosApp` on `main`.
2. Follow the workflow/run until completion; a queued/running build is not a successful handoff.
3. If compilation fails, inspect the concrete compiler/build error and make the minimal source fix, then allow the push to trigger the next run.
4. On success, verify the IPA artifact actually exists.
5. Verify the packaged app identity is Nuvio, not an artifact that was merely renamed.
6. Report commit SHA, run number/ID and artifact/IPA details when handing it off.

## 7. Working rules for ChatGPT Work

- Read this file first whenever resuming the project.
- Treat this file plus current repository state/history as the project source of truth.
- Inspect existing code and recent commits before modifying anything; do not recreate functionality that already exists.
- Keep IvyPlay/NovaPlay separate from this Nuvio workstream.
- Do not overwrite current Nuvio with an old upstream import.
- Do not delete current AV01 work while fixing YouTube UI.
- Prefer minimal, evidence-based changes.
- Continue through CI/build failures when the error is actionable instead of stopping after the first failed run.
- Never claim an interaction works solely from a successful compile or screenshot. Where device-only behavior cannot be directly verified, state exactly what was verified in code/CI and what still requires on-device confirmation.

## 8. Immediate next action

First finish/accept the current YouTube interaction work on-device. Then continue the AV01 diagnosis using section 5 above. Do not repeat the two existing header-only fixes.

For YouTube, verify:
1. no addon -> no fake channels;
2. install/enable addon -> addon data appears;
3. touch/channel/video actions work;
4. player route works;
5. Back/Close returns to Nuvio;
6. disable/remove addon and profile switching clear/update state correctly.

For AV01, instrument the real iPhone/Nuvio request chain and find the first checkpoint that differs from the proven VLC/local `221293` flow.

## 9. Work continuation — 2026-10-04, YouTube interaction fix

Source commit: `436662ac590028398dd632fdbdf449fc910ae597`.
iOS run: #125.
Status: run #125 completed successfully at 2026-10-04 13:08 UTC. Compilation/package verification passed; device interaction acceptance remains pending.

Audit findings:
- Mobile and TV empty states hard-coded two channel names; the unused RSS repository also contained default channels.
- Mobile top/bottom controls were decorative icons; channel callback was never supplied by MainAppContent.
- Home route loaded once and only replaced data when non-empty, so addon changes/removal were not observed.
- YouTube did not notify the shell that Home content had rendered. iOS gate readiness depended on a timeout fallback.
- Nuvio navigation bars were suppressed for the entire YouTube profile, including outside its Home screen.

Implemented:
- Removed demo channel placeholders and the unused default RSS source.
- Observe installed addons and active profile; immediately clear channel/selection state when sources change.
- Render loading, no-addon, empty-content and failure states with retry/addon management.
- Use the existing channel screen with a real selection/back handler and existing Nuvio StreamRoute for video playback.
- Preserve complete addon media IDs and catalog content type for stream lookup.
- Operational Home/Shorts/Channels tabs and local search of loaded addon videos; removed unsupported decorative actions.
- Back/Nuvio opens Nuvio Settings/Profile; standard navigation is available outside YouTube Home.
- Signal content readiness even for loading/empty/error UI so touch does not wait on network.
- The active workflow filename stays unchanged; its run/artifact display names now say Nuvio.
- Before packaging, CI requires the built bundle ID to be `com.nuvio.app` and includes `Nuvio-build-identity.txt` alongside `Nuvio-unsigned.ipa`.

Validation/limits:
- Source diff checked; AV01 resolver/player files unchanged by this YouTube work.
- Local Gradle could not download its distribution because the execution environment could not reach services.gradle.org.
- Device acceptance still required: no-addon state, install/disable/remove addon, tap channel/video, player return, close to Nuvio, switch profile.
- Search currently covers loaded addon catalog data. Existing catalog adapter supplies videos; Shorts is empty unless actual Shorts data is supplied.
- Do not claim AV01 fixed or device interactions verified from CI.

### Run #125 verified result

- Source: `436662ac590028398dd632fdbdf449fc910ae597`; workflow result: SUCCESS.
- Build log confirms `BUILD SUCCEEDED`, product `Nuvio.app`, target `iosApp`, device arm64, bundle `com.nuvio.app`.
- The packaging step successfully checked the built Info.plist bundle ID before copying that app into Payload.
- Artifact: `Nuvio-iPhone-unsigned`, ID `11304570963`, 59,774,258 bytes; includes `Nuvio-unsigned.ipa` and `Nuvio-build-identity.txt`.
- Artifact SHA-256 reported by GitHub: `aa766654419309b2b6dce4a8476b08b710bdf4e7621f6317b837f7128acd4b81`.
- CI/bundle identity verified from completed workflow/logs. Local archive inspection was unavailable because the artifact download URL returned HTTP 403 in the execution environment; do not claim local IPA extraction.
- Next acceptance step is testing the new IPA on the user's iPhone: no addon, install addon, channel/video taps, player Back, Nuvio Close and addon removal/profile switch. AV01 diagnosis remains open and must follow section 5.

## 10. Profile YouTube audit — 2026-10-06

Continues “Kiểm tra log render nuvio”, from main `53d8dace`.

Concrete source fixes:
- Home route now observes addon state with collectLatest after initial hydration. Previously, addon changes recreated remembered screen state without restarting the loading effect, which could leave the visible state empty while the old coroutine wrote into discarded state. Source changes now cancel obsolete catalog loads and reset channel/playlist selections.
- The source indicator uses current addons, not a saved manifest snapshot. Retry also refreshes failed enabled manifests.
- Total catalog request failure now reaches the retry UI; partial catalog successes remain usable. Duplicate IDs within a catalog are removed before producing keyed channel video rows.
- Stream request reuse now includes the preferred addon URL. Explicit addon launches bypass the unscoped embedded-stream cache so an equal media ID from another source cannot override the requested source.

Validation:
- Reviewed source routing through MainAppContent -> StreamLaunch -> StreamsScreen load/retry -> StreamsRepository.
- git diff --check passed. Local Gradle compilation could not start: services.gradle.org was unreachable while downloading Gradle 9.4.1. CI and physical iPhone validation remain required; no playback success is claimed.
- SaveTube/resolver selection and AV01/native player source were not changed.

Remaining observed gaps (not fixed by this patch):
- YouTube addon adapter loads only the first catalog page; pagination is not yet connected.
- Playlists are currently constructed as empty; related-channel/Series data are not mapped.
- Home notification action is empty, and the You tab has no content implementation.
- Search covers loaded catalog items only. Do not describe this as complete channel search or complete YouTube parity.

CI policy: push compiles only; package IPA manually after validation. Preserve runs already in progress. Verify actual run status instead of relying only on ci-status files.


## 11. Continuation — 2026-10-06 afternoon

- Commit c28a14b removed unsupported preferredAddonManifestUrl arguments from PlayerLaunch and the unscoped link-cache key call, while retaining the source URL on StreamsRepository retries. Explicit addon launches bypass the unscoped saved-link cache. Run #170 (37433248900) completed SUCCESS. This is compile verification only; it was a push build and did not package an IPA.
- Commit 8b88078 connects the YouTube iOS libmpv bridge's audio/subtitle enumeration, selected-state and selection methods to mpv track-list/aid/sid instead of returning zero tracks/no-op selection. Subtitle delay and externally selected subtitle URLs now have real commands. Run #171 is pending verification. Automatic subtitlesJson loading, external subtitle removal and subtitle styling remain unimplemented in this bridge; do not claim complete subtitle support.
- Commit fb655ed adds pagination using the existing catalog pagination/merge helpers, only when a manifest explicitly declares skip support. Duplicate-page progression uses the existing bounded policy; later-page errors preserve earlier items. Home search now filters the channel list and Shorts shelf, and closing search clears its hidden filter. Run #172 is queued.
- Correction to the earlier first-page diagnosis: current Toolchecklink/youtube-khoai-service/server.js returns whole catalogs and does not advertise skip. Its Khoai response is not truncated by the app parser (maxItems is null). Do not attribute every missing-video report to pagination. Verify actual live catalog counts separately.
- Native YouTube getVideoQualityCount/selectVideoQuality still return zero/do nothing; quality completeness remains open. Source switching away from SaveTube remains prohibited until actual playback validation of the replacement.
- User authorized automatic IPA packaging after completion and latest-source CI pass, without asking again. Pushes remain compile-only. Device-only validation must be described honestly and does not block producing an IPA to test.
- During an active session, poll build status about every 30–60 seconds while continuing independent source review. Background automation has an hourly limit; never promise continuous background monitoring.
