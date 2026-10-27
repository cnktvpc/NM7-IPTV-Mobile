import Foundation
import Combine

@MainActor
final class AppStore: ObservableObject {
    static let defaultURL = "https://iptv-live-merge.phuongnm7-iptv.workers.dev/playlist.m3u"

    @Published var sources: [PlaylistSource] = []
    @Published var channels: [IPTVChannel] = []
    @Published var selectedSourceID: UUID?
    @Published var selectedGroup = ""
    @Published var searchText = ""
    @Published var section: ChannelSection = .all
    @Published var currentChannelID: String?
    @Published var isLoading = false
    @Published var isLoadingEPG = false
    @Published var errorMessage: String?
    @Published var epgURL = ""
    @Published private(set) var epgLookup: [String: EPGProgramme] = [:]

    private let defaults = UserDefaults.standard
    private let parser = M3UParser()
    private var favorites = Set<String>()
    private var recent: [String] = []

    init() { restoreLocalState() }

    var selectedSource: PlaylistSource? {
        guard let selectedSourceID else { return nil }
        return sources.first { $0.id == selectedSourceID }
    }

    var groups: [String] {
        Array(Set(channels.map(\.group))).sorted { $0.localizedCaseInsensitiveCompare($1) == .orderedAscending }
    }

    var filteredChannels: [IPTVChannel] {
        let query = searchText.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        var result = channels.filter { channel in
            let groupOK = selectedGroup.isEmpty || channel.group == selectedGroup
            let sectionOK: Bool
            switch section {
            case .all: sectionOK = true
            case .favorites: sectionOK = favorites.contains(channel.id)
            case .recent: sectionOK = recent.contains(channel.id)
            }
            let queryOK = query.isEmpty
                || channel.name.lowercased().contains(query)
                || channel.group.lowercased().contains(query)
                || channel.url.lowercased().contains(query)
            return groupOK && sectionOK && queryOK
        }
        if section == .recent {
            let order = Dictionary(uniqueKeysWithValues: recent.enumerated().map { ($0.element, $0.offset) })
            result.sort { (order[$0.id] ?? Int.max) < (order[$1.id] ?? Int.max) }
        }
        return result
    }

    func start() async {
        if sources.isEmpty {
            let source = PlaylistSource(name: "Nguồn IPTV mặc định", url: Self.defaultURL)
            sources = [source]
            selectedSourceID = source.id
            persistSources()
        }
        if selectedSourceID == nil { selectedSourceID = sources.first?.id }
        if let source = selectedSource { await load(source) }
    }

    func reloadCurrent() async {
        guard let source = selectedSource else { return }
        await load(source)
    }

    func load(_ source: PlaylistSource) async {
        guard let url = URL(string: source.url) else {
            errorMessage = "Nguồn playlist không hợp lệ"
            return
        }
        let scheme = url.scheme?.lowercased() ?? ""
        guard ["http", "https", "file"].contains(scheme) else {
            errorMessage = "Nguồn playlist phải là HTTP/HTTPS hoặc tệp M3U đã nhập"
            return
        }

        isLoading = true
        errorMessage = nil
        epgLookup = [:]
        do {
            let data: Data
            let effectiveURL: URL
            if url.isFileURL {
                data = try Data(contentsOf: url, options: [.mappedIfSafe])
                effectiveURL = url
            } else {
                var request = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 35)
                request.setValue("NM7-IPTV-iOS/1.0", forHTTPHeaderField: "User-Agent")
                request.setValue("application/vnd.apple.mpegurl,application/x-mpegURL,text/plain,*/*", forHTTPHeaderField: "Accept")
                let result = try await URLSession.shared.data(for: request)
                data = result.0
                if let http = result.1 as? HTTPURLResponse, !(200..<300).contains(http.statusCode) {
                    throw StoreError.message("HTTP \(http.statusCode)")
                }
                effectiveURL = result.1.url ?? url
            }

            guard data.count <= 20 * 1024 * 1024 else { throw StoreError.message("Playlist lớn hơn 20 MB") }
            guard let text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) else {
                throw StoreError.message("Không đọc được nội dung playlist")
            }
            let parsed = try parser.parse(text, baseURL: effectiveURL)
            guard !parsed.channels.isEmpty else { throw StoreError.message("Playlist không có kênh hợp lệ") }
            channels = parsed.channels
            epgURL = parsed.epgURL
            selectedSourceID = source.id
            selectedGroup = ""
            searchText = ""
            persistSources()
            isLoading = false
            if !epgURL.isEmpty { await reloadEPG(silent: true) }
            return
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }

    func reloadEPG(silent: Bool = false) async {
        guard !epgURL.isEmpty else {
            if !silent { errorMessage = "Playlist hiện tại không khai báo URL EPG/XMLTV" }
            return
        }
        isLoadingEPG = true
        do {
            epgLookup = try await EPGStore.download(epgURL)
        } catch {
            if !silent { errorMessage = error.localizedDescription }
        }
        isLoadingEPG = false
    }

    func programme(for channel: IPTVChannel) -> EPGProgramme? {
        EPGStore.find(channel, in: epgLookup)
    }

    func addSource(name: String, url: String) async {
        let cleanURL = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard M3UParser.isNetworkURL(cleanURL), cleanURL.lowercased().hasPrefix("http") else {
            errorMessage = "Link playlist phải bắt đầu bằng http:// hoặc https://"
            return
        }
        sources.removeAll { $0.url == cleanURL }
        let host = URL(string: cleanURL)?.host ?? "Nguồn IPTV"
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let source = PlaylistSource(name: cleanName.isEmpty ? host : String(cleanName.prefix(80)), url: cleanURL)
        sources.insert(source, at: 0)
        if sources.count > 50 { sources = Array(sources.prefix(50)) }
        persistSources()
        await load(source)
    }

    func importPlaylistFile(_ pickedURL: URL) async {
        let accessed = pickedURL.startAccessingSecurityScopedResource()
        defer { if accessed { pickedURL.stopAccessingSecurityScopedResource() } }
        do {
            let manager = FileManager.default
            let base = try manager.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
                .appendingPathComponent("NM7IPTV", isDirectory: true)
            try manager.createDirectory(at: base, withIntermediateDirectories: true)
            let ext = pickedURL.pathExtension.isEmpty ? "m3u" : pickedURL.pathExtension
            let destination = base.appendingPathComponent("playlist-\(UUID().uuidString).\(ext)")
            try manager.copyItem(at: pickedURL, to: destination)
            let display = pickedURL.deletingPathExtension().lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines)
            let source = PlaylistSource(name: display.isEmpty ? "Playlist đã nhập" : String(display.prefix(80)), url: destination.absoluteString)
            sources.insert(source, at: 0)
            if sources.count > 50 { sources = Array(sources.prefix(50)) }
            persistSources()
            await load(source)
        } catch {
            errorMessage = "Không nhập được tệp M3U: \(error.localizedDescription)"
        }
    }

    func updateSource(_ source: PlaylistSource, name: String, url: String) async {
        guard let index = sources.firstIndex(where: { $0.id == source.id }) else { return }
        let cleanURL = url.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanURL.hasPrefix("https://") || cleanURL.hasPrefix("http://") else {
            errorMessage = "Chỉ có thể sửa URL cho nguồn HTTP/HTTPS. Với tệp cục bộ, hãy nhập lại tệp."
            return
        }
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        sources[index].name = cleanName.isEmpty ? (URL(string: cleanURL)?.host ?? "Nguồn IPTV") : String(cleanName.prefix(80))
        sources[index].url = cleanURL
        persistSources()
        await load(sources[index])
    }

    func removeSource(_ source: PlaylistSource) {
        if let url = URL(string: source.url), url.isFileURL { try? FileManager.default.removeItem(at: url) }
        sources.removeAll { $0.id == source.id }
        if selectedSourceID == source.id {
            selectedSourceID = sources.first?.id
            channels = []
            epgLookup = [:]
            epgURL = ""
            currentChannelID = nil
        }
        persistSources()
    }

    func toggleFavorite(_ channel: IPTVChannel) {
        if favorites.contains(channel.id) { favorites.remove(channel.id) } else { favorites.insert(channel.id) }
        persistFavorites()
        objectWillChange.send()
    }

    func isFavorite(_ channel: IPTVChannel) -> Bool { favorites.contains(channel.id) }

    func recordPlaying(_ channel: IPTVChannel) {
        currentChannelID = channel.id
        recent.removeAll { $0 == channel.id }
        recent.insert(channel.id, at: 0)
        if recent.count > 30 { recent = Array(recent.prefix(30)) }
        defaults.set(recent, forKey: "nm7.recent")
    }

    func clearPlaying() { currentChannelID = nil }

    private func restoreLocalState() {
        if let data = defaults.data(forKey: "nm7.sources"), let value = try? JSONDecoder().decode([PlaylistSource].self, from: data) {
            sources = value
        }
        if sources.isEmpty {
            let source = PlaylistSource(name: "Nguồn IPTV mặc định", url: Self.defaultURL)
            sources = [source]
            selectedSourceID = source.id
        }
        if let id = defaults.string(forKey: "nm7.selectedSource"), let uuid = UUID(uuidString: id), sources.contains(where: { $0.id == uuid }) {
            selectedSourceID = uuid
        } else if selectedSourceID == nil {
            selectedSourceID = sources.first?.id
        }
        favorites = Set(defaults.stringArray(forKey: "nm7.favorites") ?? [])
        recent = defaults.stringArray(forKey: "nm7.recent") ?? []
    }

    private func persistSources() {
        if let data = try? JSONEncoder().encode(sources) { defaults.set(data, forKey: "nm7.sources") }
        defaults.set(selectedSourceID?.uuidString, forKey: "nm7.selectedSource")
    }

    private func persistFavorites() { defaults.set(Array(favorites), forKey: "nm7.favorites") }
}

enum StoreError: LocalizedError {
    case message(String)
    var errorDescription: String? { if case .message(let value) = self { return value }; return nil }
}
