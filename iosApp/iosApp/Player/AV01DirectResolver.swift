import Foundation

struct AV01ResolvedPlayback {
    let localPlaylistURL: URL
    let tokenExpiresAt: TimeInterval
    let requestHeaders: [String: String]
    // Determined from the response body we parsed as an EXT-M3U media playlist, not from the URL suffix.
    let detectedDemuxer: String?
}

enum AV01DirectResolver {
    private static let av01Host = "www.av01.media"
    private static let cdnHostSuffix = "iw01.xyz"
    private static let userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 Version/18.0 Mobile/15E148 Safari/604.1"

    static func canResolve(_ urlString: String) -> Bool {
        guard let url = URL(string: urlString) else { return false }
        if url.host?.lowercased() == av01Host,
           url.path.contains("/api/v1/videos/"),
           url.path.hasSuffix("/manifest/master.m3u8") {
            return true
        }
        let parts = url.path.split(separator: "/")
        return parts.count >= 3 && parts[0] == "av01" && Int(parts[1]) != nil && parts.last == "master.m3u8"
    }

    static func resolve(_ source: String) async throws -> AV01ResolvedPlayback {
        guard let sourceURL = URL(string: source),
              let videoID = extractVideoID(sourceURL) else {
            throw NSError(domain: "AV01DirectResolver", code: 1, userInfo: [NSLocalizedDescriptionKey: "Unsupported AV01 stream URL"])
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
            let expiresValue = geo["expires"],
            !tokenV2.isEmpty
        else {
            throw NSError(domain: "AV01DirectResolver", code: 2, userInfo: [NSLocalizedDescriptionKey: "AV01 geo response missing token_v2/expires"])
        }
        let expires = String(describing: expiresValue)

        var accessComponents = URLComponents(string: "https://customers.iw01.xyz/api/v1/videos/\(videoID)/cdn-access")!
        accessComponents.queryItems = [
            URLQueryItem(name: "token_v2", value: tokenV2),
            URLQueryItem(name: "expires", value: expires),
            URLQueryItem(name: "ip", value: geo["ip"].map { String(describing: $0) })
        ].filter { $0.value != nil }

        var accessRequest = URLRequest(url: accessComponents.url!)
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

        let masterURL = URL(string: "https://www.av01.media/api/v1/videos/\(videoID)/manifest/master.m3u8")!
        var masterRequest = URLRequest(url: masterURL)
        masterRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        masterRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (masterData, masterResponse) = try await session.data(for: masterRequest)
        try requireHTTP(masterResponse, "master.m3u8")
        guard let master = String(data: masterData, encoding: .utf8), !master.isEmpty else {
            throw NSError(domain: "AV01DirectResolver", code: 4, userInfo: [NSLocalizedDescriptionKey: "AV01 master playlist empty"])
        }

        guard let mediaURL = chooseHighestBandwidthVariant(master, baseURL: masterURL) else {
            throw NSError(domain: "AV01DirectResolver", code: 5, userInfo: [NSLocalizedDescriptionKey: "AV01 master has no playable variant"])
        }

        var mediaRequest = URLRequest(url: mediaURL)
        mediaRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        mediaRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (mediaData, mediaResponse) = try await session.data(for: mediaRequest)
        try requireHTTP(mediaResponse, "media.m3u8")
        guard let media = String(data: mediaData, encoding: .utf8), !media.isEmpty else {
            throw NSError(domain: "AV01DirectResolver", code: 6, userInfo: [NSLocalizedDescriptionKey: "AV01 media playlist empty"])
        }

        let signed = rewritePlaylist(media, baseURL: mediaURL, token: accessToken)
        guard let firstObject = firstPlayableObject(in: signed, baseURL: mediaURL) else {
            throw NSError(domain: "AV01DirectResolver", code: 7, userInfo: [NSLocalizedDescriptionKey: "AV01 media playlist contains no init/segment URI"])
        }

        var probeRequest = URLRequest(url: firstObject)
        probeRequest.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        probeRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (_, probeResponse) = try await session.data(for: probeRequest)
        try requireHTTP(probeResponse, "first media object")

        let dir = FileManager.default.temporaryDirectory.appendingPathComponent("av01-playback", isDirectory: true)
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        let file = dir.appendingPathComponent("\(videoID)-\(UUID().uuidString).m3u8")
        guard let signedData = signed.data(using: .utf8) else {
            throw NSError(domain: "AV01DirectResolver", code: 8, userInfo: [NSLocalizedDescriptionKey: "Unable to encode signed AV01 playlist"])
        }
        try signedData.write(to: file, options: .atomic)

        return AV01ResolvedPlayback(
            localPlaylistURL: file,
            tokenExpiresAt: exp,
            requestHeaders: [
                "User-Agent": userAgent,
                "Referer": "https://www.av01.media/"
            ],
            detectedDemuxer: media.contains("#EXTM3U") ? "hls" : nil
        )
    }

    private static func extractVideoID(_ url: URL) -> String? {
        let parts = url.path.split(separator: "/")
        if let apiIndex = parts.firstIndex(of: "api"),
           apiIndex + 3 < parts.count,
           parts[apiIndex + 1] == "v1",
           parts[apiIndex + 2] == "videos",
           Int(parts[apiIndex + 3]) != nil {
            return String(parts[apiIndex + 3])
        }
        if parts.count >= 3, parts[0] == "av01", Int(parts[1]) != nil {
            return String(parts[1])
        }
        return nil
    }

    private static func chooseHighestBandwidthVariant(_ master: String, baseURL: URL) -> URL? {
        let lines = master.split(whereSeparator: { $0.isNewline }).map(String.init)
        var best: (bandwidth: Int, url: URL)?
        var pendingBandwidth = 0
        for rawLine in lines {
            let line = rawLine.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.hasPrefix("#EXT-X-STREAM-INF:") {
                pendingBandwidth = attributeInt("BANDWIDTH", in: line) ?? 0
                continue
            }
            guard pendingBandwidth > 0,
                  !line.isEmpty,
                  !line.hasPrefix("#"),
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
        text.split(omittingEmptySubsequences: false, whereSeparator: { $0.isNewline }).map { raw in
            var line = String(raw)

            var searchStart = line.startIndex
            while let marker = line.range(of: "URI=\"", range: searchStart..<line.endIndex) {
                let valueStart = marker.upperBound
                guard let quote = line[valueStart...].firstIndex(of: "\"") else { break }
                let rawValue = String(line[valueStart..<quote])
                guard let url = URL(string: rawValue, relativeTo: baseURL)?.absoluteURL else { break }
                let signed = signedURL(url, token: token).absoluteString
                line.replaceSubrange(valueStart..<quote, with: signed)
                searchStart = line.index(valueStart, offsetBy: signed.count, limitedBy: line.endIndex) ?? line.endIndex
            }

            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty,
                  !trimmed.hasPrefix("#"),
                  let url = URL(string: trimmed, relativeTo: baseURL)?.absoluteURL else {
                return line
            }
            return signedURL(url, token: token).absoluteString
        }.joined(separator: "\n")
    }

    private static func firstPlayableObject(in playlist: String, baseURL: URL) -> URL? {
        for rawLine in playlist.split(whereSeparator: { $0.isNewline }) {
            let line = String(rawLine).trimmingCharacters(in: .whitespacesAndNewlines)
            if let marker = line.range(of: "URI=\"") {
                let valueStart = marker.upperBound
                if let quote = line[valueStart...].firstIndex(of: "\"") {
                    let value = String(line[valueStart..<quote])
                    if let url = URL(string: value, relativeTo: baseURL)?.absoluteURL {
                        return url
                    }
                }
            }
            if !line.isEmpty, !line.hasPrefix("#"), let url = URL(string: line, relativeTo: baseURL)?.absoluteURL {
                return url
            }
        }
        return nil
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
