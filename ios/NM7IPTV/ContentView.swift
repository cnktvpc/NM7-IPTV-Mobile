import SwiftUI

struct ContentView: View {
    @ObservedObject var store: AppStore
    @ObservedObject var playerStore: PlayerStore
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    @State private var showSources = false
    @State private var showFullscreen = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                header
                sectionBar
                searchBar
                groupBar

                if horizontalSizeClass == .regular, playerStore.currentChannel != nil {
                    HStack(spacing: 0) {
                        playerPanel
                            .frame(minWidth: 360, idealWidth: 520, maxWidth: 620, alignment: .top)
                        Divider()
                        channelContent
                    }
                } else {
                    if playerStore.currentChannel != nil { playerPanel }
                    channelContent
                }
            }
            .navigationBarHidden(true)
        }
        .sheet(isPresented: $showSources) {
            PlaylistManagerView(store: store)
        }
        .fullScreenCover(isPresented: $showFullscreen) {
            FullscreenPlayerView(playerStore: playerStore)
        }
        .alert("NM7 IPTV", isPresented: Binding(
            get: { store.errorMessage != nil },
            set: { if !$0 { store.errorMessage = nil } }
        )) {
            Button("Đóng", role: .cancel) { store.errorMessage = nil }
        } message: {
            Text(store.errorMessage ?? "")
        }
        .task { await store.start() }
    }

    @ViewBuilder
    private var playerPanel: some View {
        if let current = playerStore.currentChannel {
            VStack(spacing: 0) {
                NM7PlayerPane(playerStore: playerStore, fullscreen: { showFullscreen = true })
                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(current.name).font(.headline).lineLimit(1)
                        if let programme = store.programme(for: current) {
                            Text(programme.title).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                        } else {
                            Text(playerStore.statusText).font(.caption).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                    Button {
                        playerStore.stop()
                        store.clearPlaying()
                    } label: {
                        Image(systemName: "xmark.circle.fill").font(.title3)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Đóng trình phát")
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                Divider()
            }
        }
    }

    @ViewBuilder
    private var channelContent: some View {
        if store.isLoading {
            VStack(spacing: 12) {
                ProgressView()
                Text("Đang tải playlist…").foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if store.filteredChannels.isEmpty {
            VStack(spacing: 12) {
                Image(systemName: "tv.slash").font(.system(size: 36)).foregroundStyle(.secondary)
                Text("Không tìm thấy kênh").font(.headline)
                Text("Chọn nguồn IPTV hoặc thay đổi bộ lọc.")
                    .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
            }
            .padding()
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            List(store.filteredChannels) { channel in
                ChannelRow(
                    channel: channel,
                    programme: store.programme(for: channel),
                    playing: store.currentChannelID == channel.id,
                    favorite: store.isFavorite(channel),
                    onFavorite: { store.toggleFavorite(channel) }
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    store.recordPlaying(channel)
                    playerStore.play(channel)
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 10, bottom: 4, trailing: 10))
                .listRowBackground(
                    store.currentChannelID == channel.id
                        ? Color.accentColor.opacity(0.20)
                        : Color.clear
                )
            }
            .listStyle(.plain)
            .refreshable { await store.reloadCurrent() }
        }
    }

    private var header: some View {
        HStack(spacing: 10) {
            NM7LogoMark()
            VStack(alignment: .leading, spacing: 1) {
                Text("NM7 IPTV")
                    .font(.system(size: 20, weight: .black, design: .rounded))
                    .tracking(0.8)
                Text(store.selectedSource?.name ?? "Chưa chọn nguồn")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
            Spacer()
            if store.isLoadingEPG {
                ProgressView().controlSize(.small)
            }
            Button {
                Task { await store.reloadCurrent() }
            } label: {
                Image(systemName: "arrow.clockwise")
                    .font(.headline)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.bordered)
            .disabled(store.isLoading)
            .accessibilityLabel("Tải lại playlist")

            Button { showSources = true } label: {
                Image(systemName: "rectangle.stack.badge.plus")
                    .font(.title3)
                    .frame(width: 40, height: 40)
            }
            .buttonStyle(.bordered)
            .accessibilityLabel("Quản lý nguồn IPTV")
        }
        .padding(.horizontal, 14)
        .padding(.top, 8)
        .padding(.bottom, 6)
    }

    private var sectionBar: some View {
        HStack(spacing: 8) {
            ForEach(ChannelSection.allCases) { item in
                Button {
                    store.section = item
                } label: {
                    Label(item.rawValue, systemImage: sectionIcon(item))
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .tint(store.section == item ? Color.accentColor : Color.secondary.opacity(0.35))
            }
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 7)
    }

    private var searchBar: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField("Tìm kênh, nhóm hoặc URL…", text: $store.searchText)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
            if !store.searchText.isEmpty {
                Button { store.searchText = "" } label: { Image(systemName: "xmark.circle.fill") }
                    .buttonStyle(.plain)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 12)
        .frame(height: 42)
        .background(Color.secondary.opacity(0.10), in: RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }

    private var groupBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 7) {
                groupButton("Tất cả nhóm", value: "")
                ForEach(store.groups, id: \.self) { group in
                    groupButton(group, value: group)
                }
            }
            .padding(.horizontal, 12)
        }
        .frame(height: 43)
        .padding(.bottom, 4)
    }

    private func groupButton(_ title: String, value: String) -> some View {
        Button(title) { store.selectedGroup = value }
            .buttonStyle(.borderedProminent)
            .tint(store.selectedGroup == value ? Color.accentColor : Color.secondary.opacity(0.30))
            .font(.caption.weight(.semibold))
            .lineLimit(1)
    }

    private func sectionIcon(_ section: ChannelSection) -> String {
        switch section {
        case .all: return "rectangle.grid.1x2"
        case .favorites: return "star.fill"
        case .recent: return "clock.arrow.circlepath"
        }
    }
}

private struct NM7LogoMark: View {
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: 12)
                .fill(LinearGradient(colors: [Color.accentColor.opacity(0.95), Color.accentColor.opacity(0.50)], startPoint: .topLeading, endPoint: .bottomTrailing))
            Image(systemName: "play.tv.fill")
                .font(.system(size: 23, weight: .black))
                .foregroundStyle(.white)
        }
        .frame(width: 48, height: 48)
        .shadow(radius: 2, y: 1)
        .accessibilityHidden(true)
    }
}

private struct ChannelRow: View {
    let channel: IPTVChannel
    let programme: EPGProgramme?
    let playing: Bool
    let favorite: Bool
    let onFavorite: () -> Void

    var body: some View {
        HStack(spacing: 10) {
            AsyncImage(url: URL(string: channel.logo)) { phase in
                if case .success(let image) = phase {
                    image.resizable().scaledToFit()
                } else {
                    ZStack {
                        RoundedRectangle(cornerRadius: 8).fill(Color.secondary.opacity(0.12))
                        Text(String(channel.name.prefix(1)).uppercased())
                            .font(.headline.weight(.bold))
                            .foregroundStyle(Color.accentColor)
                    }
                }
            }
            .frame(width: 46, height: 46)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    if playing {
                        Image(systemName: "waveform")
                            .foregroundStyle(Color.accentColor)
                    }
                    Text(channel.name)
                        .font(.body.weight(playing ? .bold : .semibold))
                        .lineLimit(1)
                }
                if let programme {
                    Text(programme.title)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                    ProgressView(value: programme.progress)
                        .progressViewStyle(.linear)
                        .frame(maxWidth: 220)
                } else {
                    Text(channel.group)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }
            Spacer(minLength: 6)
            Button(action: onFavorite) {
                Image(systemName: favorite ? "star.fill" : "star")
                    .font(.title3)
                    .foregroundStyle(favorite ? Color.yellow : Color.secondary)
                    .frame(width: 38, height: 38)
            }
            .buttonStyle(.plain)
        }
        .padding(.vertical, 3)
    }
}
