import SwiftUI
import AVKit

private enum NovaStyle {
    static let background = Color(red: 0.045, green: 0.057, blue: 0.10)
    static let surface = Color(red: 0.09, green: 0.11, blue: 0.17)
    static let accent = Color(red: 0.56, green: 0.49, blue: 0.98)
    static let muted = Color(red: 0.63, green: 0.67, blue: 0.76)
}

struct Film: Identifiable, Hashable {
    let id: String
    let title: String
    let year: String
    let genre: String
    let synopsis: String
    let symbol: String
    let tint: Color
    static func == (lhs: Film, rhs: Film) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

private let films: [Film] = [
    .init(id: "xiec", title: "MIKR-112", year: "2026", genre: "XemXiec", synopsis: "Nguồn phim chính #1/#2 được tách riêng khỏi trailer.", symbol: "play.rectangle.fill", tint: .indigo),
    .init(id: "phimhd", title: "Biên Niên Sử Giáng Sinh 2", year: "2020", genre: "PhimHD", synopsis: "Chọn nguồn phát hoặc nhập đường dẫn video hợp lệ.", symbol: "sparkles.tv.fill", tint: .teal),
    .init(id: "missav", title: "FTHTD-219", year: "2026", genre: "MissAV", synopsis: "HLS 1080p cần xử lý request trong player native.", symbol: "film.stack.fill", tint: .purple),
    .init(id: "ikisoda", title: "HSM-061", year: "2024", genre: "IkiSoda", synopsis: "Liên kết phát được cung cấp bởi resolver độc lập.", symbol: "play.square.stack.fill", tint: .orange)
]

@MainActor final class PlaybackModel: ObservableObject {
    @Published var options: [PlaybackTarget] = []
    @Published var player: AVPlayer?
    @Published var status = "Sẵn sàng. Chọn phim hoặc nhập URL video."
    @Published var isLoading = false
    @Published var currentTitle = ""
    private let resolver = SourceResolver()
    private var proxy: LocalHLSProxy?
    private var generation = 0

    func lookup(_ id: String, title: String) {
        generation += 1
        let token = generation
        options = []
        currentTitle = title
        isLoading = true
        status = "Đang lấy nguồn cho \(title)…"
        Task {
            do {
                guard let item = resolver.examples.first(where: { $0.id == id }) else {
                    throw SourceError.missing("Chưa có resolver cho phim này.")
                }
                let items = try await resolver.resolve(item)
                guard token == generation else { return }
                options = items
                status = items.isEmpty ? "Chưa có liên kết phát." : "Đã nhận \(items.count) nguồn. Chọn để phát."
            } catch {
                guard token == generation else { return }
                status = "Nguồn: \(error.localizedDescription)"
            }
            if token == generation { isLoading = false }
        }
    }

    func play(_ target: PlaybackTarget) {
        generation += 1
        let token = generation
        player?.pause()
        player = nil
        proxy?.stop()
        proxy = nil
        currentTitle = target.title
        isLoading = true
        status = "Đang chuẩn bị phát…"
        Task {
            do {
                let local = LocalHLSProxy()
                try await local.start(referer: target.referer, origin: target.origin)
                guard token == generation else { local.stop(); return }
                proxy = local
                let next = AVPlayer(url: local.localURL(for: target.url))
                player = next
                next.play()
                status = "Đã gửi yêu cầu tới player. Đang chờ dữ liệu video."
            } catch {
                if token == generation { status = "Player: \(error.localizedDescription)" }
            }
            if token == generation { isLoading = false }
        }
    }

    func playDirect(_ raw: String) {
        guard let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              url.scheme?.lowercased() == "https" else {
            status = "Nhập URL HTTPS hợp lệ."
            return
        }
        play(PlaybackTarget(url: url, referer: nil, origin: nil, title: "Video tùy chọn"))
    }

    func stop() {
        generation += 1
        player?.pause()
        player = nil
        proxy?.stop()
        proxy = nil
        isLoading = false
        status = "Đã dừng và giải phóng player."
    }
}

struct ContentView: View {
    @StateObject private var model = PlaybackModel()
    @AppStorage("novaplay.saved") private var savedIDs = ""
    @AppStorage("novaplay.manifests") private var manifestStore = ""
    @State private var selectedTab = 0
    @State private var chosenFilm: Film?
    @State private var searchText = ""
    @State private var manualURL = ""
    @State private var manifestURL = ""
    @State private var showingPlayer = false

    private var favorites: Set<String> {
        Set(savedIDs.split(separator: "|").map(String.init))
    }
    private var matches: [Film] {
        let term = searchText.trimmingCharacters(in: .whitespacesAndNewlines)
        return term.isEmpty ? films : films.filter {
            $0.title.localizedCaseInsensitiveContains(term) || $0.genre.localizedCaseInsensitiveContains(term)
        }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            NavigationStack { home }
                .tabItem { Label("Trang chủ", systemImage: "house.fill") }.tag(0)
            NavigationStack { search }
                .tabItem { Label("Tìm kiếm", systemImage: "magnifyingglass") }.tag(1)
            NavigationStack { library }
                .tabItem { Label("Thư viện", systemImage: "bookmark.fill") }.tag(2)
            NavigationStack { sources }
                .tabItem { Label("Nguồn", systemImage: "square.stack.3d.up.fill") }.tag(3)
        }
        .tint(NovaStyle.accent)
        .preferredColorScheme(.dark)
        .sheet(item: $chosenFilm) { film in detail(film) }
        .fullScreenCover(isPresented: $showingPlayer) { playerScreen }
    }

    private var home: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 27) {
                HStack(spacing: 8) {
                    Image(systemName: "play.square.stack.fill").foregroundStyle(NovaStyle.accent)
                    Text("NOVAPLAY").font(.system(size: 24, weight: .black, design: .rounded))
                    Spacer()
                    Text("iPHONE").font(.caption2.weight(.bold)).foregroundStyle(NovaStyle.muted)
                }.padding(.top, 12)
                hero(films[1])
                rail("Tiếp tục khám phá", items: films)
                rail("Nguồn phim của bạn", items: Array(films.reversed()))
                VStack(alignment: .leading, spacing: 8) {
                    Label("Nhẹ và độc lập", systemImage: "bolt.fill").font(.headline)
                    Text("Danh mục và player nằm trên iPhone. Video phát trực tiếp hoặc qua loopback trong máy; NovaPlay không dùng Render.")
                        .font(.subheadline).foregroundStyle(NovaStyle.muted)
                }.padding(17).frame(maxWidth: .infinity, alignment: .leading)
                    .background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 18))
            }.padding(.horizontal, 18).padding(.bottom, 28)
        }
        .background(NovaStyle.background.ignoresSafeArea())
        .toolbar(.hidden, for: .navigationBar)
    }

    private func hero(_ film: Film) -> some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: [film.tint.opacity(0.85), NovaStyle.surface, NovaStyle.background],
                           startPoint: .topTrailing, endPoint: .bottomLeading)
            Image(systemName: film.symbol).font(.system(size: 125, weight: .ultraLight))
                .foregroundStyle(.white.opacity(0.19)).frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topTrailing).padding(24)
            VStack(alignment: .leading, spacing: 12) {
                Text("ĐỀ XUẤT").font(.caption2.weight(.bold)).tracking(2).foregroundStyle(.white.opacity(0.8))
                Text(film.title).font(.system(size: 28, weight: .heavy)).lineLimit(2)
                Text("\(film.year)   •   \(film.genre)").font(.caption).foregroundStyle(.white.opacity(0.8))
                HStack {
                    Button { chosenFilm = film } label: { Label("Chi tiết", systemImage: "info.circle") }
                        .buttonStyle(.borderedProminent)
                    Button { openFilm(film) } label: { Label("Phát", systemImage: "play.fill") }
                        .buttonStyle(.bordered)
                }
            }.padding(21)
        }
        .frame(height: 285)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    private func rail(_ name: String, items: [Film]) -> some View {
        VStack(alignment: .leading, spacing: 13) {
            HStack { Text(name).font(.title3.bold()); Spacer(); Image(systemName: "chevron.right").font(.caption).foregroundStyle(NovaStyle.muted) }
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .top, spacing: 12) {
                    ForEach(items) { film in
                        Button { chosenFilm = film } label: { poster(film) }
                            .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func poster(_ film: Film) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            ZStack {
                LinearGradient(colors: [film.tint.opacity(0.72), NovaStyle.surface], startPoint: .topLeading, endPoint: .bottomTrailing)
                Image(systemName: film.symbol).font(.system(size: 45)).foregroundStyle(.white.opacity(0.8))
                VStack { Spacer(); HStack { Text(film.genre).font(.caption2.bold()); Spacer() }.padding(9) }
            }.frame(width: 130, height: 177).clipShape(RoundedRectangle(cornerRadius: 13))
            Text(film.title).font(.caption.weight(.semibold)).lineLimit(2).frame(width: 130, alignment: .leading)
            Text(film.year).font(.caption2).foregroundStyle(NovaStyle.muted)
        }
    }

    private var search: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                TextField("Tên phim hoặc tên nguồn", text: $searchText)
                    .textInputAutocapitalization(.never)
                    .padding(13).background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 12))
                Text("Khám phá").font(.title3.bold())
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 16) {
                    ForEach(matches) { film in
                        Button { chosenFilm = film } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                poster(film)
                                Text(film.genre).font(.caption2).foregroundStyle(NovaStyle.muted)
                            }.frame(maxWidth: .infinity, alignment: .leading)
                        }.buttonStyle(.plain)
                    }
                }
            }.padding(18)
        }.background(NovaStyle.background.ignoresSafeArea()).navigationTitle("Tìm kiếm")
    }

    private var library: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Phim đã lưu").font(.title3.bold())
                let items = films.filter { favorites.contains($0.id) }
                if items.isEmpty {
                    ContentUnavailableView("Chưa có phim yêu thích", systemImage: "bookmark",
                                           description: Text("Mở chi tiết phim và nhấn Lưu để thêm vào thư viện."))
                } else {
                    ForEach(items) { film in
                        Button { chosenFilm = film } label: { filmRow(film) }.buttonStyle(.plain)
                    }
                }
            }.padding(18)
        }.background(NovaStyle.background.ignoresSafeArea()).navigationTitle("Thư viện")
    }

    private func filmRow(_ film: Film) -> some View {
        HStack(spacing: 13) {
            Image(systemName: film.symbol).font(.largeTitle).frame(width: 55, height: 72)
                .background(film.tint.opacity(0.30), in: RoundedRectangle(cornerRadius: 10))
            VStack(alignment: .leading, spacing: 6) {
                Text(film.title).font(.headline)
                Text("\(film.year) · \(film.genre)").font(.caption).foregroundStyle(NovaStyle.muted)
            }
            Spacer()
            Image(systemName: "chevron.right").foregroundStyle(NovaStyle.muted)
        }.padding(10).background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 14))
    }

    private var sources: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 17) {
                Text("Nguồn & Add-on").font(.title3.bold())
                Text("Giao diện quản lý nguồn. Manifest được lưu trên iPhone; bước đồng bộ catalog sẽ tích hợp riêng. Không dùng backend Render.")
                    .font(.subheadline).foregroundStyle(NovaStyle.muted)
                HStack {
                    TextField("https://.../manifest.json", text: $manifestURL)
                        .textInputAutocapitalization(.never).keyboardType(.URL).autocorrectionDisabled()
                    Button("Thêm") { addManifest() }.disabled(!validManifest)
                }.padding(12).background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 13))
                ForEach(manifestStore.split(separator: "\n").map(String.init), id: \.self) { url in
                    HStack {
                        Image(systemName: "link.circle.fill").foregroundStyle(NovaStyle.accent)
                        Text(url).font(.caption).lineLimit(2)
                        Spacer()
                        Button { removeManifest(url) } label: { Image(systemName: "trash") }
                    }.padding(12).background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 12))
                }
                Text("Nguồn mẫu").font(.headline)
                ForEach(films) { film in
                    Button { chosenFilm = film } label: { filmRow(film) }.buttonStyle(.plain)
                }
                Button { showingPlayer = true } label: {
                    Label("Mở player và nhập URL video", systemImage: "play.rectangle.on.rectangle")
                        .frame(maxWidth: .infinity).padding(12)
                }.buttonStyle(.borderedProminent)
            }.padding(18)
        }.background(NovaStyle.background.ignoresSafeArea()).navigationTitle("Nguồn")
    }

    private var validManifest: Bool {
        guard let url = URL(string: manifestURL), url.scheme == "https" else { return false }
        return url.path.hasSuffix("manifest.json")
    }
    private func addManifest() {
        guard validManifest else { return }
        var items = manifestStore.split(separator: "\n").map(String.init)
        if !items.contains(manifestURL) { items.append(manifestURL) }
        manifestStore = items.joined(separator: "\n")
        manifestURL = ""
    }
    private func removeManifest(_ url: String) {
        manifestStore = manifestStore.split(separator: "\n").map(String.init).filter { $0 != url }.joined(separator: "\n")
    }

    private func detail(_ film: Film) -> some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    ZStack {
                        LinearGradient(colors: [film.tint.opacity(0.8), NovaStyle.background], startPoint: .top, endPoint: .bottom)
                        Image(systemName: film.symbol).font(.system(size: 100)).foregroundStyle(.white.opacity(0.5))
                    }.frame(height: 260).clipShape(RoundedRectangle(cornerRadius: 20))
                    Text(film.title).font(.largeTitle.bold())
                    Text("\(film.year)   •   \(film.genre)").foregroundStyle(NovaStyle.muted)
                    HStack(spacing: 12) {
                        Button { chosenFilm = nil; openFilm(film) } label: {
                            Label("Phát phim", systemImage: "play.fill").frame(maxWidth: .infinity)
                        }.buttonStyle(.borderedProminent)
                        Button { toggleFavorite(film) } label: {
                            Label(favorites.contains(film.id) ? "Đã lưu" : "Lưu",
                                  systemImage: favorites.contains(film.id) ? "bookmark.fill" : "bookmark")
                        }.buttonStyle(.bordered)
                    }
                    Text("Nội dung").font(.headline)
                    Text(film.synopsis).foregroundStyle(NovaStyle.muted)
                    Text("Nguồn phát sẽ được chọn trong player. Giao diện không phụ thuộc vào link video.")
                        .font(.footnote).foregroundStyle(NovaStyle.muted)
                }.padding(18)
            }.background(NovaStyle.background.ignoresSafeArea())
                .navigationTitle("Chi tiết phim").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) {
                    Button("Đóng") { chosenFilm = nil }
                }}
        }.preferredColorScheme(.dark)
    }

    private func toggleFavorite(_ film: Film) {
        var ids = favorites
        if ids.contains(film.id) { ids.remove(film.id) } else { ids.insert(film.id) }
        savedIDs = ids.sorted().joined(separator: "|")
    }
    private func openFilm(_ film: Film) {
        model.lookup(film.id, title: film.title)
        showingPlayer = true
    }

    private var playerScreen: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 17) {
                    if let player = model.player {
                        VideoPlayer(player: player).frame(height: 235)
                            .background(.black, in: RoundedRectangle(cornerRadius: 14))
                        Button("Dừng phát") { model.stop() }.buttonStyle(.bordered)
                    } else {
                        ZStack {
                            RoundedRectangle(cornerRadius: 16).fill(.black)
                            VStack(spacing: 9) {
                                Image(systemName: "play.rectangle").font(.system(size: 49)).foregroundStyle(NovaStyle.accent)
                                Text("Chọn nguồn hoặc nhập liên kết").font(.subheadline)
                            }
                        }.frame(height: 220)
                    }
                    Text(model.currentTitle.isEmpty ? "NovaPlay Player" : model.currentTitle).font(.title2.bold())
                    if model.isLoading { ProgressView() }
                    Text(model.status).font(.footnote).foregroundStyle(NovaStyle.muted).textSelection(.enabled)
                    if !model.options.isEmpty {
                        Text("Nguồn phát").font(.headline)
                        ForEach(Array(model.options.enumerated()), id: \.offset) { _, option in
                            Button { model.play(option) } label: {
                                HStack { Image(systemName: "play.circle.fill"); Text(option.title); Spacer(); Image(systemName: "chevron.right") }
                                    .padding(13).background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 12))
                            }.buttonStyle(.plain)
                        }
                    }
                    Text("Phát từ đường dẫn của bạn").font(.headline)
                    TextField("https://.../video.m3u8 hoặc .mp4", text: $manualURL)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .padding(13).background(NovaStyle.surface, in: RoundedRectangle(cornerRadius: 12))
                    Button { model.playDirect(manualURL) } label: {
                        Label("Phát URL", systemImage: "play.fill").frame(maxWidth: .infinity)
                    }.buttonStyle(.borderedProminent)
                    Text("Player hỗ trợ đường dẫn HTTPS; video phải hợp lệ và được nguồn cho phép truy cập. Không chuyển tiếp qua Render.")
                        .font(.caption).foregroundStyle(NovaStyle.muted)
                }.padding(18)
            }.background(NovaStyle.background.ignoresSafeArea())
                .navigationTitle("Trình phát").navigationBarTitleDisplayMode(.inline)
                .toolbar { ToolbarItem(placement: .topBarTrailing) {
                    Button("Đóng") { model.stop(); showingPlayer = false }
                }}
        }.preferredColorScheme(.dark)
    }
}
