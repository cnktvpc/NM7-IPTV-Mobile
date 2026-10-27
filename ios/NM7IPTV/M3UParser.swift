import Foundation

struct M3UParser {
    private static let attributeRegex = try! NSRegularExpression(
        pattern: #"([\w-]+)\s*=\s*(?:\"([^\"]*)\"|'([^']*)'|([^\s,]+))"#,
        options: []
    )

    func parse(_ rawContent: String, baseURL: URL?) throws -> ParsedPlaylist {
        let content = rawContent.replacingOccurrences(of: "\u{FEFF}", with: "")
        let trimmed = content.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("<") || content.contains("\0") {
            throw ParserError.invalidPlaylist("Nội dung không phải playlist M3U.")
        }
        if content.range(of: #"(?m)^\s*#EXT-X-"#, options: .regularExpression) != nil {
            guard let baseURL, ["http", "https"].contains(baseURL.scheme?.lowercased() ?? "") else {
                throw ParserError.invalidPlaylist("Đây là manifest HLS. Hãy dùng URL gốc của luồng.")
            }
            let channel = IPTVChannel(
                name: "Luồng HLS", group: "Phát trực tiếp", url: baseURL.absoluteString,
                logo: "", tvgId: "", headers: [:],
                options: ["#KODIPROP:inputstream.adaptive.manifest_type=hls"], originalExtInf: ""
            )
            return ParsedPlaylist(channels: [channel], duplicateCount: 0, missingURLCount: 0, epgURL: "")
        }

        let lines = content.components(separatedBy: .newlines)
        var channels: [IPTVChannel] = []
        var seen = Set<String>()
        var duplicates = 0
        var missing = 0
        var epgURL = ""
        var pending: Metadata?
        var pendingHeaders: [String: String] = [:]
        var pendingOptions: [String] = []

        for raw in lines {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !line.isEmpty else { continue }

            if line.lowercased().hasPrefix("#extm3u") {
                let attrs = attributes(in: line)
                if let value = attrs["url-tvg"] ?? attrs["x-tvg-url"] {
                    let first = value.split(separator: ",", maxSplits: 1).first.map(String.init) ?? value
                    epgURL = resolve(first.trimmingCharacters(in: .whitespaces), relativeTo: baseURL)
                }
                continue
            }

            if line.lowercased().hasPrefix("#extinf:") {
                if pending != nil { missing += 1 }
                pending = parseMetadata(line)
                pendingHeaders.removeAll(keepingCapacity: true)
                pendingOptions.removeAll(keepingCapacity: true)
                continue
            }

            if line.lowercased().hasPrefix("#extgrp:"), var metadata = pending {
                metadata.group = String(line.dropFirst(8)).trimmingCharacters(in: .whitespacesAndNewlines)
                pending = metadata
                continue
            }

            if line.lowercased().hasPrefix("#extvlcopt:") {
                let option = String(line.dropFirst(11))
                if !parseHeaderOption(option, into: &pendingHeaders), pending != nil {
                    pendingOptions.append(line)
                }
                continue
            }

            if line.hasPrefix("#") {
                if pending != nil { pendingOptions.append(line) }
                continue
            }

            let split = splitURLAndHeaders(line)
            let resolvedURL = resolve(split.url, relativeTo: baseURL)
            var headers = pendingHeaders
            headers.merge(split.headers) { _, new in new }
            let metadata = pending ?? Metadata()
            let name = metadata.name.isEmpty ? fallbackName(resolvedURL) : metadata.name

            guard isNetworkURL(resolvedURL) else {
                missing += 1
                pending = nil
                pendingHeaders.removeAll(keepingCapacity: true)
                pendingOptions.removeAll(keepingCapacity: true)
                continue
            }

            let channel = IPTVChannel(
                name: clean(name, fallback: "Kênh không tên"),
                group: clean(metadata.group, fallback: "Chưa phân nhóm"),
                url: resolvedURL,
                logo: metadata.logo.trimmingCharacters(in: .whitespacesAndNewlines),
                tvgId: metadata.tvgId.trimmingCharacters(in: .whitespacesAndNewlines),
                headers: headers,
                options: pendingOptions,
                originalExtInf: metadata.original
            )
            if seen.insert(channel.id).inserted { channels.append(channel) } else { duplicates += 1 }
            pending = nil
            pendingHeaders.removeAll(keepingCapacity: true)
            pendingOptions.removeAll(keepingCapacity: true)
        }

        if pending != nil { missing += 1 }
        return ParsedPlaylist(channels: channels, duplicateCount: duplicates, missingURLCount: missing, epgURL: epgURL)
    }

    private func parseMetadata(_ line: String) -> Metadata {
        let comma = nameComma(in: line)
        let attrPart = comma.map { String(line[..<$0]) } ?? line
        let name = comma.map { String(line[line.index(after: $0)...]).trimmingCharacters(in: .whitespacesAndNewlines) } ?? ""
        let attrs = attributes(in: attrPart)
        return Metadata(
            original: line,
            name: name.isEmpty ? (attrs["tvg-name"] ?? "") : name,
            group: attrs["group-title"] ?? "",
            logo: attrs["tvg-logo"] ?? "",
            tvgId: attrs["tvg-id"] ?? ""
        )
    }

    private func attributes(in text: String) -> [String: String] {
        let ns = text as NSString
        let range = NSRange(location: 0, length: ns.length)
        var result: [String: String] = [:]
        Self.attributeRegex.enumerateMatches(in: text, options: [], range: range) { match, _, _ in
            guard let match, match.numberOfRanges >= 5 else { return }
            let key = ns.substring(with: match.range(at: 1)).lowercased()
            for index in 2...4 where match.range(at: index).location != NSNotFound {
                result[key] = ns.substring(with: match.range(at: index))
                break
            }
        }
        return result
    }

    private func nameComma(in line: String) -> String.Index? {
        var quoted = false
        var quote: Character?
        for index in line.indices {
            let char = line[index]
            if (char == "\"" || char == "'") && (!quoted || quote == char) {
                quoted.toggle()
                quote = quoted ? char : nil
            } else if char == "," && !quoted {
                return index
            }
        }
        return nil
    }

    private func parseHeaderOption(_ option: String, into headers: inout [String: String]) -> Bool {
        guard let equals = option.firstIndex(of: "=") else { return false }
        let key = option[..<equals].trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let value = option[option.index(after: equals)...].trimmingCharacters(in: .whitespacesAndNewlines)
        switch key {
        case "http-user-agent": headers["User-Agent"] = value
        case "http-referrer", "http-referer": headers["Referer"] = value
        case "http-origin": headers["Origin"] = value
        default: return false
        }
        return true
    }

    private func splitURLAndHeaders(_ input: String) -> (url: String, headers: [String: String]) {
        guard let pipe = input.firstIndex(of: "|") else {
            return (input.trimmingCharacters(in: .whitespacesAndNewlines), [:])
        }
        let url = input[..<pipe].trimmingCharacters(in: .whitespacesAndNewlines)
        let tail = input[input.index(after: pipe)...]
        var headers: [String: String] = [:]
        for pair in tail.split(separator: "&") {
            guard let equals = pair.firstIndex(of: "=") else { continue }
            var key = String(pair[..<equals]).removingPercentEncoding ?? String(pair[..<equals])
            let valuePart = pair[pair.index(after: equals)...]
            let value = String(valuePart).removingPercentEncoding ?? String(valuePart)
            if key.caseInsensitiveCompare("referrer") == .orderedSame { key = "Referer" }
            if key.caseInsensitiveCompare("referer") == .orderedSame { key = "Referer" }
            if key.caseInsensitiveCompare("user-agent") == .orderedSame { key = "User-Agent" }
            if key.caseInsensitiveCompare("origin") == .orderedSame { key = "Origin" }
            guard validHeader(key, value) else { continue }
            headers[key] = value
        }
        return (url, headers)
    }

    private func resolve(_ value: String, relativeTo baseURL: URL?) -> String {
        let value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty else { return "" }
        guard let baseURL else { return value }
        return URL(string: value, relativeTo: baseURL)?.absoluteURL.absoluteString ?? value
    }

    private func fallbackName(_ value: String) -> String {
        guard let url = URL(string: value) else { return "Kênh không tên" }
        let last = url.lastPathComponent
        if !last.isEmpty { return last }
        return url.host ?? "Kênh không tên"
    }

    private func clean(_ value: String, fallback: String) -> String {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? fallback : trimmed
    }

    static func isNetworkURL(_ value: String) -> Bool {
        guard let url = URL(string: value), let scheme = url.scheme?.lowercased(), url.host != nil else { return false }
        return ["http", "https", "rtsp", "rtsps", "udp", "rtmp", "rtmps", "rtp", "srt"].contains(scheme)
    }

    private func isNetworkURL(_ value: String) -> Bool { Self.isNetworkURL(value) }

    private func validHeader(_ key: String, _ value: String) -> Bool {
        guard !key.isEmpty, !value.contains("\r"), !value.contains("\n") else { return false }
        return key.range(of: #"^[!#$%&'*+.^_`|~0-9A-Za-z-]+$"#, options: .regularExpression) != nil
    }

    private struct Metadata {
        var original = ""
        var name = ""
        var group = ""
        var logo = ""
        var tvgId = ""
    }
}

enum ParserError: LocalizedError {
    case invalidPlaylist(String)
    var errorDescription: String? {
        switch self { case .invalidPlaylist(let message): return message }
    }
}
