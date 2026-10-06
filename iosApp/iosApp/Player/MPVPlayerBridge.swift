import Foundation
import UIKit
import AVFoundation
import MobileVLCKit
import Libmpv
import QuartzCore
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



// MARK: - YouTube Profile libmpv backend
// Deliberately isolated from MPVPlayerBridgeImpl/MobileVLCKit so AV01 and the
// normal Nuvio player keep their proven playback path unchanged.

final class YouTubeMPVMetalLayer: CAMetalLayer {
    override var drawableSize: CGSize {
        get { super.drawableSize }
        set {
            if newValue.width > 1, newValue.height > 1 { super.drawableSize = newValue }
        }
    }
}

final class YouTubeMPVViewController: UIViewController {
    private let metalLayer = YouTubeMPVMetalLayer()
    private var mpv: OpaquePointer?
    private let eventQueue = DispatchQueue(label: "nuvio.youtube.mpv.events")
    private(set) var errorMessage = ""
    private var lastVideoURL: String?
    private var lastAudioURL: String?
    private var lastHeaders: [String: String] = [:]

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        metalLayer.contentsScale = UIScreen.main.nativeScale
        metalLayer.framebufferOnly = true
        metalLayer.backgroundColor = UIColor.black.cgColor
        view.layer.addSublayer(metalLayer)
        setupMPV()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        metalLayer.frame = view.bounds
    }

    private func setupMPV() {
        guard mpv == nil, let ctx = mpv_create() else {
            if mpv == nil { errorMessage = "Unable to create libmpv context" }
            return
        }
        mpv = ctx
        _ = mpv_set_option_string(ctx, "vo", "gpu-next")
        _ = mpv_set_option_string(ctx, "gpu-api", "vulkan")
        _ = mpv_set_option_string(ctx, "gpu-context", "moltenvk")
        _ = mpv_set_option_string(ctx, "hwdec", "videotoolbox")
        _ = mpv_set_option_string(ctx, "keep-open", "yes")
        _ = mpv_set_option_string(ctx, "pause", "yes")
        var layerObject: AnyObject = metalLayer
        _ = withUnsafeMutablePointer(to: &layerObject) {
            mpv_set_option(ctx, "wid", MPV_FORMAT_INT64, $0)
        }
        let initStatus = mpv_initialize(ctx)
        if initStatus < 0 {
            errorMessage = "libmpv initialize failed: \(String(cString: mpv_error_string(initStatus)))"
            return
        }
        mpv_set_wakeup_callback(ctx, { raw in
            guard let raw else { return }
            let owner = Unmanaged<YouTubeMPVViewController>.fromOpaque(raw).takeUnretainedValue()
            owner.drainEvents()
        }, Unmanaged.passUnretained(self).toOpaque())
    }

    private var qualitySource: URL?
    private var qualityTask: URLSessionDataTask?
    private var qualityRequested = false
    private var qualityRetryAfter = Date.distantPast
    private(set) var availableQualities: [Int32] = []
    private(set) var selectedQuality: Int32 = 0

    private func sourceForQuality(_ videoURL: String) -> URL? {
        guard var parts = URLComponents(string: videoURL) else { return nil }
        let path = parts.path.split(separator: "/")
        guard path.count == 3, path[0] == "play", path[2].hasSuffix(".mp4") else { return nil }
        parts.path = "/play/\(path[1])/auto.mp4"
        return parts.url
    }

    func refreshQualities() {
        guard !qualityRequested, Date() >= qualityRetryAfter, let source = qualitySource,
              var parts = URLComponents(url: source, resolvingAgainstBaseURL: false) else { return }
        qualityRequested = true
        let videoID = source.path.split(separator: "/")[1]
        parts.path = "/formats/\(videoID)"
        guard let url = parts.url else { return }
        var request = URLRequest(url: url)
        request.timeoutInterval = 120
        qualityTask = URLSession.shared.dataTask(with: request) { [weak self] data, response, _ in
            guard let data, (response as? HTTPURLResponse)?.statusCode == 200,
                  let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let formats = root["formats"] as? [[String: Any]] else {
                DispatchQueue.main.async {
                    guard let self, self.qualitySource == source else { return }
                    self.qualityRequested = false
                    self.qualityRetryAfter = Date().addingTimeInterval(300)
                }
                return
            }
            let heights = formats.compactMap { format -> Int32? in
                guard let height = format["quality"] as? NSNumber,
                      let reported = format["reportedQuality"] as? NSNumber,
                      height.int32Value > 0, height.int32Value == reported.int32Value else { return nil }
                return height.int32Value
            }
            DispatchQueue.main.async {
                guard let self, self.qualitySource == source else { return }
                self.availableQualities = Array(Set(heights)).sorted(by: >)
            }
        }
        qualityTask?.resume()
    }

    func selectQuality(_ height: Int32) {
        guard let source = qualitySource, height == 0 || availableQualities.contains(height),
              var parts = URLComponents(url: source, resolvingAgainstBaseURL: false) else { return }
        let videoID = source.path.split(separator: "/")[1]
        parts.path = "/play/\(videoID)/\(height == 0 ? "auto" : String(height)).mp4"
        guard let url = parts.url else { return }
        let resumeSeconds = Double(positionMs) / 1000.0
        selectedQuality = height
        load(videoURL: url.absoluteString, audioURL: lastAudioURL, headers: lastHeaders, startSeconds: resumeSeconds)
    }

    func load(videoURL: String, audioURL: String?, headers: [String: String], startSeconds: Double? = nil) {
        if !isViewLoaded { loadViewIfNeeded() }
        guard mpv != nil else { return }
        let source = sourceForQuality(videoURL)
        if source != qualitySource {
            qualityTask?.cancel()
            qualitySource = source
            qualityRequested = false
            qualityRetryAfter = .distantPast
            availableQualities = []
            selectedQuality = 0
        }
        lastVideoURL = videoURL
        lastAudioURL = audioURL
        lastHeaders = headers
        errorMessage = ""
        let value = headers.map { "\($0.key): \($0.value)" }.joined(separator: ",")
        setString("http-header-fields", value)
        var options: [String] = []
        if let audioURL, !audioURL.isEmpty {
            options.append("audio-file=\(audioURL)")
        }
        if let startSeconds { options.append("start=\(max(0, startSeconds))") }
        var args = [videoURL, "replace"]
        if !options.isEmpty {
            // mpv 0.38 inserted the playlist index before per-file options.
            let version = getString("mpv-version").split(whereSeparator: { !$0.isNumber && $0 != "." }).first?.split(separator: ".") ?? []
            let major = version.first.flatMap { Int($0) } ?? 0
            let minor = version.dropFirst().first.flatMap { Int($0) } ?? 0
            if major > 0 || minor >= 38 { args.append("-1") }
            args.append(options.joined(separator: ","))
        }
        command("loadfile", args)
    }

    func retry() {
        guard let lastVideoURL else { return }
        load(videoURL: lastVideoURL, audioURL: lastAudioURL, headers: lastHeaders)
    }

    func play() { setFlag("pause", false) }
    func pause() { setFlag("pause", true) }
    func stop() { command("stop", []) }
    func seek(toMs ms: Int64) { command("seek", [String(Double(max(0, ms)) / 1000.0), "absolute+exact"]) }
    func seek(byMs ms: Int64) { command("seek", [String(Double(ms) / 1000.0), "relative+exact"]) }
    func setSpeed(_ value: Float) { setDouble("speed", Double(value)) }
    func setMuted(_ value: Bool) { setFlag("mute", value) }

    var isPlaying: Bool { !getFlag("pause") && !isEnded }
    var isLoading: Bool { getFlag("paused-for-cache") || getFlag("seeking") }
    var isEnded: Bool { getFlag("eof-reached") }
    var durationMs: Int64 { Int64(getDouble("duration") * 1000.0) }
    var positionMs: Int64 { Int64(getDouble("time-pos") * 1000.0) }
    var bufferedMs: Int64 { Int64(getDouble("demuxer-cache-time") * 1000.0) }
    var speed: Float { Float(getDouble("speed")) }

    private func command(_ name: String, _ args: [String]) {
        guard let mpv else { return }
        let values = [name] + args
        let allocated = values.map { strdup($0) }
        defer { allocated.forEach { free($0) } }
        var cargs: [UnsafePointer<CChar>?] = allocated.map { pointer in
            pointer.map { UnsafePointer($0) }
        }
        cargs.append(nil)
        let status = cargs.withUnsafeMutableBufferPointer { buffer in
            mpv_command(mpv, buffer.baseAddress)
        }
        if status < 0 { errorMessage = "libmpv \(name): \(String(cString: mpv_error_string(status)))" }
    }

    private func setFlag(_ name: String, _ value: Bool) {
        guard let mpv else { return }
        var v: Int32 = value ? 1 : 0
        _ = mpv_set_property(mpv, name, MPV_FORMAT_FLAG, &v)
    }

    private func setDouble(_ name: String, _ value: Double) {
        guard let mpv else { return }
        var v = value
        _ = mpv_set_property(mpv, name, MPV_FORMAT_DOUBLE, &v)
    }

    private func setString(_ name: String, _ value: String) {
        guard let mpv else { return }
        _ = mpv_set_property_string(mpv, name, value)
    }

    private func getFlag(_ name: String) -> Bool {
        guard let mpv else { return false }
        var v: Int32 = 0
        return mpv_get_property(mpv, name, MPV_FORMAT_FLAG, &v) >= 0 && v != 0
    }

    private func getDouble(_ name: String) -> Double {
        guard let mpv else { return 0 }
        var v = 0.0
        return mpv_get_property(mpv, name, MPV_FORMAT_DOUBLE, &v) >= 0 ? v : 0
    }

    // Read mpv's live track list; IDs are distinct from the UI's zero-based indexes.
    func trackIndexes(_ type: String) -> [Int] {
        let count = Int(getString("track-list/count")) ?? 0
        return (0..<max(0, count)).filter { getString("track-list/\($0)/type") == type }
    }

    func trackProperty(_ type: String, at: Int32, name: String) -> String {
        let indexes = trackIndexes(type)
        guard at >= 0, Int(at) < indexes.count else { return "" }
        return getString("track-list/\(indexes[Int(at)])/\(name)")
    }

    func selectAudioTrack(_ id: Int32) { setString("aid", id < 0 ? "no" : String(id)) }
    func selectSubtitleTrack(_ id: Int32) { setString("sid", id < 0 ? "no" : String(id)) }
    func setSubtitleDelay(_ ms: Int32) { setDouble("sub-delay", Double(ms) / 1000.0) }
    func addSubtitle(_ url: String) { command("sub-add", [url, "select"]) }

    private func getString(_ name: String) -> String {
        guard let mpv, let value = mpv_get_property_string(mpv, name) else { return "" }
        defer { mpv_free(value) }
        return String(cString: value)
    }

    private func drainEvents() {
        eventQueue.async { [weak self] in
            guard let self, let mpv = self.mpv else { return }
            while true {
                guard let event = mpv_wait_event(mpv, 0), event.pointee.event_id != MPV_EVENT_NONE else { break }
                if event.pointee.event_id == MPV_EVENT_END_FILE,
                   let data = event.pointee.data?.assumingMemoryBound(to: mpv_event_end_file.self),
                   data.pointee.reason == MPV_END_FILE_REASON_ERROR {
                    let code = data.pointee.error
                    DispatchQueue.main.async {
                        self.errorMessage = "libmpv playback error: \(String(cString: mpv_error_string(code)))"
                    }
                }
            }
        }
    }

    deinit {
        qualityTask?.cancel()
        if let mpv {
            mpv_set_wakeup_callback(mpv, nil, nil)
            mpv_terminate_destroy(mpv)
        }
    }
}

final class YouTubeMPVPlayerBridgeImpl: NSObject, NuvioPlayerBridge {
    private var playerVC: YouTubeMPVViewController?
    private func controller() -> YouTubeMPVViewController {
        if let playerVC { return playerVC }
        let vc = YouTubeMPVViewController()
        playerVC = vc
        return vc
    }

    func createPlayerViewController() -> UIViewController { controller() }
    func loadFile(url: String) { controller().load(videoURL: url, audioURL: nil, headers: [:]) }
    func loadFileWithAudio(videoUrl: String, audioUrl: String?, headersJson: String?, subtitlesJson: String?) {
        var headers: [String: String] = [:]
        if let headersJson, let data = headersJson.data(using: .utf8),
           let decoded = try? JSONSerialization.jsonObject(with: data) as? [String: String] { headers = decoded }
        controller().load(videoURL: videoUrl, audioURL: audioUrl, headers: headers)
    }
    func play() { playerVC?.play() }
    func pause() { playerVC?.pause() }
    func seekTo(positionMs: Int64) { playerVC?.seek(toMs: positionMs) }
    func seekBy(offsetMs: Int64) { playerVC?.seek(byMs: offsetMs) }
    func retry() { playerVC?.retry() }
    func destroy() { playerVC?.stop(); playerVC = nil }
    func setPlaybackSpeed(speed: Float) { playerVC?.setSpeed(speed) }
    func setMuted(muted: Bool) { playerVC?.setMuted(muted) }
    func getPlaybackSpeed() -> Float { playerVC?.speed ?? 1 }
    func getIsPlaying() -> Bool { playerVC?.isPlaying ?? false }
    func getIsLoading() -> Bool { playerVC?.isLoading ?? false }
    func getIsEnded() -> Bool { playerVC?.isEnded ?? false }
    func getDurationMs() -> Int64 { playerVC?.durationMs ?? 0 }
    func getPositionMs() -> Int64 { playerVC?.positionMs ?? 0 }
    func getBufferedMs() -> Int64 { playerVC?.bufferedMs ?? 0 }
    func getCurrentSpeed() -> Float { playerVC?.speed ?? 1 }
    func getErrorMessage() -> String { playerVC?.errorMessage ?? "" }

    func updateNowPlayingMetadata(title: String, subtitle: String?, artworkUrl: String?) {}
    func clearNowPlayingInfo() {}
    func configureVideoOutput(hardwareDecoder: String, targetColorspaceHint: Bool, toneMapping: String, hdrComputePeak: Bool, targetPrimaries: String, targetTransfer: String, extendedDynamicRange: Bool, deband: Bool, interpolation: Bool, brightness: Int32, contrast: Int32, saturation: Int32, gamma: Int32) {}
    func configureAudioOutput(audioOutput: String) {}
    func setResizeMode(mode: Int32) {}
    func applyAudioLanguagePreferences(languages: [String]) {}
    func applySubtitleStyle(textColor: String, backgroundColor: String, outlineColor: String, outlineSize: Float, bold: Bool, fontSize: Float, subPos: Int32, stripSdh: Bool) {}
    func clearExternalSubtitle() {}
    func clearExternalSubtitleAndSelect(trackId: Int32) { playerVC?.selectSubtitleTrack(trackId) }
    func setSubtitleDelayMs(delayMs: Int32) { playerVC?.setSubtitleDelay(delayMs) }
    func setSubtitleUrl(url: String) { playerVC?.addSubtitle(url) }
    func syncVideoSurfaceLayout(width: Double, height: Double) {}
    func getVideoQualityCount() -> Int32 { playerVC?.refreshQualities(); return Int32(playerVC?.availableQualities.count ?? 0) }
    func getVideoQualityHeight(at: Int32) -> Int32 {
        guard let playerVC, at >= 0, Int(at) < playerVC.availableQualities.count else { return 0 }
        return playerVC.availableQualities[Int(at)]
    }
    func getSelectedVideoQualityHeight() -> Int32 { playerVC?.selectedQuality ?? 0 }
    func selectVideoQuality(height: Int32) { playerVC?.selectQuality(height) }
    func getAudioTrackCount() -> Int32 { Int32(playerVC?.trackIndexes("audio").count ?? 0) }
    func getAudioTrackIndex(at: Int32) -> Int32 { at }
    func getAudioTrackId(at: Int32) -> String { playerVC?.trackProperty("audio", at: at, name: "id") ?? "" }
    func getAudioTrackLabel(at: Int32) -> String { playerVC?.trackProperty("audio", at: at, name: "title") ?? "" }
    func getAudioTrackLang(at: Int32) -> String { playerVC?.trackProperty("audio", at: at, name: "lang") ?? "" }
    func isAudioTrackSelected(at: Int32) -> Bool { playerVC?.trackProperty("audio", at: at, name: "selected") == "yes" }
    func selectAudioTrack(trackId: Int32) { playerVC?.selectAudioTrack(trackId) }
    func getSubtitleTrackCount() -> Int32 { Int32(playerVC?.trackIndexes("sub").count ?? 0) }
    func getSubtitleTrackIndex(at: Int32) -> Int32 { at }
    func getSubtitleTrackId(at: Int32) -> String { playerVC?.trackProperty("sub", at: at, name: "id") ?? "" }
    func getSubtitleTrackLabel(at: Int32) -> String { playerVC?.trackProperty("sub", at: at, name: "title") ?? "" }
    func getSubtitleTrackLang(at: Int32) -> String { playerVC?.trackProperty("sub", at: at, name: "lang") ?? "" }
    func isSubtitleTrackSelected(at: Int32) -> Bool { playerVC?.trackProperty("sub", at: at, name: "selected") == "yes" }
    func selectSubtitleTrack(trackId: Int32) { playerVC?.selectSubtitleTrack(trackId) }
    func disableSubtitles() { playerVC?.selectSubtitleTrack(-1) }
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
        return YouTubeMPVPlayerBridgeImpl()
    }
}

// MARK: - Registration (called from Swift app startup)

enum NuvioPlayerRegistration {
    static func register() {
        NuvioPlayerBridgeFactory.shared.registerFactory(creator: MPVPlayerBridgeCreator())
        YouTubeProfilePlayerBridgeFactory.shared.registerFactory(creator: YouTubeProfilePlayerBridgeCreator())
    }
}
