import Foundation

struct AV01ResolvedPlayback {
    let localPlaylistURL: URL
    let tokenExpiresAt: TimeInterval
}

enum AV01DirectResolver {
    private static let baseHost = "www.av01.media"
    private static let cdnHostSuffix = "iw01.xyz"
    private static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1"

    static func canResolve(_ urlString: String) -> Bool {
        guard let url = URL(string: urlString),
              url.host?.lowercased() == baseHost else { return false }
        return url.path.contains("/api/v1/videos/") && url.path.hasSuffix("/manifest/master.m3u8")
    }

    static func resolve(_ source: String) async throws -> AV01ResolvedPlayback {
        guard let sourceURL = URL(string: source),
              let videoID = extractVideoID(sourceURL) else {
            throw NSError(domain: "AV01DirectResolver", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unsupported AV01 manifest URL"])
        }

        let session = URLSession(configuration: .ephemeral)
        var geoRequest = URLRequest(url: URL(string: "https://files.iw01.xyz/edge/geo.js?json")!)
        geoRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        geoRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")

        let (geoData, geoResponse) = try await session.data(for: geoRequest)
        try requireHTTP(geoResponse, "geo.js")
        guard
            let geo = try JSONSerialization.jsonObject(with: geoData) as? [String: Any],
            let tokenV2 = geo["token_v2"] as? String,
            let expires = geo["expires"] as? String,
            !tokenV2.isEmpty
        else {
            throw NSError(domain: "AV01DirectResolver", code: 2, userInfo: [NSLocalizedDescriptionKey: "AV01 geo response missing token_v2/expires"])
        }

        var components = URLComponents(string: "https://customers.iw01.xyz/api/v1/videos/\(videoID)/cdn-access")!
        components.queryItems = [
            URLQueryItem(name: "token_v2", value: tokenV2),
            URLQueryItem(name: "expires", value: expires),
            URLQueryItem(name: "ip", value: geo["ip"] as? String)
        ].filter { $0.value != nil }

        var accessRequest = URLRequest(url: components.url!)
        accessRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        accessRequest.setValue("application/json,*/*", forHTTPHeaderField: "Accept")
        accessRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")

        let (accessData, accessResponse) = try await session.data(for: accessRequest)
        try requireHTTP(accessResponse, "cdn-access")
        guard
            let access = try JSONSerialization.jsonObject(with: accessData) as? [String: Any],
            let accessToken = access["access_token"] as? String,
            !accessToken.isEmpty
        else {
            throw NSError(domain: "AV01DirectResolver", code: 3, userInfo: [NSLocalizedDescriptionKey: "AV01 cdn-access returned no access_token"])
        }

        let claims = decodeJWTPayload(accessToken)
        let exp = (claims["exp"] as? NSNumber)?.doubleValue ?? Date().timeIntervalSince1970 + 3600

        var manifestRequest = URLRequest(url: sourceURL)
        manifestRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        manifestRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (masterData, masterResponse) = try await session.data(for: manifestRequest)
        try requireHTTP(masterResponse, "master.m3u8")

        let master = String(data: masterData, encoding: .utf8) ?? ""
        let variant = chooseHighestBandwidthVariant(master, baseURL: sourceURL)
        let mediaURL = variant ?? sourceURL

        var mediaRequest = URLRequest(url: mediaURL)
        mediaRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        mediaRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (mediaData, mediaResponse) = try await session.data(for: mediaRequest)
        try requireHTTP(mediaResponse, "media.m3u8")

        let media = String(data: mediaData, encoding: .utf8) ?? ""
        let signed = rewritePlaylist(media, baseURL: mediaURL, token: accessToken)
        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("av01-playback", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("\(videoID)-\(UUID().uuidString).m3u8")
        try signed.data(using: .utf8)!.write(to: file, options: .atomic)

        return AV01ResolvedPlayback(localPlaylistURL: file, tokenExpiresAt: exp)
    }

    private static func extractVideoID(_ url: URL) -> String? {
        let parts = url.path.split(separator: "/")
        guard let apiIndex = parts.firstIndex(of: "api"),
              apiIndex + 3 < parts.count,
              parts[apiIndex + 1] == "v1",
              parts[apiIndex + 2] == "videos" else { return nil }
        return String(parts[apiIndex + 3])
    }

    private static func chooseHighestBandwidthVariant(_ master: String, baseURL: URL) -> URL? {
        let lines = master.split(whereSeparator: \.isNewline).map(String.init)
        var best: (bandwidth: Int, url: URL)?
        var pendingBandwidth = 0
        for line in lines {
            if line.hasPrefix("#EXT-X-STREAM-INF:") {
                pendingBandwidth = attributeInt("BANDWIDTH", in: line) ?? 0
                continue
            }
            guard pendingBandwidth > 0, !line.hasPrefix("#"),
                  let url = URL(string: line, relativeTo: baseURL)?.absoluteURL else { continue }
            if best == nil || pendingBandwidth > best!.bandwidth {
                best = (pendingBandwidth, url)
            }
            pendingBandwidth = 0
        }
        return best?.url
    }

    private static func attributeInt(_ name: String, in line: String) -> Int? {
        let marker = name + "="
        guard let range = line.range(of: marker) else { return nil }
        let rest = line[range.upperBound...]
        let value = rest.prefix { $0.isNumber }
        return Int(value)
    }

    private static func rewritePlaylist(_ text: String, baseURL: URL, token: String) -> String {
        text.split(separator: "\n", omittingEmptySubsequences: false).map { raw in
            let line = String(raw)
            if line.contains("URI=\"") {
                return line.replacingOccurrences(
                    of: #"URI="([^"]+)""#,
                    with: { match in
                        guard let r = match.range(at: 1),
                              let url = URL(string: String(line[r]), relativeTo: baseURL)?.absoluteURL else { return match }
                        return #"URI="#(signedURL(url, token: token).absoluteString)""#
                    },
                    options: .regularExpression
                )
            }
            guard !line.trimmingCharacters(in: .whitespaces).hasPrefix("#"),
                  let url = URL(string: line.trimmingCharacters(in: .whitespacesAndNewlines), relativeTo: baseURL)?.absoluteURL else {
                return line
            }
            return signedURL(url, token: token).absoluteString
        }.joined(separator: "\n")
    }

    private static func signedURL(_ url: URL, token: String) -> URL {
        guard url.host?.hasSuffix(cdnHostSuffix) == true else { return url }
        guard var components = URLComponents(url: url, resolvingAgainstBaseURL: false) else { return url }
        var items = components.queryItems ?? []
        items.removeAll { $0.name == "access_token" }
        items.append(URLQueryItem(name: "access_token", value: token))
        components.queryItems = items
        return components.url ?? url
    }

    private static func decodeJWTPayload(_ token: String) -> [String: Any] {
        let parts = token.split(separator: ".")
        guard parts.count == 3 else { return [:] }
        var encoded = String(parts[1]).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        guard let data = Data(base64Encoded: encoded),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [:] }
        return json
    }

    private static func requireHTTP(_ response: URLResponse, _ name: String) throws {
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            let code = (response as? HTTPURLResponse)?.statusCode ?? -1
            throw NSError(domain: "AV01DirectResolver", code: code, userInfo: [NSLocalizedDescriptionKey: "AV01 \(name) HTTP \(code)"])
        }
    }
}
