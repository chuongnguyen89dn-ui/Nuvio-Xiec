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

        let options: [String: Any]? = headers.isEmpty
            ? nil
            : ["AVURLAssetHTTPHeaderFieldsKey": headers]
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
        player?.seek(to: time, toleranceBefore: .zero, toleranceAfter: .zero) { ok in
            completion?(ok)
        }
    }

    var currentTimeMs: Int64 {
        guard let seconds = player?.currentTime().seconds, seconds.isFinite else { return 0 }
        return Int64(seconds * 1000)
    }

    var durationMs: Int64 {
        guard let seconds = player?.currentItem?.duration.seconds, seconds.isFinite else { return 0 }
        return Int64(seconds * 1000)
    }

    func destroy() {
        player?.pause()
        player?.replaceCurrentItem(with: nil)
        playerLayer?.removeFromSuperlayer()
        playerLayer = nil
        player = nil
    }
}
