import Foundation

final class EPGStore: NSObject, XMLParserDelegate {
    private var channelNames: [String: [String]] = [:]
    private var currentByChannel: [String: EPGProgramme] = [:]
    private var currentChannelID = ""
    private var programmeChannelID = ""
    private var programmeStart: Date?
    private var programmeEnd: Date?
    private var programmeTitle = ""
    private var activeTextTag = ""
    private var textBuffer = ""
    private let now = Date()

    static func download(_ address: String) async throws -> [String: EPGProgramme] {
        guard let url = URL(string: address), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
            throw StoreError.message("URL EPG không hợp lệ")
        }
        var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 35)
        request.setValue("NM7-IPTV-iOS/1.0", forHTTPHeaderField: "User-Agent")
        request.setValue("application/xml,text/xml,application/gzip,*/*", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: request)
        if let http = response as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
            throw StoreError.message("EPG HTTP \(http.statusCode)")
        }
        guard data.count <= 40 * 1024 * 1024 else { throw StoreError.message("EPG quá lớn") }
        if data.count >= 2 && data[data.startIndex] == 0x1f && data[data.startIndex + 1] == 0x8b {
            throw StoreError.message("EPG đang ở dạng .gz chưa giải nén. Hãy dùng URL XML/XMLTV hoặc máy chủ có Content-Encoding gzip.")
        }
        let store = EPGStore()
        let parser = XMLParser(data: data)
        parser.delegate = store
        guard parser.parse() else {
            throw parser.parserError ?? StoreError.message("Không đọc được XMLTV EPG")
        }
        return store.makeLookup()
    }

    func parser(_ parser: XMLParser, didStartElement elementName: String, namespaceURI: String?, qualifiedName qName: String?, attributes attributeDict: [String : String] = [:]) {
        activeTextTag = ""
        textBuffer = ""
        switch elementName {
        case "channel":
            currentChannelID = attributeDict["id"]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        case "display-name":
            if !currentChannelID.isEmpty { activeTextTag = "display-name" }
        case "programme":
            programmeChannelID = attributeDict["channel"]?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            programmeStart = Self.parseTime(attributeDict["start"])
            programmeEnd = Self.parseTime(attributeDict["stop"])
            programmeTitle = ""
        case "title":
            if !programmeChannelID.isEmpty { activeTextTag = "title" }
        default:
            break
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        if !activeTextTag.isEmpty { textBuffer += string }
    }

    func parser(_ parser: XMLParser, didEndElement elementName: String, namespaceURI: String?, qualifiedName qName: String?) {
        let clean = textBuffer.trimmingCharacters(in: .whitespacesAndNewlines)
        if elementName == "display-name", activeTextTag == "display-name", !clean.isEmpty, !currentChannelID.isEmpty {
            channelNames[currentChannelID, default: []].append(clean)
        } else if elementName == "title", activeTextTag == "title" {
            programmeTitle = clean
        } else if elementName == "channel" {
            currentChannelID = ""
        } else if elementName == "programme" {
            if let start = programmeStart, let end = programmeEnd, start <= now, now < end, !programmeChannelID.isEmpty {
                currentByChannel[programmeChannelID] = EPGProgramme(
                    title: programmeTitle.isEmpty ? "Chương trình đang phát" : programmeTitle,
                    start: start,
                    end: end
                )
            }
            programmeChannelID = ""
            programmeStart = nil
            programmeEnd = nil
            programmeTitle = ""
        }
        activeTextTag = ""
        textBuffer = ""
    }

    private func makeLookup() -> [String: EPGProgramme] {
        var result: [String: EPGProgramme] = [:]
        for (id, programme) in currentByChannel {
            Self.putAlias(id, programme: programme, into: &result)
            for name in channelNames[id] ?? [] { Self.putAlias(name, programme: programme, into: &result) }
        }
        return result
    }

    static func find(_ channel: IPTVChannel, in lookup: [String: EPGProgramme]) -> EPGProgramme? {
        let candidates = [normalize(channel.tvgId), normalize(channel.name), relaxed(channel.tvgId), relaxed(channel.name)]
        for key in candidates where !key.isEmpty {
            if let value = lookup[key] { return value }
        }
        let target = relaxed(channel.name)
        guard !target.isEmpty else { return nil }
        return lookup.first { key, _ in
            let item = relaxed(key)
            return !item.isEmpty && (target == item || target.contains(item) || item.contains(target))
        }?.value
    }

    private static func putAlias(_ text: String, programme: EPGProgramme, into result: inout [String: EPGProgramme]) {
        let normal = normalize(text)
        if !normal.isEmpty { result[normal] = programme }
        let loose = relaxed(text)
        if !loose.isEmpty { result[loose] = programme }
    }

    private static func normalize(_ text: String) -> String {
        let folded = text.folding(options: [.diacriticInsensitive, .widthInsensitive], locale: .current).lowercased()
        return folded.unicodeScalars.filter { CharacterSet.alphanumerics.contains($0) }.map(String.init).joined()
    }

    private static func relaxed(_ text: String) -> String {
        var value = normalize(text)
        for suffix in ["fullhd", "fhd", "uhd", "4k", "1080p", "720p", "hd", "television", "channel"] where value.hasSuffix(suffix) {
            value.removeLast(suffix.count)
            break
        }
        for prefix in ["kenh", "channel"] where value.hasPrefix(prefix) {
            value.removeFirst(prefix.count)
            break
        }
        return value
    }

    private static func parseTime(_ value: String?) -> Date? {
        guard let value else { return nil }
        let clean = value.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
        for format in ["yyyyMMddHHmmss Z", "yyyyMMddHHmm Z", "yyyyMMddHHmmss", "yyyyMMddHHmm"] {
            let formatter = DateFormatter()
            formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = format
            formatter.isLenient = false
            if !format.contains("Z") { formatter.timeZone = .current }
            if let date = formatter.date(from: clean) { return date }
        }
        return nil
    }
}
