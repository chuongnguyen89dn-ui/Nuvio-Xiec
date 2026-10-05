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
