import Foundation
import Network

/// On-device AV01 resolver/proxy for IvyPlay iPhone.
/// The AV01 token and all HLS requests stay on the same iPhone/network.
/// No Render video proxy is used.
final class AV01LocalHLSProxy {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "ivyplay.av01.loopback")
    private var port: UInt16 = 0
    private let session: URLSession = {
        let config = URLSessionConfiguration.ephemeral
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        config.httpCookieAcceptPolicy = .always
        return URLSession(configuration: config)
    }()

    private var token = ""
    private var ro = ""

    func start(id: Int) async throws -> URL {
        stop()
        try await startListener()
        let (accessToken, accessRo) = try await resolveToken(id: id)
        token = accessToken
        ro = accessRo

        let master = URL(string:
            "https://www.av01.media/api/v1/videos/\(id)/manifest/index90-sv3-v1-a1.m3u8"
        )!
        let (data, response) = try await fetch(master)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw AV01Error.upstream("manifest")
        }
        guard let text = String(data: data, encoding: .utf8) else {
            throw AV01Error.invalidPlaylist
        }

        let rewritten = rewritePlaylist(text, base: http.url ?? master)
        print("[IvyPlayAV01] RESOLVED id=\(id) manifest=\(rewritten.utf8.count) token=YES")
        return localURL(for: master, playlist: rewritten)
    }

    func stop() {
        listener?.cancel()
        listener = nil
        port = 0
    }

    private func startListener() async throws {
        let params = NWParameters.tcp
        params.requiredLocalEndpoint = .hostPort(host: .ipv4(.loopback), port: .any)
        let newListener = try NWListener(using: params, on: .any)
        listener = newListener

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            var resumed = false
            newListener.stateUpdateHandler = { [weak self] state in
                guard !resumed else { return }
                switch state {
                case .ready:
                    guard let p = newListener.port?.rawValue else {
                        resumed = true
                        continuation.resume(throwing: AV01Error.unavailable)
                        return
                    }
                    self?.port = p
                    resumed = true
                    continuation.resume()
                case .failed(let error):
                    resumed = true
                    continuation.resume(throwing: error)
                case .cancelled:
                    resumed = true
                    continuation.resume(throwing: AV01Error.unavailable)
                default:
                    break
                }
            }
            newListener.newConnectionHandler = { [weak self] connection in
                self?.handle(connection)
            }
            newListener.start(queue: self.queue)
        }
    }

    private func resolveToken(id: Int) async throws -> (String, String) {
        var geoRequest = URLRequest(url: URL(string: "https://files.iw01.xyz/edge/geo.js?json")!)
        geoRequest.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        geoRequest.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (geoData, geoResponse) = try await session.data(for: geoRequest)
        guard let geoHTTP = geoResponse as? HTTPURLResponse, (200...299).contains(geoHTTP.statusCode) else {
            throw AV01Error.upstream("geo")
        }
        guard
            let geo = try JSONSerialization.jsonObject(with: geoData) as? [String: Any],
            let tokenV2 = geo["token_v2"],
            let expires = geo["expires"],
            let ip = geo["ip"]
        else {
            throw AV01Error.invalidTokenResponse
        }

        var components = URLComponents(string: "https://customers.iw01.xyz/api/v1/videos/\(id)/cdn-access")!
        components.queryItems = [
            URLQueryItem(name: "token_v2", value: String(describing: tokenV2)),
            URLQueryItem(name: "expires", value: String(describing: expires)),
            URLQueryItem(name: "ip", value: String(describing: ip))
        ]
        var request = URLRequest(url: components.url!)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            throw AV01Error.upstream("cdn-access")
        }
        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let accessToken = json["access_token"] as? String,
            !accessToken.isEmpty
        else {
            throw AV01Error.invalidTokenResponse
        }
        return (accessToken, json["ro"] as? String ?? "")
    }

    private func signed(_ url: URL) -> URL {
        guard url.host?.hasSuffix("iw01.xyz") == true else { return url }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)!
        var items = components.queryItems ?? []
        items.removeAll { $0.name == "access_token" || $0.name == "ro" }
        items.append(URLQueryItem(name: "access_token", value: token))
        if !ro.isEmpty { items.append(URLQueryItem(name: "ro", value: ro)) }
        components.queryItems = items
        return components.url!
    }

    private func fetch(_ url: URL) async throws -> (Data, URLResponse) {
        var request = URLRequest(url: signed(url))
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
        request.setValue("https://www.av01.media/", forHTTPHeaderField: "Referer")
        return try await session.data(for: request)
    }

    private func localURL(for remote: URL, playlist: String? = nil) -> URL {
        var components = URLComponents()
        components.scheme = "http"
        components.host = "127.0.0.1"
        components.port = Int(port)
        components.path = "/media"
        components.queryItems = [URLQueryItem(name: "u", value: remote.absoluteString)]
        return components.url!
    }

    private func rewritePlaylist(_ content: String, base: URL) -> String {
        content.components(separatedBy: .newlines).map { line in
            if line.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return line }
            if line.hasPrefix("#") {
                guard let range = line.range(of: #"URI="([^"]+)""#, options: .regularExpression) else { return line }
                let match = String(line[range])
                guard let start = match.firstIndex(of: """),
                      let end = match.lastIndex(of: """),
                      start < end else { return line }
                let raw = String(match[match.index(after: start)..<end])
                guard let absolute = URL(string: raw, relativeTo: base)?.absoluteURL else { return line }
                return line.replacingOccurrences(of: raw, with: localURL(for: absolute).absoluteString)
            }
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty, !trimmed.hasPrefix("#"),
                  let absolute = URL(string: trimmed, relativeTo: base)?.absoluteURL else { return line }
            return localURL(for: absolute).absoluteString
        }.joined(separator: "\n")
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, error in
            guard let self, error == nil, let data,
                  let raw = String(data: data, encoding: .utf8),
                  let line = raw.components(separatedBy: "\r\n").first else {
                connection.cancel()
                return
            }
            let parts = line.split(separator: " ")
            guard parts.count >= 2, parts[0] == "GET",
                  let components = URLComponents(string: "http://localhost" + String(parts[1])),
                  let target = components.queryItems?.first(where: { $0.name == "u" })?.value,
                  let remote = URL(string: target),
                  remote.scheme?.lowercased() == "https" else {
                self.send(connection, status: 400, body: Data("Invalid request".utf8), type: "text/plain")
                return
            }
            let range = raw.components(separatedBy: "\r\n").first(where: {
                $0.lowercased().hasPrefix("range:")
            })?.dropFirst(6).trimmingCharacters(in: .whitespaces)
            Task { await self.forward(connection, remote: remote, range: range) }
        }
    }

    private func forward(_ connection: NWConnection, remote: URL, range: String?) async {
        do {
            let (data, response) = try await fetch(remote)
            guard let http = response as? HTTPURLResponse else { throw AV01Error.unavailable }
            guard (200...299).contains(http.statusCode) else {
                throw AV01Error.upstream(String(http.statusCode))
            }
            var body = data
            var type = http.mimeType ?? "application/octet-stream"
            if let text = String(data: data, encoding: .utf8),
               text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("#EXTM3U") {
                body = Data(rewritePlaylist(text, base: http.url ?? remote).utf8)
                type = "application/vnd.apple.mpegurl"
            }
            send(connection, status: http.statusCode, body: body, type: type)
        } catch {
            print("[IvyPlayAV01] PROXY_ERROR \(error)")
            send(connection, status: 502, body: Data(String(describing: error).utf8), type: "text/plain")
        }
    }

    private func send(_ connection: NWConnection, status: Int, body: Data, type: String) {
        let reason = status == 200 ? "OK" : "Upstream Error"
        var headers = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: \(type)\r\nContent-Length: \(body.count)\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
        var packet = Data(headers.utf8)
        packet.append(body)
        connection.send(content: packet, completion: .contentProcessed { _ in connection.cancel() })
    }

    enum AV01Error: Error, CustomStringConvertible {
        case unavailable
        case upstream(String)
        case invalidTokenResponse
        case invalidPlaylist
        var description: String {
            switch self {
            case .unavailable: return "AV01 proxy unavailable"
            case .upstream(let value): return "AV01 upstream " + value
            case .invalidTokenResponse: return "AV01 token response invalid"
            case .invalidPlaylist: return "AV01 playlist invalid"
            }
        }
    }
}
