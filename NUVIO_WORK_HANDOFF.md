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

Old/unrelated workflows have been removed or selected for removal so they cannot be confused with the active Nuvio iOS build.

Known removed during cleanup:
- `.github/workflows/ivyplay-tv-android.yml`
- `.github/workflows/xiec-actor-build.yml`
- `.github/workflows/fix-ios-compat-once.yml`
- `.github/workflows/novaplay-pages.yml`

The user subsequently manually removed the remaining obsolete workflows that were identified:
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

## 5. AV01 playback — continue AFTER the YouTube/navigation issue is under control

AV01 has already been worked on multiple times. Do not make a third speculative fix.

The failing Nuvio test showed the player/FFmpeg path reaching errors equivalent to:
- `error reading header`
- `avformat_open_input() failed`
- `unrecognized file format`

A VLC/local path had previously demonstrated that a working playback request exists. Therefore diagnose the difference rather than guessing.

Instrument/compare the Nuvio request against the known-working request, including as applicable:
- resolved/final media URL
- redirects
- HTTP status
- `Content-Type`
- `User-Agent`
- `Referer`
- `Origin`
- cookies/token/query parameters
- request headers passed into the player
- enough response metadata/body prefix to distinguish an HLS/media response from HTML/error/anti-bot output

Only change source after identifying the concrete difference. Preserve the existing AV01 resolver/player work unless evidence shows a specific part is wrong.

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

Start by auditing the current `main` implementation responsible for the YouTube profile/mobile UI. Trace:

1. what opens the YouTube screen;
2. where its channel/profile data comes from;
3. why data appears without the addon;
4. what is intercepting or preventing taps;
5. how navigation back to Nuvio is supposed to work;
6. channel/video route handlers.

Fix those issues on Nuvio without removing the existing AV01 work. Build/test through the Nuvio iOS workflow only after the code path is coherent.


## 9. Work continuation — 2026-10-04, YouTube interaction fix

Source commit: `436662ac590028398dd632fdbdf449fc910ae597`.
iOS run: #125, https://github.com/chuongnguyen89dn-ui/Nuvio-Xiec/actions/runs/37203416637.
Status at this checkpoint: building, NOT accepted as a working device build.

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
- Before packaging, CI requires the built bundle ID to be com.nuvio.app and includes Nuvio-build-identity.txt alongside Nuvio-unsigned.ipa.

Validation/limits:
- Source diff checked; AV01 resolver/player files unchanged.
- Local Gradle could not download its distribution because the execution environment cannot reach services.gradle.org.
- Device acceptance still required: no-addon state, install/disable/remove addon, tap channel/video, player return, close to Nuvio, switch profile.
- Search currently covers loaded addon catalog data. Existing catalog adapter supplies videos; Shorts is empty unless actual Shorts data is supplied.
- Do not claim AV01 fixed or device interactions verified from CI.
