import Foundation
import os

/// A local logging seam.
///
/// The app deliberately ships no analytics SDK: nothing here leaves the device.
/// Call sites exist so that if a backend is ever wanted, there is one place to
/// add it rather than a scattering of new call sites — and so that removing it
/// again is equally cheap.
@MainActor
enum Telemetry {
    private static let logger = Logger(subsystem: "com.rsm.eztrivia", category: "gameplay")

    static func configure() {}

    static func log(_ name: String, parameters: [String: Any] = [:]) {
        #if DEBUG
        let detail = parameters
            .sorted { $0.key < $1.key }
            .map { "\($0.key)=\($0.value)" }
            .joined(separator: " ")
        logger.debug("\(name, privacy: .public) \(detail, privacy: .public)")
        #endif
    }

    static func record(_ error: Error) {
        logger.error("\(String(describing: error), privacy: .public)")
    }
}
