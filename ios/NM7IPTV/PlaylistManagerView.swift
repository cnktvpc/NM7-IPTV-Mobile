import SwiftUI
import UniformTypeIdentifiers

struct PlaylistManagerView: View {
    @ObservedObject var store: AppStore
    @Environment(\.dismiss) private var dismiss
    @State private var editor: SourceEditorState?
    @State private var showFileImporter = false

    private var m3uType: UTType { UTType(filenameExtension: "m3u") ?? .plainText }

    var body: some View {
        NavigationStack {
            List {
                Section("Nguồn IPTV") {
                    ForEach(store.sources) { source in
                        HStack(spacing: 10) {
                            Image(systemName: isLocal(source) ? "doc.text.fill" : "link")
                                .foregroundStyle(store.selectedSourceID == source.id ? Color.accentColor : Color.secondary)
                                .frame(width: 24)
                            VStack(alignment: .leading, spacing: 3) {
                                Text(source.name).font(.headline)
                                Text(displayAddress(source))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(2)
                            }
                            Spacer()
                            if store.selectedSourceID == source.id {
                                Image(systemName: "checkmark.circle.fill").foregroundStyle(Color.accentColor)
                            }
                        }
                        .contentShape(Rectangle())
                        .onTapGesture {
                            Task {
                                await store.load(source)
                                if store.errorMessage == nil { dismiss() }
                            }
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            Button(role: .destructive) { store.removeSource(source) } label: {
                                Label("Xóa", systemImage: "trash")
                            }
                            if !isLocal(source) {
                                Button { editor = SourceEditorState(source: source) } label: {
                                    Label("Sửa", systemImage: "pencil")
                                }
                                .tint(.blue)
                            }
                        }
                    }
                }

                Section("Thêm nguồn") {
                    Button {
                        editor = SourceEditorState(source: nil)
                    } label: {
                        Label("Thêm bằng link M3U", systemImage: "link.badge.plus")
                    }
                    Button {
                        showFileImporter = true
                    } label: {
                        Label("Nhập tệp M3U từ Files", systemImage: "doc.badge.plus")
                    }
                }

                if store.selectedSource != nil {
                    Section("Nguồn hiện tại") {
                        Button {
                            Task { await store.reloadCurrent() }
                        } label: {
                            Label(store.isLoading ? "Đang tải lại…" : "Tải lại playlist", systemImage: "arrow.clockwise")
                        }
                        .disabled(store.isLoading)

                        if !store.epgURL.isEmpty {
                            Button {
                                Task { await store.reloadEPG() }
                            } label: {
                                Label(store.isLoadingEPG ? "Đang tải EPG…" : "Tải lại lịch phát sóng (EPG)", systemImage: "calendar.badge.clock")
                            }
                            .disabled(store.isLoadingEPG)
                        }
                    }
                }

                Section {
                    Text("Có thể lưu tối đa 50 playlist. Chạm vào một nguồn để chuyển playlist. Tệp M3U nhập từ Files được sao chép vào vùng dữ liệu riêng của ứng dụng để dùng lại ở lần mở sau.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Quản lý nguồn IPTV")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Đóng") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Button { editor = SourceEditorState(source: nil) } label: {
                            Label("Thêm link M3U", systemImage: "link")
                        }
                        Button { showFileImporter = true } label: {
                            Label("Nhập tệp M3U", systemImage: "doc")
                        }
                    } label: {
                        Image(systemName: "plus")
                    }
                }
            }
            .sheet(item: $editor) { state in
                SourceEditorView(store: store, state: state)
            }
            .fileImporter(
                isPresented: $showFileImporter,
                allowedContentTypes: [m3uType, .plainText],
                allowsMultipleSelection: false
            ) { result in
                switch result {
                case .success(let urls):
                    if let url = urls.first { Task { await store.importPlaylistFile(url) } }
                case .failure(let error):
                    store.errorMessage = "Không mở được tệp: \(error.localizedDescription)"
                }
            }
        }
    }

    private func isLocal(_ source: PlaylistSource) -> Bool {
        URL(string: source.url)?.isFileURL == true
    }

    private func displayAddress(_ source: PlaylistSource) -> String {
        if let url = URL(string: source.url), url.isFileURL { return "Tệp cục bộ • \(url.lastPathComponent)" }
        return source.url
    }
}

private struct SourceEditorState: Identifiable {
    let id = UUID()
    let source: PlaylistSource?
}

private struct SourceEditorView: View {
    @ObservedObject var store: AppStore
    let state: SourceEditorState
    @Environment(\.dismiss) private var dismiss
    @State private var name: String
    @State private var url: String
    @State private var saving = false

    init(store: AppStore, state: SourceEditorState) {
        self.store = store
        self.state = state
        _name = State(initialValue: state.source?.name ?? "")
        _url = State(initialValue: state.source?.url ?? "")
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Tên nguồn") {
                    TextField("Ví dụ: Thể thao", text: $name)
                }
                Section {
                    TextField("https://.../playlist.m3u", text: $url)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                } header: {
                    Text("Link M3U")
                } footer: {
                    Text("Hỗ trợ HTTP/HTTPS. Để mở tệp .m3u trên máy, dùng mục “Nhập tệp M3U từ Files”.")
                }
            }
            .navigationTitle(state.source == nil ? "Thêm playlist" : "Sửa playlist")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Hủy") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(saving ? "Đang lưu…" : "Lưu") {
                        saving = true
                        Task {
                            if let source = state.source {
                                await store.updateSource(source, name: name, url: url)
                            } else {
                                await store.addSource(name: name, url: url)
                            }
                            saving = false
                            if store.errorMessage == nil { dismiss() }
                        }
                    }
                    .disabled(saving || url.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
    }
}
