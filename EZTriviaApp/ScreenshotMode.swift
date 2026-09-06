import Foundation

/// Suppresses the two things that make a simulator capture unusable as an App
/// Store screenshot.
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

    static let isActive: Bool = {
        #if DEBUG
        return ProcessInfo.processInfo.arguments.contains(launchArgument)
        #else
        return false
        #endif
    }()
}
