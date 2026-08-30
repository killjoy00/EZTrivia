import Foundation
import UserNotifications

/// Schedules one local notification on the evening a daily-challenge streak is
/// about to lapse.
///
/// The rule is deliberately narrow. A reminder only exists when there is
/// something real to lose -- a run of `minimumStreak` days or more -- and only
/// on the day losing it is actually on the line. `DailyStreak.dayAtRisk`
/// decides which day that is; everything here is scheduling.
///
/// Exactly one notification is ever pending. Every refresh clears the previous
/// request before deciding whether to post a new one, so playing the daily
/// cancels tonight's reminder rather than leaving a stale nag queued behind it.
@MainActor
final class StreakReminder: ObservableObject {
    /// Local hour the reminder fires on the at-risk day. Late enough to read as
    /// "the day is nearly over", early enough to leave time to actually play.
    static let reminderHour = 20

    /// Runs shorter than this are not worth interrupting an evening for.
    static let minimumStreak = 2

    private static let requestIdentifier = "EZTrivia.streakReminder"

    /// Persisted here, but acted on by `applyEnabledChange`. The toggle only
    /// records the preference; asking for permission and then scheduling has to
    /// happen in that order, and a `didSet` cannot await.
    @Published var isEnabled: Bool {
        didSet {
            guard isEnabled != oldValue else { return }
            defaults.set(isEnabled, forKey: Keys.isEnabled)
        }
    }

    /// Set when the system has denied notifications, so Settings can explain why
    /// the toggle did nothing rather than leaving it silently on.
    @Published private(set) var authorizationDenied = false

    private enum Keys {
        static let isEnabled = "daily.streakReminder.enabled"
    }

    private let defaults: UserDefaults
    private let center: UNUserNotificationCenter?
    private let calendar: Calendar

    /// `center` is optional so tests and previews can construct the object
    /// without touching the notification system.
    init(
        defaults: UserDefaults = .standard,
        center: UNUserNotificationCenter? = .current(),
        calendar: Calendar = .current
    ) {
        self.defaults = defaults
        self.center = center
        self.calendar = calendar
        isEnabled = defaults.bool(forKey: Keys.isEnabled)
    }

    /// Recomputes the pending reminder from the current daily history.
    ///
    /// Safe to call often: on launch, on the way to the background, and after a
    /// daily is finished. Scheduling is idempotent because the previous request
    /// is removed first.
    func refresh(playedDays: Set<Int>, now: Date = Date()) async {
        guard let center else { return }
        center.removePendingNotificationRequests(withIdentifiers: [Self.requestIdentifier])

        guard isEnabled, await hasAuthorization() else { return }

        let today = DailyChallenge.day(for: now, in: calendar)
        guard let risk = DailyStreak.dayAtRisk(
            playedDays: playedDays,
            today: today,
            minimumStreak: Self.minimumStreak
        ), let fireDate = fireDate(forDay: risk.day, now: now) else { return }

        let content = UNMutableNotificationContent()
        content.title = "Keep your \(risk.streak)-day streak"
        content.body = risk.streak >= 7
            ? "Today's Daily Challenge is still open. \(risk.streak) days on the line."
            : "Play today's Daily Challenge before midnight to keep it going."
        content.sound = .default

        let components = calendar.dateComponents(
            [.year, .month, .day, .hour, .minute],
            from: fireDate
        )
        let request = UNNotificationRequest(
            identifier: Self.requestIdentifier,
            content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: components, repeats: false)
        )

        do { try await center.add(request) } catch { Telemetry.record(error) }
    }

    /// When to fire on the at-risk day, or nil if that day no longer has room
    /// for a useful reminder.
    ///
    /// The normal answer is `reminderHour` on that day. The exception is the
    /// player who backgrounds the app late on the at-risk evening: the hour has
    /// already passed, but the streak does not die until midnight, so there is
    /// still a reminder worth sending. A minute out is enough to leave the app
    /// without the banner landing on top of them.
    private func fireDate(forDay day: Int, now: Date) -> Date? {
        guard let start = DailyChallenge.startOfDay(day, in: calendar),
              let scheduled = calendar.date(
                bySettingHour: Self.reminderHour, minute: 0, second: 0, of: start
              ),
              let endOfDay = calendar.date(byAdding: .day, value: 1, to: start)
        else { return nil }

        if scheduled > now { return scheduled }

        let soon = now.addingTimeInterval(60)
        return soon < endOfDay ? soon : nil
    }

    /// Follows the toggle: ask for permission when switched on, drop any
    /// pending reminder when switched off, then reschedule from current state.
    func applyEnabledChange(playedDays: Set<Int>) async {
        guard isEnabled else {
            center?.removePendingNotificationRequests(withIdentifiers: [Self.requestIdentifier])
            authorizationDenied = false
            return
        }
        await requestAuthorization()
        await refresh(playedDays: playedDays)
    }

    private func requestAuthorization() async {
        guard let center else { return }
        do {
            let granted = try await center.requestAuthorization(options: [.alert, .sound])
            authorizationDenied = !granted
        } catch {
            authorizationDenied = true
            Telemetry.record(error)
        }
    }

    private func hasAuthorization() async -> Bool {
        guard let center else { return false }
        let settings = await center.notificationSettings()
        switch settings.authorizationStatus {
        case .authorized, .provisional, .ephemeral:
            authorizationDenied = false
            return true
        case .denied:
            authorizationDenied = true
            return false
        case .notDetermined:
            return false
        @unknown default:
            return false
        }
    }
}
