import Foundation
import UIKit
import AVFoundation
import MobileVLCKit
import ComposeApp

// Nuvio iOS playback backend backed by libVLC/MobileVLCKit.
// Media demuxing, codecs and network playback are handled by VLC itself.
final class MPVPlayerBridgeImpl: NSObject, NuvioPlayerBridge {
    private var playerVC: MPVPlayerViewController?

    func createPlayerViewController() -> UIViewController { ensurePlayerViewController() }
    private func ensurePlayerViewController() -> MPVPlayerViewController {
        if let playerVC { return playerVC }
        let vc = MPVPlayerViewController()
        playerVC = vc
        return vc
    }

    func loadFile(url: String) { ensurePlayerViewController().loadFile(url, audioUrl: nil, requestHeaders: [:], subtitles: []) }

    func loadFileWithAudio(videoUrl: String, audioUrl: String?, headersJson: String?, subtitlesJson: String?) {
        ensurePlayerViewController().loadFile(
            videoUrl,
            audioUrl: audioUrl,
            requestHeaders: parseRequestHeaders(headersJson),
            subtitles: parseSubtitles(subtitlesJson)
        )
    }

    private func parseRequestHeaders(_ json: String?) -> [String: String] {
        guard let json, let data = json.data(using: .utf8),
              let value = try? JSONSerialization.jsonObject(with: data) as? [String: String] else { return [:] }
        return value
    }

    private func parseSubtitles(_ json: String?) -> [VLCSubtitle] {
        guard let json, let data = json.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return raw.compactMap {
            guard let url = $0["url"] as? String else { return nil }
            return VLCSubtitle(url: url)
        }
    }

    func play() { playerVC?.playPlayback() }
    func pause() { playerVC?.pausePlayback() }
    func seekTo(positionMs: Int64) { playerVC?.seekToMs(positionMs) }
    func seekBy(offsetMs: Int64) { playerVC?.seekByMs(offsetMs) }
    func retry() { playerVC?.retryPlayback() }
    func updateNowPlayingMetadata(title: String, subtitle: String?, artworkUrl: String?) {}
    func clearNowPlayingInfo() {}
    func configureVideoOutput(hardwareDecoder: String, targetColorspaceHint: Bool, toneMapping: String, hdrComputePeak: Bool, targetPrimaries: String, targetTransfer: String, extendedDynamicRange: Bool, deband: Bool, interpolation: Bool, brightness: Int32, contrast: Int32, saturation: Int32, gamma: Int32) {}
    func configureAudioOutput(audioOutput: String) {}
    func setPlaybackSpeed(speed: Float) { playerVC?.setSpeed(speed) }
    func setMuted(muted: Bool) { playerVC?.setMuted(muted) }
    func setResizeMode(mode: Int32) { playerVC?.setResize(Int(mode)) }
    func applyAudioLanguagePreferences(languages: [String]) {}
    func applySubtitleStyle(textColor: String, backgroundColor: String, outlineColor: String, outlineSize: Float, bold: Bool, fontSize: Float, subPos: Int32, stripSdh: Bool) {}
    func clearExternalSubtitle() {}
    func clearExternalSubtitleAndSelect(trackId: Int32) { playerVC?.selectSubtitleTrack(trackId) }
    func destroy() { playerVC?.stopPlayback(); playerVC = nil }
    func getPlaybackSpeed() -> Float { playerVC?.playbackSpeed ?? 1.0 }
    func setSubtitleDelayMs(delayMs: Int32) { playerVC?.setSubtitleDelayMs(delayMs) }
    func setSubtitleUrl(url: String) { playerVC?.setSubtitleUrl(url) }
    func syncVideoSurfaceLayout(width: Double, height: Double) {}

    func getVideoQualityCount() -> Int32 { 0 }
    func getVideoQualityHeight(at: Int32) -> Int32 { 0 }
    func getSelectedVideoQualityHeight() -> Int32 { -1 }
    func selectVideoQuality(height: Int32) {}

    func getAudioTrackCount() -> Int32 { 0 }
    func getAudioTrackIndex(at: Int32) -> Int32 { 0 }
    func getAudioTrackId(at: Int32) -> String { "0" }
    func getAudioTrackLabel(at: Int32) -> String { "" }
    func getAudioTrackLang(at: Int32) -> String { "" }
    func isAudioTrackSelected(at: Int32) -> Bool { false }
    func selectAudioTrack(trackId: Int32) { playerVC?.selectAudioTrack(trackId) }

    func getSubtitleTrackCount() -> Int32 { 0 }
    func getSubtitleTrackIndex(at: Int32) -> Int32 { 0 }
    func getSubtitleTrackId(at: Int32) -> String { "0" }
    func getSubtitleTrackLabel(at: Int32) -> String { "" }
    func getSubtitleTrackLang(at: Int32) -> String { "" }
    func isSubtitleTrackSelected(at: Int32) -> Bool { false }
    func selectSubtitleTrack(trackId: Int32) { playerVC?.selectSubtitleTrack(trackId) }
    func disableSubtitles() {}

    func getIsPlaying() -> Bool { playerVC?.isPlaying ?? false }
    func getIsLoading() -> Bool {
        guard let playerVC else { return false }
        // VLC can transiently report .buffering while decoded frames are still
        // advancing. Do not cover actively playing video with Nuvio's spinner.
        return playerVC.isSeekPending || (playerVC.isLoading && !playerVC.isPlaying)
    }
    func getIsEnded() -> Bool { playerVC?.isEnded ?? false }
    func getDurationMs() -> Int64 { playerVC?.durationMs ?? 0 }
    func getPositionMs() -> Int64 { playerVC?.positionMs ?? 0 }
    func getBufferedMs() -> Int64 { 0 }
    func getCurrentSpeed() -> Float { playerVC?.currentSpeed ?? 1 }
    func getErrorMessage() -> String { playerVC?.errorMessage ?? "" }
}

struct VLCSubtitle { let url: String }

// Keep the historical class name because NowPlayingController and the Compose host
// use it as their UI contract. Playback underneath is libVLC, not mpv.
final class MPVPlayerViewController: UIViewController, VLCMediaPlayerDelegate {
    private let vlc = VLCMediaPlayer(options: ["--network-caching=1500", "--http-reconnect", "--avcodec-hw=any"])
    private var lastURL: String?
    private var lastHeaders: [String: String] = [:]
    private var lastAudioURL: String?
    private var lastSubtitles: [VLCSubtitle] = []
    private(set) var isLoading = false
    private(set) var isEnded = false
    private(set) var errorMessage = ""
    private(set) var currentSpeed: Float = 1
    private var pendingSeekTargetMs: Int64?

    var isPlaying: Bool { vlc.isPlaying }
    var durationMs: Int64 { Int64(vlc.media?.length.intValue ?? 0) }
    var positionMs: Int64 {
        let value = Int64(vlc.time.intValue)
        if let target = pendingSeekTargetMs, abs(value - target) <= 2_500 {
            pendingSeekTargetMs = nil
            isLoading = false
        }
        return value
    }
    var isSeekPending: Bool { pendingSeekTargetMs != nil }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        vlc.drawable = view
        vlc.delegate = self
    }

    func loadFile(_ urlString: String, audioUrl: String?, requestHeaders: [String: String], subtitles: [VLCSubtitle]) {
        guard let url = URL(string: urlString) else { errorMessage = "Invalid media URL"; return }
        lastURL = urlString
        lastHeaders = requestHeaders
        lastAudioURL = audioUrl
        lastSubtitles = subtitles
        pendingSeekTargetMs = nil
        errorMessage = ""
        isEnded = false
        isLoading = true

        let media = VLCMedia(url: url)
        if let ua = header("User-Agent", in: requestHeaders) { media.addOption(":http-user-agent=\(ua)") }
        if let ref = header("Referer", in: requestHeaders) { media.addOption(":http-referrer=\(ref)") }
        vlc.media = media
        // Loading prepares the media only. Nuvio owns whether playback starts;
        // PlatformPlayerSurface calls play()/pause() from playWhenReady.

        // VLC owns probing/demux/decoding. Additional audio/subtitle resources stay optional.
        if let audioUrl, let u = URL(string: audioUrl) { vlc.addPlaybackSlave(u, type: .audio, enforce: true) }
        for subtitle in subtitles {
            if let u = URL(string: subtitle.url) { vlc.addPlaybackSlave(u, type: .subtitle, enforce: false) }
        }
    }

    private func header(_ key: String, in headers: [String: String]) -> String? {
        headers.first { $0.key.caseInsensitiveCompare(key) == .orderedSame }?.value
    }

    func playPlayback() { vlc.play() }
    func pausePlayback() { vlc.pause() }
    func seekToMs(_ ms: Int64) {
        pendingSeekTargetMs = max(0, ms)
        isLoading = true
        isEnded = false
        errorMessage = ""
        vlc.time = VLCTime(int: Int32(clamping: ms))
    }
    func seekByMs(_ ms: Int64) { seekToMs(max(0, positionMs + ms)) }
    func retryPlayback() {
        if let lastURL {
            loadFile(lastURL, audioUrl: lastAudioURL, requestHeaders: lastHeaders, subtitles: lastSubtitles)
        }
    }
    func setSpeed(_ speed: Float) { currentSpeed = speed; vlc.rate = speed }
    func setMuted(_ muted: Bool) { vlc.audio?.isMuted = muted }
    var isPlayerPlaying: Bool { vlc.isPlaying }
    var playbackSpeed: Float { vlc.rate }
    func stopPlayback() { vlc.stop() }
    func setSubtitleDelayMs(_ delayMs: Int32) { vlc.currentVideoSubTitleDelay = Int(delayMs) * 1000 }
    func setSubtitleUrl(_ url: String) {
        guard let u = URL(string: url) else { return }
        vlc.addPlaybackSlave(u, type: .subtitle, enforce: true)
    }
    func seekByMs(_ ms: Int64, exact: Bool) { seekByMs(ms) }
    func selectAudioTrack(_ trackId: Int32) { vlc.currentAudioTrackIndex = trackId }
    func selectSubtitleTrack(_ trackId: Int32) { vlc.currentVideoSubTitleIndex = trackId }
    func setResize(_ mode: Int) {}

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        switch vlc.state {
        case .opening, .buffering:
            isLoading = true
        case .playing, .paused:
            // A VLC state transition to playing can arrive before a far seek
            // has actually reached its target. Keep Nuvio in loading state
            // until the reported playback clock reaches the requested seek.
            if pendingSeekTargetMs == nil {
                isLoading = false
            }
        case .ended:
            isLoading = false; isEnded = true
        case .error:
            isLoading = false; errorMessage = "libVLC playback error"
        default:
            break
        }
    }
}


// MARK: - Bridge Creator (implements Kotlin protocol)

final class MPVPlayerBridgeCreator: NSObject, NuvioPlayerBridgeCreator {
    func createBridge() -> any NuvioPlayerBridge {
        return MPVPlayerBridgeImpl()
    }
}

/// Separate creator used only by PlayerPlaybackContext.YOUTUBE_PROFILE.
/// It intentionally creates a fresh MobileVLCKit-backed bridge instead of
/// sharing the Nuvio/AV01 player instance or any playback lifecycle state.
final class YouTubeProfilePlayerBridgeCreator: NSObject, NuvioPlayerBridgeCreator {
    func createBridge() -> any NuvioPlayerBridge {
        return MPVPlayerBridgeImpl()
    }
}

// MARK: - Registration (called from Swift app startup)

enum NuvioPlayerRegistration {
    static func register() {
        NuvioPlayerBridgeFactory.shared.registerFactory(creator: MPVPlayerBridgeCreator())
        YouTubeProfilePlayerBridgeFactory.shared.registerFactory(creator: YouTubeProfilePlayerBridgeCreator())
    }
}
