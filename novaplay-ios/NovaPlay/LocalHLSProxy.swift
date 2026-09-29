import Foundation
import Network

/// On-device HTTP loopback. HLS manifests and child requests are fetched using
/// URLSession with per-source headers. No remote proxy or Render bandwidth.
final class LocalHLSProxy {
    private var listener: NWListener?
    private let queue = DispatchQueue(label: "novaplay.loopback")
    private var port: UInt16 = 0
    private var referer: String?
    private var origin: String?
    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 25
        config.httpCookieAcceptPolicy = .always
        return URLSession(configuration: config)
    }()

    func start(referer: String?, origin: String?) async throws {
        stop()
        self.referer = referer
        self.origin = origin
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
                        continuation.resume(throwing: ProxyFailure.unavailable)
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
                    continuation.resume(throwing: ProxyFailure.unavailable)
                default: break
                }
            }
            newListener.newConnectionHandler = { [weak self] connection in
                self?.handle(connection)
            }
            newListener.start(queue: queue)
        }
    }

    func stop() {
        listener?.cancel()
        listener = nil
        port = 0
    }

    func localURL(for remote: URL) -> URL {
        var parts = URLComponents()
        parts.scheme = "http"
        parts.host = "127.0.0.1"
        parts.port = Int(port)
        parts.path = "/media"
        parts.queryItems = [URLQueryItem(name: "u", value: remote.absoluteString)]
        return parts.url!
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        connection.receive(minimumIncompleteLength: 1, maximumLength: 65536) { [weak self] data, _, _, error in
            guard let self = self, error == nil, let data = data,
                  let raw = String(data: data, encoding: .utf8),
                  let requestLine = raw.components(separatedBy: "\r\n").first else {
                connection.cancel()
                return
            }
            let pieces = requestLine.split(separator: " ")
            guard pieces.count >= 2, pieces[0] == "GET",
                  let path = String(pieces[1]).addingPercentEncoding(withAllowedCharacters: .urlFragmentAllowed),
                  let url = URLComponents(string: "http://localhost" + path),
                  let remoteString = url.queryItems?.first(where: { $0.name == "u" })?.value,
                  let remote = URL(string: remoteString),
                  remote.scheme?.lowercased() == "https" else {
                self.send(connection, status: 400, body: Data("Invalid local request".utf8), contentType: "text/plain")
                return
            }
            let range = raw.components(separatedBy: "\r\n").first(where: { $0.lowercased().hasPrefix("range:") })?
                .dropFirst(6).trimmingCharacters(in: .whitespaces)
            Task { await self.forward(connection, remote: remote, range: range) }
        }
    }

    private func forward(_ connection: NWConnection, remote: URL, range: String?) async {
        do {
            var request = URLRequest(url: remote)
            request.cachePolicy = .reloadIgnoringLocalCacheData
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 18_5 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Safari/604.1", forHTTPHeaderField: "User-Agent")
            if let referer = referer { request.setValue(referer, forHTTPHeaderField: "Referer") }
            if let origin = origin { request.setValue(origin, forHTTPHeaderField: "Origin") }
            if let range = range { request.setValue(range, forHTTPHeaderField: "Range") }
            let (bytes, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw ProxyFailure.unavailable }
            print("[NOVAPLAY_HLS] host=\(remote.host ?? "?") status=\(http.statusCode) bytes=\(bytes.count)")
            guard [200, 206].contains(http.statusCode) else {
                send(connection, status: http.statusCode, body: Data("Upstream HTTP \(http.statusCode)".utf8), contentType: "text/plain")
                return
            }
            var body = bytes
            let mime = http.mimeType ?? "application/octet-stream"
            var contentType = mime
            if let text = String(data: bytes, encoding: .utf8), text.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("#EXTM3U") {
                let base = http.url ?? remote
                body = Data(rewritePlaylist(text, base: base).utf8)
                contentType = "application/vnd.apple.mpegurl"
            }
            let contentRange = http.value(forHTTPHeaderField: "Content-Range")
            send(connection, status: http.statusCode, body: body, contentType: contentType, contentRange: contentRange)
        } catch {
            print("[NOVAPLAY_HLS] error=\(error.localizedDescription)")
            send(connection, status: 502, body: Data(error.localizedDescription.utf8), contentType: "text/plain")
        }
    }

    private func rewritePlaylist(_ content: String, base: URL) -> String {
        let regex = try! NSRegularExpression(pattern: #"URI="([^"]+)""#)
        return content.components(separatedBy: "\n").map { line in
            if line.hasPrefix("#") {
                let ns = line as NSString
                let matches = regex.matches(in: line, range: NSRange(location: 0, length: ns.length))
                var result = line
                for match in matches.reversed() {
                    guard let r = Range(match.range(at: 1), in: result),
                          let absolute = URL(string: String(result[r]), relativeTo: base)?.absoluteURL,
                          absolute.scheme?.lowercased() == "https" else { continue }
                    result.replaceSubrange(r, with: localURL(for: absolute).absoluteString)
                }
                return result
            }
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty,
                  let absolute = URL(string: trimmed, relativeTo: base)?.absoluteURL,
                  absolute.scheme?.lowercased() == "https" else { return line }
            return localURL(for: absolute).absoluteString
        }.joined(separator: "\n")
    }

    private func send(_ connection: NWConnection, status: Int, body: Data, contentType: String, contentRange: String? = nil) {
        let reason: String
        switch status {
        case 200: reason = "OK"
        case 206: reason = "Partial Content"
        case 400: reason = "Bad Request"
        case 403: reason = "Forbidden"
        case 404: reason = "Not Found"
        default: reason = "Upstream Error"
        }
        var headers = "HTTP/1.1 \(status) \(reason)\r\nContent-Type: \(contentType)\r\nContent-Length: \(body.count)\r\nConnection: close\r\nCache-Control: no-store\r\n"
        if let contentRange = contentRange { headers += "Content-Range: \(contentRange)\r\nAccept-Ranges: bytes\r\n" }
        headers += "\r\n"
        var data = Data(headers.utf8)
        data.append(body)
        connection.send(content: data, completion: .contentProcessed { _ in connection.cancel() })
    }

    enum ProxyFailure: Error { case unavailable }
}
