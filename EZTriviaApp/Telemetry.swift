import Foundation
#if canImport(FirebaseCore)
import FirebaseCore
import FirebaseAnalytics
import FirebaseCrashlytics
#endif

@MainActor
enum Telemetry {
    static func configure() {
        #if canImport(FirebaseCore)
        guard FirebaseApp.app() == nil,
              Bundle.main.url(forResource: "GoogleService-Info", withExtension: "plist") != nil else { return }
        FirebaseApp.configure()
        #endif
    }

    static func log(_ name: String, parameters: [String: Any] = [:]) {
        #if canImport(FirebaseAnalytics)
        guard FirebaseApp.app() != nil else { return }
        Analytics.logEvent(name, parameters: parameters)
        #endif
    }

    static func record(_ error: Error) {
        #if canImport(FirebaseCrashlytics)
        guard FirebaseApp.app() != nil else { return }
        Crashlytics.crashlytics().record(error: error)
        #endif
    }
}
