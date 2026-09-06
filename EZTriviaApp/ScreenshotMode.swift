import Foundation

/// Suppresses the two things that make a simulator capture unusable as an App
/// Store screenshot, and routes the app to the screen a capture run asked for.
///
/// A simulator has no signed-in Game Center account, so authentication always
/// fails and the resulting alert sits in the middle of the screen. And the
/// AdMob test unit renders a "You've loaded a test ad" placeholder, which is
/// developer-facing text that reads as an unfinished app -- the same reason
/// `AdBannerView` refuses to draw a placeholder card.
///
/// Gated on `DEBUG` as well as the launch argument. App Store builds are
/// Release, so this is compiled out of anything that ships: a distributed
/// binary cannot enter this mode however it is launched.
enum ScreenshotMode {
    static let launchArgument = "-EZTriviaScreenshotMode"
    static let screenArgument = "-EZTriviaScreenshotScreen"

    /// The screens worth putting on a store listing. Deliberately excludes
    /// Scores, which without a Game Center account renders only its "Sign in
    /// to Game Center" empty state.
    enum Screen: String {
        case home
        case difficulty
        case question
        /// A question with its answer revealed, so the capture shows the
        /// explanation. Reaching it means answering one, which is why
        /// `GameView` consults this.
        case answered
        case daily
    }

    static let isActive: Bool = {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains(launchArgument)
        #else
        return false
        #endif
    }()

    /// Nil whenever screenshot mode is off, so a single `guard let` covers
    /// both "not a capture run" and "no screen requested" at every call site.
    /// An unrecognized name falls back to home rather than trapping: a typo in
    /// a workflow should cost a wrong screenshot, not a crashed capture.
    static var screen: Screen? {
        guard isActive else { return nil }
        let arguments = ProcessInfo.processInfo.arguments
        guard let flag = arguments.firstIndex(of: screenArgument),
              arguments.index(after: flag) < arguments.endIndex else { return .home }
        return Screen(rawValue: arguments[arguments.index(after: flag)]) ?? .home
    }
}
