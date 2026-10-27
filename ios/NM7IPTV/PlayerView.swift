import SwiftUI
import AVFoundation
import AVKit
import MediaPlayer
import UIKit
import Combine

@MainActor
final class PlayerStore: ObservableObject {
    let player = AVPlayer()
    @Published var currentChannel: IPTVChannel?
    @Published var isPlaying = false
    @Published var statusText = ""
    @Published var errorMessage: String?
    @Published var controlsVisible = false
    @Published var pictureInPictureAvailable = false

    private var timeObservation: NSKeyValueObservation?
    private var itemStatusObservation: NSKeyValueObservation?
    private var failedObserver: NSObjectProtocol?
    private var remoteTargets: [Any] = []
    private var pipController: AVPictureInPictureController?
    weak var volumeSlider: UISlider?

    init() {
        player.automaticallyWaitsToMinimizeStalling = true
        timeObservation = player.observe(\.timeControlStatus, options: [.initial, .new]) { [weak self] player, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.isPlaying = player.timeControlStatus == .playing
                switch player.timeControlStatus {
                case .waitingToPlayAtSpecifiedRate: self.statusText = "Đang tải luồng…"
                case .playing: self.statusText = "Đang phát"
                case .paused where self.currentChannel != nil: self.statusText = "Đã tạm dừng"
                default: break
                }
                self.updateNowPlayingRate()
            }
        }
        failedObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemFailedToPlayToEndTime, object: nil, queue: .main
        ) { [weak self] notification in
            let error = notification.userInfo?[AVPlayerItemFailedToPlayToEndTimeErrorKey] as? Error
            Task { @MainActor in
                self?.errorMessage = error?.localizedDescription ?? "Không phát được nguồn này"
            }
        }
        configureRemoteCommands()
    }

    deinit {
        timeObservation?.invalidate()
        itemStatusObservation?.invalidate()
        if let failedObserver { NotificationCenter.default.removeObserver(failedObserver) }
        let center = MPRemoteCommandCenter.shared()
        if remoteTargets.indices.contains(0) { center.playCommand.removeTarget(remoteTargets[0]) }
        if remoteTargets.indices.contains(1) { center.pauseCommand.removeTarget(remoteTargets[1]) }
        if remoteTargets.indices.contains(2) { center.togglePlayPauseCommand.removeTarget(remoteTargets[2]) }
    }

    func play(_ channel: IPTVChannel) {
        errorMessage = nil
        statusText = "Đang kết nối…"
        currentChannel = channel
        controlsVisible = false
        updateNowPlaying(channel)

        if let drm = channel.drmKind {
            player.pause()
            player.replaceCurrentItem(with: nil)
            errorMessage = "Kênh này dùng \(drm). iPhone/iPad cần FairPlay hoặc SDK DRM dành riêng cho iOS; cấu hình DRM Android không dùng trực tiếp được trên iOS."
            statusText = "DRM chưa tương thích iOS"
            return
        }

        let lowerURL = channel.url.lowercased()
        if channel.mimeHint.lowercased().contains("dash") || lowerURL.contains(".mpd") {
            player.pause()
            player.replaceCurrentItem(with: nil)
            errorMessage = "Nguồn DASH/MPD chưa được AVPlayer hỗ trợ trực tiếp. Hãy dùng nguồn HLS/M3U8 tương ứng trên iPhone/iPad."
            statusText = "Định dạng chưa tương thích iOS"
            return
        }

        guard let url = URL(string: channel.url), ["http", "https"].contains(url.scheme?.lowercased() ?? "") else {
            errorMessage = "Giao thức của kênh này chưa được bản iOS hỗ trợ."
            statusText = "Không hỗ trợ nguồn"
            return
        }

        do {
            let audio = AVAudioSession.sharedInstance()
            try audio.setCategory(.playback, mode: .moviePlayback, options: [])
            try audio.setActive(true)
        } catch { }

        var options: [String: Any] = [:]
        if !channel.headers.isEmpty { options["AVURLAssetHTTPHeaderFieldsKey"] = channel.headers }
        let asset = AVURLAsset(url: url, options: options)
        let item = AVPlayerItem(asset: asset)
        observeItem(item)
        player.replaceCurrentItem(with: item)
        player.play()
    }

    func togglePlayback() {
        if player.timeControlStatus == .playing { player.pause() } else { player.play() }
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
        itemStatusObservation?.invalidate()
        itemStatusObservation = nil
        currentChannel = nil
        errorMessage = nil
        statusText = ""
        controlsVisible = false
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    func attachPictureInPicture(to layer: AVPlayerLayer) {
        guard AVPictureInPictureController.isPictureInPictureSupported() else {
            pictureInPictureAvailable = false
            return
        }
        if pipController?.isPictureInPictureActive == true { return }
        guard let controller = AVPictureInPictureController(playerLayer: layer) else {
            pictureInPictureAvailable = false
            return
        }
        controller.canStartPictureInPictureAutomaticallyFromInline = true
        pipController = controller
        pictureInPictureAvailable = true
    }

    func startPictureInPicture() {
        guard let pipController, pipController.isPictureInPicturePossible else {
            errorMessage = "Picture in Picture chưa sẵn sàng. Hãy đợi kênh bắt đầu phát rồi thử lại."
            return
        }
        pipController.startPictureInPicture()
        controlsVisible = false
    }

    var systemVolume: Float {
        volumeSlider?.value ?? AVAudioSession.sharedInstance().outputVolume
    }

    func setSystemVolume(_ value: Float) {
        let value = min(1, max(0, value))
        volumeSlider?.setValue(value, animated: false)
        volumeSlider?.sendActions(for: .valueChanged)
    }

    private func observeItem(_ item: AVPlayerItem) {
        itemStatusObservation?.invalidate()
        itemStatusObservation = item.observe(\.status, options: [.initial, .new]) { [weak self] item, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                if item.status == .failed {
                    self.errorMessage = item.error?.localizedDescription ?? "Không phát được nguồn này"
                    self.statusText = "Lỗi phát"
                }
            }
        }
    }

    private func configureRemoteCommands() {
        let center = MPRemoteCommandCenter.shared()
        center.nextTrackCommand.isEnabled = false
        center.previousTrackCommand.isEnabled = false
        remoteTargets.append(center.playCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.player.play() }
            return .success
        })
        remoteTargets.append(center.pauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.player.pause() }
            return .success
        })
        remoteTargets.append(center.togglePlayPauseCommand.addTarget { [weak self] _ in
            Task { @MainActor in self?.togglePlayback() }
            return .success
        })
    }

    private func updateNowPlaying(_ channel: IPTVChannel) {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = [
            MPMediaItemPropertyTitle: channel.name,
            MPMediaItemPropertyArtist: channel.group,
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: 1.0
        ]
    }

    private func updateNowPlayingRate() {
        guard currentChannel != nil else { return }
        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
        info[MPNowPlayingInfoPropertyPlaybackRate] = isPlaying ? 1.0 : 0.0
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }
}

struct PlayerSurface: UIViewRepresentable {
    @ObservedObject var store: PlayerStore

    func makeUIView(context: Context) -> PlayerLayerView {
        let view = PlayerLayerView()
        view.playerLayer.videoGravity = .resizeAspect
        view.playerLayer.player = store.player
        DispatchQueue.main.async { store.attachPictureInPicture(to: view.playerLayer) }
        return view
    }

    func updateUIView(_ uiView: PlayerLayerView, context: Context) {
        uiView.playerLayer.player = store.player
    }
}

final class PlayerLayerView: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var playerLayer: AVPlayerLayer { layer as! AVPlayerLayer }
}

struct SystemVolumeBridge: UIViewRepresentable {
    @ObservedObject var store: PlayerStore

    func makeUIView(context: Context) -> MPVolumeView {
        let view = MPVolumeView(frame: .zero)
        DispatchQueue.main.async { store.volumeSlider = view.subviews.compactMap { $0 as? UISlider }.first }
        return view
    }

    func updateUIView(_ uiView: MPVolumeView, context: Context) {
        if store.volumeSlider == nil {
            DispatchQueue.main.async { store.volumeSlider = uiView.subviews.compactMap { $0 as? UISlider }.first }
        }
    }
}

struct NM7PlayerPane: View {
    @ObservedObject var playerStore: PlayerStore
    let fullscreen: () -> Void

    @State private var gestureSide = 0
    @State private var startBrightness: CGFloat = 0.5
    @State private var startVolume: Float = 0.5
    @State private var feedback: String?

    var body: some View {
        GeometryReader { geometry in
            ZStack {
                Color.black
                PlayerSurface(store: playerStore)
                SystemVolumeBridge(store: playerStore)
                    .frame(width: 1, height: 1)
                    .opacity(0.001)

                if playerStore.controlsVisible {
                    ZStack {
                        Color.black.opacity(0.18)
                        HStack(spacing: 28) {
                            Button(action: playerStore.togglePlayback) {
                                Image(systemName: playerStore.isPlaying ? "pause.fill" : "play.fill")
                                    .font(.system(size: 27, weight: .bold))
                                    .frame(width: 58, height: 58)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                            if playerStore.pictureInPictureAvailable {
                                Button(action: playerStore.startPictureInPicture) {
                                    Image(systemName: "pip.enter")
                                        .font(.system(size: 20, weight: .semibold))
                                        .frame(width: 50, height: 50)
                                        .background(.ultraThinMaterial, in: Circle())
                                }
                                .accessibilityLabel("Picture in Picture")
                            }
                            Button(action: fullscreen) {
                                Image(systemName: "arrow.up.left.and.arrow.down.right")
                                    .font(.system(size: 21, weight: .semibold))
                                    .frame(width: 50, height: 50)
                                    .background(.ultraThinMaterial, in: Circle())
                            }
                        }
                        .buttonStyle(.plain)
                        .foregroundStyle(.white)
                    }
                }

                if let feedback {
                    Text(feedback)
                        .font(.headline)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(.ultraThinMaterial, in: Capsule())
                        .foregroundStyle(.white)
                }

                if let error = playerStore.errorMessage {
                    VStack(spacing: 10) {
                        Image(systemName: "exclamationmark.triangle.fill")
                        Text(error).multilineTextAlignment(.center)
                    }
                    .font(.subheadline)
                    .padding(18)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                    .foregroundStyle(.white)
                    .padding(24)
                }
            }
            .contentShape(Rectangle())
            .onTapGesture { playerStore.controlsVisible.toggle() }
            .gesture(edgeGesture(size: geometry.size))
        }
        .aspectRatio(16 / 9, contentMode: .fit)
        .background(Color.black)
    }

    private func edgeGesture(size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                if gestureSide == 0 {
                    if value.startLocation.x < size.width * 0.30 {
                        gestureSide = 1
                        startBrightness = UIScreen.main.brightness
                    } else if value.startLocation.x > size.width * 0.70 {
                        gestureSide = 2
                        startVolume = playerStore.systemVolume
                    } else {
                        gestureSide = -1
                    }
                }
                guard gestureSide > 0 else { return }
                let change = Float(-value.translation.height / max(1, size.height)) * 1.25
                if gestureSide == 1 {
                    let next = min(1, max(0.05, startBrightness + CGFloat(change)))
                    UIScreen.main.brightness = next
                    feedback = "☀︎  Độ sáng \(Int(next * 100))%"
                } else {
                    let next = min(1, max(0, startVolume + change))
                    playerStore.setSystemVolume(next)
                    feedback = "🔊  Âm lượng \(Int(next * 100))%"
                }
            }
            .onEnded { _ in
                gestureSide = 0
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.55) { feedback = nil }
            }
    }
}

struct FullscreenPlayerView: View {
    @ObservedObject var playerStore: PlayerStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.black.ignoresSafeArea()
            NM7PlayerPane(playerStore: playerStore, fullscreen: { dismiss() })
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            Button(action: { dismiss() }) {
                Image(systemName: "xmark")
                    .font(.headline)
                    .frame(width: 44, height: 44)
                    .background(.ultraThinMaterial, in: Circle())
            }
            .foregroundStyle(.white)
            .padding()
        }
        .statusBarHidden()
    }
}
