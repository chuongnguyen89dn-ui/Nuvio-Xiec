import Foundation
import UIKit
import AVFoundation
import MobileVLCKit
import ComposeApp

// Nuvio iOS playback backend backed by libVLC/MobileVLCKit.
// Media demuxing, codecs and network playback are handled by VLC itself.
final class MPVPlayerBridgeImpl: NSObject, NuvioPlayerBridge {
    private var playerVC: VLCPlayerViewController?

    func createPlayerViewController() -> UIViewController { ensurePlayerViewController() }
    private func ensurePlayerViewController() -> VLCPlayerViewController {
        if let playerVC { return playerVC }
        let vc = VLCPlayerViewController()
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

    private func parseSubtitles(_ json: String?) -> [PluginSubtitle] {
        guard let json, let data = json.data(using: .utf8),
              let raw = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return raw.compactMap {
            guard let url = $0["url"] as? String else { return nil }
            return PluginSubtitle(url: url, language: $0["language"] as? String ?? "Unknown", name: $0["name"] as? String, headers: $0["headers"] as? [String: String])
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
    func selectAudioTrack(index: Int32) {}

    func getSubtitleTrackCount() -> Int32 { 0 }
    func getSubtitleTrackIndex(at: Int32) -> Int32 { 0 }
    func getSubtitleTrackId(at: Int32) -> String { "0" }
    func getSubtitleTrackLabel(at: Int32) -> String { "" }
    func getSubtitleTrackLang(at: Int32) -> String { "" }
    func isSubtitleTrackSelected(at: Int32) -> Bool { false }
    func selectSubtitleTrack(index: Int32) {}
    func disableSubtitles() {}

    func getIsPlaying() -> Bool { playerVC?.isPlaying ?? false }
    func getIsLoading() -> Bool { playerVC?.isLoading ?? false }
    func getIsEnded() -> Bool { playerVC?.isEnded ?? false }
    func getDurationMs() -> Int64 { playerVC?.durationMs ?? 0 }
    func getPositionMs() -> Int64 { playerVC?.positionMs ?? 0 }
    func getBufferedMs() -> Int64 { 0 }
    func getCurrentSpeed() -> Float { playerVC?.currentSpeed ?? 1 }
    func getErrorMessage() -> String { playerVC?.errorMessage ?? "" }
}

final class VLCPlayerViewController: UIViewController, VLCMediaPlayerDelegate {
    private let vlc = VLCMediaPlayer(options: ["--network-caching=1500", "--http-reconnect", "--avcodec-hw=any"])
    private var lastURL: String?
    private var lastHeaders: [String: String] = [:]
    private(set) var isLoading = false
    private(set) var isEnded = false
    private(set) var errorMessage = ""
    private(set) var currentSpeed: Float = 1

    var isPlaying: Bool { vlc.isPlaying }
    var durationMs: Int64 { vlc.media?.length.intValue ?? 0 }
    var positionMs: Int64 { vlc.time.intValue }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        vlc.drawable = view
        vlc.delegate = self
    }

    func loadFile(_ urlString: String, audioUrl: String?, requestHeaders: [String: String], subtitles: [PluginSubtitle]) {
        guard let url = URL(string: urlString) else { errorMessage = "Invalid media URL"; return }
        lastURL = urlString
        lastHeaders = requestHeaders
        errorMessage = ""
        isEnded = false
        isLoading = true

        let media = VLCMedia(url: url)
        if let ua = header("User-Agent", in: requestHeaders) { media.addOption(":http-user-agent=\(ua)") }
        if let ref = header("Referer", in: requestHeaders) { media.addOption(":http-referrer=\(ref)") }
        vlc.media = media
        vlc.play()

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
    func seekToMs(_ ms: Int64) { vlc.time = VLCTime(int: Int32(clamping: ms)) }
    func seekByMs(_ ms: Int64) { seekToMs(max(0, positionMs + ms)) }
    func retryPlayback() { if let lastURL { loadFile(lastURL, audioUrl: nil, requestHeaders: lastHeaders, subtitles: []) } }
    func setSpeed(_ speed: Float) { currentSpeed = speed; vlc.rate = speed }
    func setMuted(_ muted: Bool) { vlc.audio?.isMuted = muted }
    func setResize(_ mode: Int) {
        vlc.videoAspectRatio = mode == 1 ? UnsafeMutablePointer(mutating: ("16:9" as NSString).utf8String) : nil
    }

    func mediaPlayerStateChanged(_ aNotification: Notification) {
        switch vlc.state {
        case .opening, .buffering:
            isLoading = true
        case .playing, .paused:
            isLoading = false
        case .ended:
            isLoading = false; isEnded = true
        case .error:
            isLoading = false; errorMessage = "libVLC playback error"
        default:
            break
        }
    }
}
