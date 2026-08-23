import SwiftUI

@main
struct EZTriviaApp: App {
    @StateObject private var scores = ScoreStore()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(scores)
                .tint(.indigo)
        }
    }
}
