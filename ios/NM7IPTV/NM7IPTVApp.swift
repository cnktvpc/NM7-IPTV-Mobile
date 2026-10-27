import SwiftUI

@main
struct NM7IPTVApp: App {
    @StateObject private var store = AppStore()
    @StateObject private var playerStore = PlayerStore()

    var body: some Scene {
        WindowGroup {
            ContentView(store: store, playerStore: playerStore)
                .tint(Color(red: 0.18, green: 0.78, blue: 0.78))
                .preferredColorScheme(.dark)
        }
    }
}
