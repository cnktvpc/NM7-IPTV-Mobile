import Foundation
import CryptoKit

struct IPTVChannel: Codable, Identifiable, Hashable {
    let name: String
    let group: String
    let url: String
    let logo: String
    let tvgId: String
    let headers: [String: String]
    var options: [String]
    var originalExtInf: String

    var id: String {
        var pieces = [url]
        for key in headers.keys.sorted(by: { $0.lowercased() < $1.lowercased() }) {
            pieces.append(key.lowercased())
            pieces.append(headers[key] ?? "")
        }
        pieces.append(contentsOf: options)
        let digest = SHA256.hash(data: Data(pieces.joined(separator: "\u{001F}").utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    var mimeHint: String {
        for option in options {
            let lower = option.lowercased()
            if lower.hasSuffix("manifest_type=hls") { return "application/x-mpegURL" }
            if lower.hasSuffix("manifest_type=mpd") { return "application/dash+xml" }
            if lower.hasPrefix("#kodiprop:mimetype="), let equals = option.firstIndex(of: "=") {
                return String(option[option.index(after: equals)...]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
        }
        return ""
    }

    var drmKind: String? {
        for option in options {
            let lower = option.lowercased()
            guard lower.contains("inputstream.adaptive.license_type") || lower.contains("inputstream.adaptive.drm_legacy") else { continue }
            if lower.contains("widevine") || lower.contains("com.widevine.alpha") { return "Widevine" }
            if lower.contains("clearkey") || lower.contains("org.w3.clearkey") { return "ClearKey" }
            if lower.contains("playready") || lower.contains("com.microsoft.playready") { return "PlayReady" }
            return "DRM"
        }
        return nil
    }

    static func == (lhs: IPTVChannel, rhs: IPTVChannel) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

struct ParsedPlaylist {
    let channels: [IPTVChannel]
    let duplicateCount: Int
    let missingURLCount: Int
    let epgURL: String
}

struct PlaylistSource: Codable, Identifiable, Equatable {
    var id: UUID
    var name: String
    var url: String

    init(id: UUID = UUID(), name: String, url: String) {
        self.id = id
        self.name = name
        self.url = url
    }
}

enum ChannelSection: String, CaseIterable, Identifiable {
    case all = "Tất cả"
    case favorites = "Yêu thích"
    case recent = "Gần đây"
    var id: String { rawValue }
}

struct EPGProgramme: Equatable {
    let title: String
    let start: Date
    let end: Date

    var progress: Double {
        let span = end.timeIntervalSince(start)
        guard span > 0 else { return 0 }
        return min(1, max(0, Date().timeIntervalSince(start) / span))
    }
}
