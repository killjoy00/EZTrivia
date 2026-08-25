import AVFoundation
import SwiftUI
import UIKit

/// Sound and haptics for the round screen.
///
/// Both are opt-out and remembered, because feedback that cannot be silenced
/// is worse than no feedback: a trivia app gets played on trains and in
/// waiting rooms.
@MainActor
final class Feedback: ObservableObject {
    enum Cue: String, CaseIterable {
        case correct, wrong, complete
    }

    @Published var soundEnabled: Bool {
        didSet {
            defaults.set(soundEnabled, forKey: Keys.sound)
            if soundEnabled { prepareAudio() }
        }
    }

    @Published var hapticsEnabled: Bool {
        didSet { defaults.set(hapticsEnabled, forKey: Keys.haptics) }
    }

    private enum Keys {
        static let sound = "feedback.sound.enabled"
        static let haptics = "feedback.haptics.enabled"
    }

    private let defaults: UserDefaults
    private var players: [Cue: AVAudioPlayer] = [:]
    private var sessionConfigured = false

    private let selection = UISelectionFeedbackGenerator()
    private let notification = UINotificationFeedbackGenerator()

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
        // Absent keys default to on. `object(forKey:)` rather than `bool(forKey:)`
        // because the latter cannot tell "never set" from "explicitly false",
        // which would silently start every new install muted.
        soundEnabled = defaults.object(forKey: Keys.sound) as? Bool ?? true
        hapticsEnabled = defaults.object(forKey: Keys.haptics) as? Bool ?? true
    }

    // MARK: - Cues

    func correct() {
        play(.correct)
        if hapticsEnabled { notification.notificationOccurred(.success) }
    }

    func wrong() {
        play(.wrong)
        // .warning rather than .error: the player made a normal move that
        // happened to be wrong, and .error's double-thump reads as "the app
        // broke" rather than "not quite".
        if hapticsEnabled { notification.notificationOccurred(.warning) }
    }

    func roundComplete() {
        play(.complete)
        if hapticsEnabled { notification.notificationOccurred(.success) }
    }

    /// Light tick when a control is chosen but nothing has been judged yet.
    func select() {
        if hapticsEnabled { selection.selectionChanged() }
    }

    /// Warms up the haptic engine and decodes the audio.
    ///
    /// Called when a round starts. Without it the first correct answer of a
    /// session pays for decoding a file and spinning up Taptic on the main
    /// thread, which lands as a visible stutter at the worst moment.
    func prepare() {
        if soundEnabled { prepareAudio() }
        guard hapticsEnabled else { return }
        selection.prepare()
        notification.prepare()
    }

    // MARK: - Audio

    private func play(_ cue: Cue) {
        guard soundEnabled else { return }
        prepareAudio()
        guard let player = players[cue] else { return }
        player.currentTime = 0
        player.play()
    }

    private func prepareAudio() {
        configureSessionIfNeeded()
        guard players.isEmpty else { return }
        for cue in Cue.allCases {
            guard let url = Bundle.main.url(forResource: cue.rawValue, withExtension: "wav") else { continue }
            guard let player = try? AVAudioPlayer(contentsOf: url) else { continue }
            player.prepareToPlay()
            players[cue] = player
        }
    }

    private func configureSessionIfNeeded() {
        guard !sessionConfigured else { return }
        sessionConfigured = true
        // .ambient is the category that makes the ring/silent switch work and
        // leaves whatever the player is already listening to running. A trivia
        // cue has no business interrupting someone's podcast.
        try? AVAudioSession.sharedInstance().setCategory(.ambient, mode: .default)
        try? AVAudioSession.sharedInstance().setActive(true)
    }
}
