import SwiftUI
import AVKit

@MainActor
final class PlaybackModel: ObservableObject {
    @Published var sources: [MovieSource] = []
    @Published var options: [PlaybackTarget] = []
    @Published var player: AVPlayer?
    @Published var status: String = "Chọn một phim để lấy nguồn thực tế."
    @Published var isLoading = false
    @Published var currentTitle = ""
    private let resolver = SourceResolver()
    private var proxy: LocalHLSProxy?

    init() {
        sources = resolver.examples
    }

    func lookup(_ item: MovieSource) {
        isLoading = true
        options = []
        currentTitle = item.title
        status = "Đang lấy dữ liệu trực tiếp trên iPhone..."
        Task {
            do {
                let result = try await resolver.resolve(item)
                options = result
                status = "Tìm được \(result.count) nguồn. Chọn một nguồn để phát."
            } catch {
                status = "Resolver: \(error.localizedDescription)"
            }
            isLoading = false
        }
    }

    func play(_ option: PlaybackTarget) {
        isLoading = true
        status = "Đang chuẩn bị player..."
        currentTitle = option.title
        player?.pause()
        player = nil
        proxy?.stop()
        proxy = nil
        Task {
            do {
                // HLS playlists/segments and signed MP4 requests use device-only
                // loopback so that Safari's cross-origin restrictions do not apply.
                let local = LocalHLSProxy()
                try await local.start(referer: option.referer, origin: option.origin)
                proxy = local
                player = AVPlayer(url: local.localURL(for: option.url))
                status = "AVPlayer đang tải qua HTTP loopback. Không có Render."
                player?.play()
            } catch {
                status = "Player: \(error.localizedDescription)"
            }
            isLoading = false
        }
    }

    func stop() {
        player?.pause()
        player = nil
        proxy?.stop()
        proxy = nil
        status = "Đã dừng player và giải phóng proxy."
    }
}

struct ContentView: View {
    @StateObject private var model = PlaybackModel()
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Phim của bạn, cách phát của bạn.")
                            .font(.title2.bold())
                        Text("Nguồn được lấy trực tiếp trên iPhone. Player sử dụng HTTP loopback trong máy, không gửi video qua Render.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.indigo.opacity(0.18), in: RoundedRectangle(cornerRadius: 16))

                    Text("4 nguồn kiểm thử").font(.headline)
                    ForEach(model.sources) { source in
                        Button { model.lookup(source) } label: {
                            VStack(alignment: .leading, spacing: 5) {
                                Text(source.title).font(.headline)
                                Text(source.detail).font(.caption).foregroundStyle(.secondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(14)
                            .background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                        }
                        .buttonStyle(.plain)
                    }
                    if !model.options.isEmpty {
                        Text("Nguồn phát · \(model.currentTitle)").font(.headline)
                        ForEach(Array(model.options.enumerated()), id: \.offset) { index, option in
                            Button {
                                model.play(option)
                            } label: {
                                Label(option.title, systemImage: "play.circle.fill")
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(14)
                                    .background(Color.indigo.opacity(0.25), in: RoundedRectangle(cornerRadius: 12))
                            }
                        }
                    }
                    if let player = model.player {
                        VideoPlayer(player: player)
                            .frame(height: 250)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        Button("Dừng và giải phóng player", role: .destructive) { model.stop() }
                    }
                    if model.isLoading { ProgressView() }
                    Text(model.status)
                        .font(.footnote.monospaced())
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 10))
                }.padding()
            }
            .navigationTitle("NovaPlay")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
