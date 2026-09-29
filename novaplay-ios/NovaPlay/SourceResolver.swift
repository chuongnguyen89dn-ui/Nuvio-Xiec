import Foundation
import AVKit

struct MovieSource: Identifiable {
    let id: String
    let title: String
    let detail: String
}

struct PlaybackTarget {
    let url: URL
    let referer: String?
    let origin: String?
    let title: String
}

enum SourceError: LocalizedError {
    case missing(String)
    case invalid(String)
    var errorDescription: String? {
        switch self {
        case .missing(let reason), .invalid(let reason): return reason
        }
    }
}

final class SourceResolver {
    private let root = "https://raw.githubusercontent.com/chuongnguyen89dn-ui/"
    private let session: URLSession = {
        let configuration = URLSessionConfiguration.default
        configuration.timeoutIntervalForRequest = 18
        configuration.httpCookieAcceptPolicy = .always
        return URLSession(configuration: configuration)
    }()

    let examples: [MovieSource] = [
        .init(id: "xiec", title: "XemXiec · MIKR-112", detail: "Phim chính #1/#2 từ dữ liệu GitHub, không dùng trailer."),
        .init(id: "phimhd", title: "PhimHD · Biên Niên Sử Giáng Sinh 2", detail: "HLS từ kiểm thử RoPhim/StreamVSMov ngày 25/09."),
        .init(id: "missav", title: "MissAV · FTHTD-219", detail: "HLS 1080p kèm Referer/Origin trên iPhone."),
        .init(id: "ikisoda", title: "IkiSoda · HSM-061", detail: "Lấy get_file 1080p từ trang nguồn thực tế, không dùng token cũ.")
    ]

    private func json(_ address: String) async throws -> Any {
        guard let url = URL(string: address) else { throw SourceError.invalid("URL dữ liệu không hợp lệ") }
        var request = URLRequest(url: url)
        request.cachePolicy = .reloadIgnoringLocalCacheData
        let (data, response) = try await session.data(for: request)
        guard let r = response as? HTTPURLResponse, r.statusCode == 200 else {
            throw SourceError.missing("Không thể đọc dữ liệu GitHub (HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0))")
        }
        return try JSONSerialization.jsonObject(with: data)
    }

    private func asObjects(_ value: Any) -> [[String: Any]] {
        if let array = value as? [[String: Any]] { return array }
        if let d = value as? [String: Any] { return d["movies"] as? [[String: Any]] ?? [] }
        return []
    }

    private func goodURL(_ value: Any?) -> URL? {
        guard let s = value as? String, let url = URL(string: s), url.scheme?.lowercased() == "https" else { return nil }
        return url
    }

    func resolve(_ source: MovieSource) async throws -> [PlaybackTarget] {
        switch source.id {
        case "xiec":
            let rows = asObjects(try await json(root + "XemXiec/main/ket_qua_1500_phim.json"))
            guard let movie = rows.first(where: { ($0["code"] as? String)?.uppercased() == "MIKR-112" }) else {
                throw SourceError.missing("Không có MIKR-112 trong dataset")
            }
            let streams = movie["streams"] as? [[String: Any]] ?? []
            var targets: [PlaybackTarget] = []
            var seen = Set<String>()
            for (index, stream) in streams.enumerated() {
                if let url = goodURL(stream["url"]), seen.insert(url.absoluteString).inserted {
                    targets.append(PlaybackTarget(url: url, referer: nil, origin: nil, title: stream["name"] as? String ?? "#\(index + 1)"))
                }
            }
            for key in ["manifest_url", "mp4_url"] {
                if let url = goodURL(movie[key]), seen.insert(url.absoluteString).inserted {
                    targets.append(PlaybackTarget(url: url, referer: nil, origin: nil, title: key))
                }
            }
            if targets.isEmpty { throw SourceError.missing("Bản ghi thiếu URL phim chính") }
            return targets

        case "phimhd":
            // Taken from browser_playback_verification.json in the phimHD repository.
            let url = URL(string: "https://v10.streamvsmov.com/stream/8837a59a-be70-4cbd-8d2e-ea6cf1dc99ec/master.m3u8")!
            return [PlaybackTarget(url: url, referer: "https://rophims.team/", origin: "https://rophims.team", title: "HLS · source verified 25/09")]

        case "missav":
            let rows = asObjects(try await json(root + "missav/main/data/catalog-verified.json"))
            guard let movie = rows.first(where: { ($0["code"] as? String)?.uppercased() == "FTHTD-219" }),
                  let entries = movie["streams_1080"] as? [[String: Any]],
                  let entry = entries.first(where: { ($0["verification"] as? String) == "master_resolution_1080" }),
                  let upstream = goodURL(entry["url"]) else {
                throw SourceError.missing("Không tìm thấy HLS 1080p trong dataset")
            }
            var components = URLComponents(url: upstream, resolvingAgainstBaseURL: false)!
            components.host = "surrit.mrstcdn.store"
            let mirror = components.url ?? upstream
            return [
                PlaybackTarget(url: mirror, referer: "https://missav.ws/", origin: "https://missav.ws", title: "Mirror HLS 1080p"),
                PlaybackTarget(url: upstream, referer: "https://missav.ws/", origin: "https://missav.ws", title: "Origin HLS 1080p")
            ]

        case "ikisoda":
            // Existing source project's documented test page. The BAZX-390 catalog
            // record has no source_page and only an expired signed URL.
            guard let page = goodURL("https://ikisoda.com/videos/hsm-061-hino-akari-s-cosplay-debut-erection-explosion/") else {
                throw SourceError.invalid("Invalid IkiSoda test page")
            }
            var request = URLRequest(url: page)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
            request.setValue("https://ikisoda.com/", forHTTPHeaderField: "Referer")
            let (htmlData, response) = try await session.data(for: request)
            guard (response as? HTTPURLResponse)?.statusCode == 200,
                  let html = String(data: htmlData, encoding: .utf8) else {
                throw SourceError.missing("IkiSoda page unavailable; check status in device log")
            }
            let decoded = html.replacingOccurrences(of: "\\/", with: "/").replacingOccurrences(of: "&amp;", with: "&")
            let regex = try NSRegularExpression(pattern: #"https?://ikisoda\.com/get_file/[^"'<>\s]+?22675_1080p\.mp4/?(?:\?[^"'<>\s]*)?"#, options: [.caseInsensitive])
            let range = NSRange(decoded.startIndex..<decoded.endIndex, in: decoded)
            guard let match = regex.firstMatch(in: decoded, range: range),
                  let matchRange = Range(match.range, in: decoded),
                  let mediaURL = URL(string: String(decoded[matchRange])) else {
                throw SourceError.missing("HSM-061: no fresh get_file 1080p on source page")
            }
            var mediaRequest = URLRequest(url: mediaURL)
            mediaRequest.setValue(page.absoluteString, forHTTPHeaderField: "Referer")
            mediaRequest.setValue("https://ikisoda.com", forHTTPHeaderField: "Origin")
            mediaRequest.setValue("bytes=0-1", forHTTPHeaderField: "Range")
            let (_, mediaResponse) = try await session.data(for: mediaRequest)
            guard let http = mediaResponse as? HTTPURLResponse, [200, 206].contains(http.statusCode),
                  let signed = http.url, signed.scheme == "https" else {
                throw SourceError.missing("HSM-061: get_file did not yield an accessible MP4")
            }
            return [PlaybackTarget(url: signed, referer: page.absoluteString, origin: "https://ikisoda.com", title: "HSM-061 · fresh 1080p")]

        default: throw SourceError.invalid("Nguồn chưa được hỗ trợ")
        }
    }
}
