import AVFoundation
import UIKit

/// Source-agnostic native HLS playback backend for IvyPlay iOS.
/// This intentionally contains no site-specific resolver logic.
final class NativeHLSPlayer {
    private weak var hostView: UIView?
    private var player: AVPlayer?
    private var playerLayer: AVPlayerLayer?

    init(hostView: UIView) {
        self.hostView = hostView
    }

    func load(url: URL, headers: [String: String] = [:]) {
        destroy()
        guard let hostView else { return }
        let options: [String: Any]? = headers.isEmpty ? nil : ["AVURLAssetHTTPHeaderFieldsKey": headers]
        let asset = AVURLAsset(url: url, options: options)
        let item = AVPlayerItem(asset: asset)
        let player = AVPlayer(playerItem: item)
        player.automaticallyWaitsToMinimizeStalling = true
        let layer = AVPlayerLayer(player: player)
        layer.videoGravity = .resizeAspect
        layer.frame = hostView.bounds
        hostView.layer.addSublayer(layer)
        self.player = player
        self.playerLayer = layer
        player.play()
    }

    func layout() {
        guard let hostView else { return }
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        playerLayer?.frame = hostView.bounds
        CATransaction.commit()
    }

    func play() { player?.play() }
    func pause() { player?.pause() }

    func seek(to milliseconds: Int64, completion: ((Bool) -> Void)? = nil) {
        let time = CMTime(value: milliseconds, timescale: 1000)
        player?.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero) { ok in completion?(ok) }
    }

    func seek(by milliseconds: Int64) {
        seek(to: max(0, currentTimeMs + milliseconds))
    }

    var currentTimeMs: Int64 {
        guard let seconds = player?.currentTime().seconds, seconds.isFinite else { return 0 }
        return Int64(seconds * 1000)
    }

    var durationMs: Int64 {
        guard let seconds = player?.currentItem?.duration.seconds, seconds.isFinite else { return 0 }
        return Int64(seconds * 1000)
    }

    var isPlaying: Bool { (player?.rate ?? 0) > 0 }

    func destroy() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        playerLayer?.removeFromSuperlayer()
        playerLayer = nil
        player = nil
    }
}

/// UIViewController adapter so the existing Kotlin/Compose player surface can host native HLS
/// without changing the public NuvioPlayerBridge contract.
final class NativeHLSPlayerViewController: UIViewController {
    private var nativePlayer: NativeHLSPlayer?
    private(set) var lastURL: URL?
    private(set) var lastHeaders: [String: String] = [:]

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black
        nativePlayer = NativeHLSPlayer(hostView: view)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        nativePlayer?.layout()
    }

    func load(url: URL, headers: [String: String]) {
        loadViewIfNeeded()
        lastURL = url
        lastHeaders = headers
        nativePlayer?.load(url: url, headers: headers)
    }

    func play() { nativePlayer?.play() }
    func pause() { nativePlayer?.pause() }
    func seek(to milliseconds: Int64) { nativePlayer?.seek(to: milliseconds) }
    func seek(by milliseconds: Int64) { nativePlayer?.seek(by: milliseconds) }
    var positionMs: Int64 { nativePlayer?.currentTimeMs ?? 0 }
    var durationMs: Int64 { nativePlayer?.durationMs ?? 0 }
    var isPlaying: Bool { nativePlayer?.isPlaying ?? false }

    func retry() {
        guard let lastURL else { return }
        nativePlayer?.load(url: lastURL, headers: lastHeaders)
    }

    func destroyPlayer() {
        nativePlayer?.destroy()
        lastURL = nil
        lastHeaders = [:]
    }
}
