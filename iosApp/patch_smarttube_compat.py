from pathlib import Path

package = Path("SmartTubeIOS/SmartTubeIOS/Package.swift")
text = package.read_text()
text = text.replace("// swift-tools-version:6.0", "// swift-tools-version:5.10")
text = text.replace("swiftSettings: [.swiftLanguageMode(.v6)]", "swiftSettings: []")
package.write_text(text)

loading = Path("SmartTubeIOS/SmartTubeIOS/Sources/SmartTubeIOS/ViewModels/PlaybackViewModel+Loading.swift")
text = loading.read_text()
old = """            tracker.transition(
                to: video.id, cpn: InnerTubeAPI.generateCPN(),
                flushPosition: 0, flushDuration: 0)
"""
new = """            _ = tracker.transition(
                to: video.id, cpn: InnerTubeAPI.generateCPN(),
                flushPosition: 0, flushDuration: 0)
"""
if old not in text:
    raise SystemExit("SmartTube transition patch anchor not found")
loading.write_text(text.replace(old, new, 1))

home = Path("SmartTubeIOS/SmartTubeIOS/Sources/SmartTubeIOS/Views/Home/HomeView.swift")
text = home.read_text()
old = """        let gridVideos = sectionVM.videoGroups.filter { $0.layout != .row }
            .flatMap(\.videos)
            .filter { !applyHideShorts || !$0.isShort }
            .filter { !hideLiveShorts || !($0.isLive && $0.isShort) }
            .filter { !hideVideoPremieres || !$0.isUpcoming }
            .filter { !applyHideWatched || !$0.isWatched(threshold: hideWatchedThreshold) }
            .filter { channelFilter == nil || $0.channelId == channelFilter }
            .filter { isShorts || !$0.isShort }
"""
new = """        var gridVideos = sectionVM.videoGroups.filter { $0.layout != .row }.flatMap(\.videos)
        if applyHideShorts { gridVideos = gridVideos.filter { !$0.isShort } }
        if hideLiveShorts { gridVideos = gridVideos.filter { !($0.isLive && $0.isShort) } }
        if hideVideoPremieres { gridVideos = gridVideos.filter { !$0.isUpcoming } }
        if applyHideWatched { gridVideos = gridVideos.filter { !$0.isWatched(threshold: hideWatchedThreshold) } }
        if let channelFilter { gridVideos = gridVideos.filter { $0.channelId == channelFilter } }
        if !isShorts { gridVideos = gridVideos.filter { !$0.isShort } }
"""
if old not in text:
    raise SystemExit("SmartTube HomeView grid patch anchor not found")
home.write_text(text.replace(old, new, 1))
