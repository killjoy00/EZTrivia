import SwiftUI

struct GameView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var feedback: Feedback
    @EnvironmentObject private var router: PlayRouter
    @Environment(\.dismiss) private var dismiss
    let category: TriviaCategory
    let difficulty: TriviaDifficulty
    @State private var engine = TriviaEngine(questions: [])
    @State private var showExitConfirmation = false
    @State private var started = false
    @State private var loadFailed = false

    var body: some View {
        Group {
            if engine.isRoundComplete && !engine.questions.isEmpty {
                ResultView(
                    category: category,
                    difficulty: difficulty,
                    score: engine.score,
                    total: engine.questions.count,
                    points: engine.points,
                    outcomes: engine.outcomes,
                    playAgain: nextRound,
                    // All the way home, not one screen back. dismiss() here
                    // returned the player to the difficulty picker, which is
                    // not what "Back to categories" says.
                    finish: { router.popToRoot() }
                )
            }
            else if engine.currentQuestion != nil {
                RoundPlayer(
                    engine: $engine,
                    tint: AppTheme.color(for: category),
                    finishTitle: "See results",
                    onAnswer: { correct in
                        if correct { feedback.correct() } else { feedback.wrong() }
                    },
                    onAdvance: { if engine.isRoundComplete { feedback.roundComplete() } }
                )
            }
            else if loadFailed { roundUnavailableView }
            else {
                ProgressView("Preparing questions…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        // A round is a focused task, and the Play/Scores tab bar was stacking
        // directly under the Next button, which is what made that button read
        // as floating over another bar. Hiding it also returns ~50pt of
        // vertical space to the question itself.
        .toolbar(.hidden, for: .tabBar)
        .onAppear {
            guard !started else { return }
            started = true
            feedback.prepare()
            nextRound()
        }
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { showExitConfirmation = true } label: { Image(systemName: "xmark") }
                    .accessibilityLabel("Exit round")
            }
            ToolbarItem(placement: .principal) { Text("\(category.title) · \(difficulty.title)").font(.headline) }
        }
        .confirmationDialog("Leave this round?", isPresented: $showExitConfirmation, titleVisibility: .visible) {
            Button("Leave Round", role: .destructive) { dismiss() }
            Button("Keep Playing", role: .cancel) {}
        } message: { Text("Your current score won't be saved.") }
    }

    private var roundUnavailableView: some View {
        ContentUnavailableView {
            Label("Questions unavailable", systemImage: "questionmark.folder")
        } description: {
            Text("This round could not be prepared. Try again or choose another category.")
        } actions: {
            Button("Try Again") { nextRound() }
                .buttonStyle(.borderedProminent)
        }
    }

    private func nextRound() {
        let seen = scores.seenQuestions(category: category, difficulty: difficulty)
        let next = QuestionPicker.round(category: category, difficulty: difficulty, excluding: seen)
        guard !next.isEmpty else {
            loadFailed = true
            return
        }
        loadFailed = false
        scores.markSeen(Set(next.map(\.id)), category: category, difficulty: difficulty)
        engine = TriviaEngine(questions: next)
    }
}

private struct ResultView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager
    let category: TriviaCategory
    let difficulty: TriviaDifficulty
    let score: Int
    let total: Int
    let points: Int
    let outcomes: [Bool]
    let playAgain: () -> Void
    let finish: () -> Void
    @State private var saved = false

    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            Image(systemName: ratio >= 0.8 ? "trophy.fill" : "sparkles")
                .font(.system(size: 54)).foregroundStyle(.yellow).frame(width: 108, height: 108).background(.yellow.opacity(0.15), in: Circle())
            Text(ratio >= 0.8 ? "Trivia champion!" : ratio >= 0.5 ? "Nice work!" : "Keep learning!").font(.largeTitle.bold())
            Text("You scored").foregroundStyle(.secondary)
            Text("\(score) / \(total)").font(.system(size: 52, weight: .bold, design: .rounded)).foregroundStyle(AppTheme.color(for: category))
            Text(RoundSummary.grid(outcomes)).font(.title3).lineLimit(1).minimumScaleFactor(0.5)
            Text(resultMessage).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal)
            Spacer()
            ShareResultButton(message: shareText, headline: shareHeadline, card: shareCard)
                .font(.headline)
                .padding(.bottom, 4)
            Button("Play \(total) more") { playAgain() }
                .font(.headline).foregroundStyle(.white).frame(maxWidth: .infinity).padding().background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
            Button("Back to categories") { finish() }.font(.headline).padding(.bottom)
        }
        .padding()
        .onAppear {
            guard !saved else { return }
            scores.record(category: category, difficulty: difficulty, score: score, total: total)
            let lifetime = scores.addLifetimePoints(points, category: category)
            gameCenter.submit(lifetimePoints: lifetime, category: category)
            Telemetry.log("round_complete", parameters: [
                "category": category.rawValue,
                "difficulty": difficulty.rawValue,
                "score": score,
                "points": points
            ])
            saved = true
        }
    }

    private var shareText: String {
        RoundSummary.round(category: category, difficulty: difficulty, outcomes: outcomes)
    }

    private var shareHeadline: String {
        RoundSummary.headline(correct: score, total: total)
    }

    private var shareCard: ScoreCard {
        ScoreCard(
            title: category.title,
            subtitle: difficulty.title,
            headline: "\(score)/\(total)",
            grid: RoundSummary.grid(outcomes),
            footnote: nil,
            tint: AppTheme.color(for: category)
        )
    }

    private var ratio: Double { total == 0 ? 0 : Double(score) / Double(total) }

    private var resultMessage: String {
        switch ratio {
        case 0.9...: "Outstanding — you really know your stuff."
        case 0.6..<0.9: "A strong round. Can you beat it next time?"
        default: "Every question is a chance to learn something new."
        }
    }
}
