import SwiftUI

/// Routing value for the daily. A distinct type so `navigationDestination`
/// can tell it apart from a category push.
struct DailyRoute: Hashable {}

/// The card on the home screen that offers today's challenge.
///
/// Placed above the category grid because the daily is the reason to open the
/// app on a day when you had not planned to play. Once it is done it stops
/// advertising and becomes a record of the streak.
struct DailyChallengeCard: View {
    @EnvironmentObject private var scores: ScoreStore

    private var today: Int { DailyChallenge.day(for: Date()) }
    private var result: DailyResult? { scores.dailyResult(for: today) }
    private var streak: Int { scores.dailyStreak(today: today) }

    var body: some View {
        NavigationLink(value: DailyRoute()) {
            HStack(spacing: 16) {
                VStack(spacing: 2) {
                    Image(systemName: result == nil ? "calendar.badge.clock" : "checkmark.seal.fill")
                        .font(.system(size: 26, weight: .semibold))
                    Text("#\(today + 1)")
                        .font(.caption2.bold().monospacedDigit())
                }
                .foregroundStyle(.white)
                .frame(width: 62, height: 62)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 16))

                VStack(alignment: .leading, spacing: 3) {
                    Text("Daily Challenge").font(.headline)
                    Text(subtitle).font(.subheadline).opacity(0.9)
                    if streak > 0 {
                        Label("\(streak) day streak", systemImage: "flame.fill")
                            .font(.caption.bold())
                            .opacity(0.95)
                    }
                }
                .foregroundStyle(.white)

                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.body.bold()).foregroundStyle(.white.opacity(0.8))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(colors: [.orange, .pink], startPoint: .topLeading, endPoint: .bottomTrailing),
                in: RoundedRectangle(cornerRadius: 22, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Daily Challenge number \(today + 1). \(subtitle)")
    }

    private var subtitle: String {
        if let result {
            return "Done — \(result.score)/\(result.total). Back tomorrow."
        }
        return "Ten questions. Everyone plays the same set."
    }
}

/// Today's challenge: the round, or the result if it has already been played.
struct DailyChallengeView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager
    @EnvironmentObject private var feedback: Feedback
    @EnvironmentObject private var router: PlayRouter
    @Environment(\.dismiss) private var dismiss

    @State private var engine = TriviaEngine(questions: [])
    @State private var started = false
    @State private var showExitConfirmation = false

    private var today: Int { DailyChallenge.day(for: Date()) }

    var body: some View {
        Group {
            if let result = scores.dailyResult(for: today) {
                DailyResultView(result: result, streak: scores.dailyStreak(today: today), finish: { router.popToRoot() })
            } else if engine.isRoundComplete && !engine.questions.isEmpty {
                // Momentary: finishRound() writes the record as the last
                // question is advanced past, which swaps this for the branch
                // above on the next render.
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if engine.currentQuestion != nil {
                RoundPlayer(
                    engine: $engine,
                    tint: .orange,
                    finishTitle: "Finish",
                    onAnswer: { correct in
                        if correct { feedback.correct() } else { feedback.wrong() }
                    },
                    onAdvance: { if engine.isRoundComplete { finishRound() } }
                )
            } else {
                ProgressView("Preparing today's questions…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .tabBar)
        .navigationBarBackButtonHidden(scores.dailyResult(for: today) == nil)
        .toolbar {
            if scores.dailyResult(for: today) == nil {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showExitConfirmation = true } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Exit the daily challenge")
                }
            }
            ToolbarItem(placement: .principal) {
                Text("Daily #\(today + 1)").font(.headline)
            }
        }
        .confirmationDialog("Leave today's challenge?", isPresented: $showExitConfirmation, titleVisibility: .visible) {
            Button("Leave", role: .destructive) { dismiss() }
            Button("Keep Playing", role: .cancel) {}
        } message: {
            Text("You get one attempt a day, so leaving now means starting over from the first question.")
        }
        .onAppear {
            guard !started else { return }
            started = true
            feedback.prepare()
            engine = TriviaEngine(questions: DailyChallenge.challenge(for: today).questions)
        }
    }

    private func finishRound() {
        feedback.roundComplete()
        let result = DailyResult(
            day: today,
            score: engine.score,
            total: engine.questions.count,
            points: engine.points,
            outcomes: engine.outcomes
        )
        scores.recordDaily(result)
        gameCenter.submitDaily(points: result.points)
        Telemetry.log("daily_complete", parameters: [
            "day": today,
            "score": result.score,
            "points": result.points
        ])
    }
}

private struct DailyResultView: View {
    let result: DailyResult
    let streak: Int
    let finish: () -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Image(systemName: streak > 1 ? "flame.fill" : "checkmark.seal.fill")
                    .font(.system(size: 50))
                    .foregroundStyle(.orange)
                    .frame(width: 104, height: 104)
                    .background(.orange.opacity(0.15), in: Circle())

                Text("Daily #\(result.day + 1) complete").font(.title.bold()).multilineTextAlignment(.center)

                Text("\(result.score) / \(result.total)")
                    .font(.system(size: 50, weight: .bold, design: .rounded))
                    .foregroundStyle(.orange)

                Text(RoundSummary.grid(result.outcomes)).font(.title3).lineLimit(1).minimumScaleFactor(0.5)

                HStack(spacing: 26) {
                    stat("\(result.points.formatted())", "points")
                    stat("\(streak)", "day streak")
                }

                Text(streak > 1
                     ? "Come back tomorrow to keep the streak going."
                     : "A new set of ten arrives tomorrow.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)

                ShareResultButton(message: shareText, headline: shareHeadline, card: shareCard)
                    .font(.headline)
                    .padding(.top, 4)

                Button("Back to categories") { finish() }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
                    .padding(.top, 4)
            }
            .padding()
        }
    }

    private func stat(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.title2.bold().monospacedDigit())
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
    }

    /// No link: the share carries the App Store URL as its item, so the score
    /// card itself is what opens the listing.
    private var shareText: String {
        RoundSummary.daily(
            day: result.day + 1,
            outcomes: result.outcomes,
            points: result.points,
            streak: streak,
            includingLink: false
        )
    }

    private var shareHeadline: String {
        RoundSummary.headline(correct: result.score, total: result.total)
    }

    private var shareCard: ScoreCard {
        ScoreCard(
            title: "Daily #\(result.day + 1)",
            subtitle: "\(result.points.formatted()) points",
            headline: "\(result.score)/\(result.total)",
            grid: RoundSummary.grid(result.outcomes),
            footnote: streak > 1 ? "\(streak) day streak" : nil,
            tint: .orange
        )
    }
}
