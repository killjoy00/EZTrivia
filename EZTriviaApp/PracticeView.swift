import SwiftUI

/// Routes to the practice round. A distinct type so the navigation path can
/// address it by value, the same way `GameRoute` and `DailyRoute` do.
struct PracticeRoute: Hashable {}

/// A round built from questions the player has previously got wrong.
///
/// Deliberately unscored: practice awards no lifetime points and submits
/// nothing to Game Center. The questions here are ones the player has already
/// been shown the answer to, so a scored replay would be a points faucet — and
/// the leaderboards it feeds are lifetime totals that only ever grow. The
/// reward for practising is the list getting shorter.
struct PracticeView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var feedback: Feedback
    @EnvironmentObject private var router: PlayRouter
    @Environment(\.dismiss) private var dismiss

    @State private var engine = TriviaEngine(questions: [])
    @State private var started = false
    @State private var showExitConfirmation = false
    /// Misses retired during this round, for the result screen. Read from the
    /// engine's outcomes rather than the store, because by the time the result
    /// is shown the store has already been emptied of them.
    @State private var retired = 0

    var body: some View {
        Group {
            if engine.isRoundComplete && !engine.questions.isEmpty {
                resultView
            } else if engine.currentQuestion != nil {
                RoundPlayer(
                    engine: $engine,
                    tint: .orange,
                    finishTitle: "See results",
                    onAnswer: { question, correct in
                        if correct {
                            feedback.correct()
                            scores.clearMiss(question.id)
                            retired += 1
                        } else {
                            feedback.wrong()
                            // Already on the list; recordMiss keeps its
                            // original position rather than moving it back.
                            scores.recordMiss(question.id)
                        }
                    },
                    onAdvance: { if engine.isRoundComplete { feedback.roundComplete() } }
                )
            } else {
                allClearView
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .tabBar)
        .onAppear {
            guard !started else { return }
            started = true
            feedback.prepare()
            engine = TriviaEngine(questions: QuestionPicker.practiceRound(ids: scores.missedQuestionIDs))
        }
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { showExitConfirmation = true } label: { Image(systemName: "xmark") }
                    .accessibilityLabel("Exit practice")
            }
            ToolbarItem(placement: .principal) { Text("Practice").font(.headline) }
        }
        .confirmationDialog("Leave practice?", isPresented: $showExitConfirmation, titleVisibility: .visible) {
            Button("Leave Practice", role: .destructive) { dismiss() }
            Button("Keep Practicing", role: .cancel) {}
        } message: {
            // Unlike a scored round, nothing is lost by leaving: questions
            // already answered correctly have already left the list.
            Text("Questions you've already got right stay cleared.")
        }
    }

    private var allClearView: some View {
        ContentUnavailableView {
            Label("Nothing to practise", systemImage: "checkmark.circle")
        } description: {
            Text("You've cleared every question you missed. Play a round to find more.")
        } actions: {
            Button("Back to categories") { router.popToRoot() }
                .buttonStyle(.borderedProminent)
        }
    }

    private var resultView: some View {
        VStack(spacing: 22) {
            Spacer()
            Image(systemName: remaining == 0 ? "checkmark.seal.fill" : "arrow.triangle.2.circlepath")
                .font(.system(size: 54))
                .foregroundStyle(.orange)
                .frame(width: 108, height: 108)
                .background(.orange.opacity(0.15), in: Circle())
            Text(remaining == 0 ? "All caught up!" : "Good practice.")
                .font(.largeTitle.bold())
                .multilineTextAlignment(.center)
            Text("\(retired) of \(engine.questions.count) cleared")
                .font(.system(size: 40, weight: .bold, design: .rounded))
                .foregroundStyle(.orange)
            Text(RoundSummary.grid(engine.outcomes))
                .font(.title3)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
            Text(message)
                .font(.body)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Spacer()
            if remaining > 0 {
                Button("Practise \(min(remaining, 10)) more") { nextRound() }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
            }
            Button("Back to categories") { router.popToRoot() }
                .font(.headline)
                .padding(.bottom)
        }
        .padding()
    }

    private var remaining: Int { scores.missedCount }

    private var message: String {
        if remaining == 0 { return "Your practice list is empty. Nicely done." }
        if retired == 0 { return "These ones are stubborn. They'll be waiting whenever you want another go." }
        return "\(remaining) question\(remaining == 1 ? "" : "s") still to clear."
    }

    private func nextRound() {
        retired = 0
        engine = TriviaEngine(questions: QuestionPicker.practiceRound(ids: scores.missedQuestionIDs))
    }
}

/// Home-screen entry point, shown only when there is something to practise.
struct PracticeCard: View {
    @EnvironmentObject private var scores: ScoreStore

    var body: some View {
        NavigationLink(value: PracticeRoute()) {
            HStack(spacing: 16) {
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.title2.bold())
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .background(.orange, in: RoundedRectangle(cornerRadius: 13))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Practice your misses").font(.headline)
                    Text("\(scores.missedCount) question\(scores.missedCount == 1 ? "" : "s") to clear")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").foregroundStyle(.tertiary)
            }
            .foregroundStyle(.primary)
            .cardStyle()
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
        .accessibilityHint("Replays up to ten questions you answered wrong")
    }
}
