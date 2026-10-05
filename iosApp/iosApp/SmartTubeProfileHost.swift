import SwiftUI
import UIKit
import SmartTubeIOS
import SmartTubeIOSCore

private let nuvioNativeYouTubeProfileVisibleKey = "NuvioNativeYouTubeProfileVisible"
private let nuvioNativeYouTubeProfileVisibilityDidChange = Notification.Name("NuvioNativeYouTubeProfileVisibilityDidChange")

@available(iOS 17.0, *)
private struct NuvioSmartTubeProfileRoot: View {
    @State private var api: InnerTubeAPI
    @State private var authService: AuthService
    @State private var browseViewModel: BrowseViewModel
    @State private var settingsStore: SettingsStore
    @State private var playerStateStore: PlayerStateStore
    @State private var tosPlayerStateStore: TOSPlayerStateStore
    @State private var playerRouter: PlayerRouter
    @State private var cardDownloadService: VideoDownloadService

    init() {
        // Keep SmartTube's own client/bootstrap path. Nuvio does not configure a
        // separate Firebase app, so disable only SmartTube's optional telemetry.
        let settingsStore = SettingsStore()
        CrashlyticsLogger.isEnabled = false

        let poTokenProvider: (any PoTokenProvider)? = {
            if let url = settingsStore.settings.poTokenServiceURL {
                return ServerPoTokenProvider(serviceURL: url)
            }
            return BotGuardClient()
        }()

        let api = InnerTubeAPI(authToken: nil, poTokenProvider: poTokenProvider)
        let authService = AuthService()
        let browseViewModel = BrowseViewModel(api: api)
        let playerStateStore = PlayerStateStore(api: api)
        let tosPlayerStateStore = TOSPlayerStateStore()
        let playerRouter = PlayerRouter(
            playerState: playerStateStore,
            tosState: tosPlayerStateStore,
            settingsStore: settingsStore
        )

        _api = State(initialValue: api)
        _authService = State(initialValue: authService)
        _browseViewModel = State(initialValue: browseViewModel)
        _settingsStore = State(initialValue: settingsStore)
        _playerStateStore = State(initialValue: playerStateStore)
        _tosPlayerStateStore = State(initialValue: tosPlayerStateStore)
        _playerRouter = State(initialValue: playerRouter)
        _cardDownloadService = State(initialValue: VideoDownloadService(api: api))
    }

    var body: some View {
        RootView()
            .environment(authService)
            .environment(browseViewModel)
            .environment(settingsStore)
            .environment(\.innerTubeAPI, api)
            .environment(cardDownloadService)
            .environment(playerStateStore)
            .environment(tosPlayerStateStore)
            .environment(playerRouter)
            .onChange(of: authService.accessToken, initial: true) { _, newToken in
                playerStateStore.vm.updateAuthToken(newToken)
                Task {
                    await api.setAuthToken(newToken)
                    await browseViewModel.updateAuthToken(newToken)
                }
            }
            .onChange(of: authService.sapisid, initial: true) { _, newSapisid in
                playerStateStore.vm.updateSAPISID(newSapisid)
                Task { await api.setSAPISID(newSapisid) }
            }
            .onChange(of: settingsStore.settings.enabledSections, initial: true) { _, newSections in
                browseViewModel.configureSections(newSections)
            }
            .onChange(of: settingsStore.settings.historyState, initial: true) { _, newState in
                browseViewModel.updateHistoryEnabled(newState == .enabled)
            }
            .onChange(of: settingsStore.settings.perDeviceRecommendationsEnabled) { _, enabled in
                Task {
                    if !enabled { await api.resetVisitorData() }
                    browseViewModel.loadContent(refresh: true, source: "nuvioProfileRecommendationsChanged")
                }
            }
            .onAppear {
                authService.handleForeground()
                browseViewModel.refreshIfStale()
            }
    }
}

@MainActor
private final class NuvioSmartTubeOverlayCoordinator {
    static let shared = NuvioSmartTubeOverlayCoordinator()

    private var overlayWindow: UIWindow?
    private weak var previousKeyWindow: UIWindow?

    private init() {
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(profileVisibilityChanged),
            name: nuvioNativeYouTubeProfileVisibilityDidChange,
            object: nil
        )
    }

    func install() {
        // Kotlin is the source of truth. Clear a stale value from a terminated run;
        // the active YouTube profile will publish true again as soon as Compose mounts.
        UserDefaults.standard.set(false, forKey: nuvioNativeYouTubeProfileVisibleKey)
    }

    @objc private func profileVisibilityChanged() {
        let visible = UserDefaults.standard.bool(forKey: nuvioNativeYouTubeProfileVisibleKey)
        visible ? show() : hide()
    }

    private func show() {
        guard overlayWindow == nil else { return }
        guard #available(iOS 17.0, *) else { return }
        guard let scene = UIApplication.shared.connectedScenes
            .compactMap({ $0 as? UIWindowScene })
            .first(where: { $0.activationState == .foregroundActive })
            ?? UIApplication.shared.connectedScenes.compactMap({ $0 as? UIWindowScene }).first
        else { return }

        previousKeyWindow = scene.windows.first(where: { $0.isKeyWindow })

        let root = NuvioSmartTubeProfileRoot()
        let controller = UIHostingController(rootView: root)
        controller.view.backgroundColor = .black

        let window = UIWindow(windowScene: scene)
        window.rootViewController = controller
        window.backgroundColor = .black
        window.windowLevel = .normal + 1
        overlayWindow = window
        window.makeKeyAndVisible()
    }

    private func hide() {
        overlayWindow?.isHidden = true
        overlayWindow?.rootViewController = nil
        overlayWindow = nil
        previousKeyWindow?.makeKey()
    }
}

@_cdecl("NuvioInstallSmartTubeProfileOverlay")
public func NuvioInstallSmartTubeProfileOverlay() {
    Task { @MainActor in
        NuvioSmartTubeOverlayCoordinator.shared.install()
    }
}
