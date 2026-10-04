import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(OrientationLockAppDelegate.self) private var appDelegate

    init() {
        if ProcessInfo.processInfo.environment["IVYPLAY_AV01_SMOKE"] == "1" {
            Task {
                do {
                    let resolved = try await AV01DirectResolver.resolve(
                        "https://missav-uimx.onrender.com/av01/221293/master.m3u8"
                    )
                    let attrs = try FileManager.default.attributesOfItem(atPath: resolved.localPlaylistURL.path)
                    let size = (attrs[.size] as? NSNumber)?.intValue ?? 0
                    if size > 0 {
                        print("[AV01_SMOKE] PASS playlistBytes=\(size) exp=\(resolved.tokenExpiresAt)")
                    } else {
                        print("[AV01_SMOKE] FAIL empty-playlist")
                    }
                } catch {
                    print("[AV01_SMOKE] FAIL \(error.localizedDescription)")
                }
            }
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .preferredColorScheme(.dark)
                .onOpenURL { url in
                    AppUrlBridgeKt.handleAppUrl(url: url.absoluteString)
                }
        }
    }
}
