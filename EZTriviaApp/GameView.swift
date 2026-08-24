import SwiftUI

struct GameView: View {
    @EnvironmentObject private var scores: ScoreStore
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
                ResultView(category: category, difficulty: difficulty, score: engine.score, total: engine.questions.count, playAgain: nextRound, finish: { dismiss() })
            }
            else if let question = engine.currentQuestion { questionView(question) }
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

    private func questionView(_ question: TriviaQuestion) -> some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: answered ? 14 : 20) {
                    HStack {
                        Text("QUESTION \(engine.currentIndex + 1) OF \(engine.questions.count)").font(.caption.bold()).foregroundStyle(.secondary)
                        Spacer()
                        Label("\(engine.score)", systemImage: "checkmark.circle.fill").font(.subheadline.bold()).foregroundStyle(.green)
                    }
                    ProgressView(value: Double(engine.currentIndex + 1), total: Double(engine.questions.count)).tint(AppTheme.color(for: category))
                    if let visual = question.visual {
                        QuestionVisual(value: visual, maxHeight: visualHeight(fitting: proxy.size.height))
                    }
                    Text(question.prompt)
                        .font(.title2.bold())
                        .frame(maxWidth: .infinity, minHeight: question.visual == nil ? 100 : nil, alignment: question.visual == nil ? .leading : .center)
                        .multilineTextAlignment(question.visual == nil ? .leading : .center)
                        .accessibilityAddTraits(.isHeader)
                    VStack(spacing: 12) {
                        ForEach(question.answers.indices, id: \.self) { index in answerButton(question, index: index) }
                    }
                    if engine.selectedAnswerIndex != nil {
                        VStack(alignment: .leading, spacing: 8) {
                            Label(engine.selectedAnswerIndex == question.correctAnswerIndex ? "Correct!" : "Good try!", systemImage: engine.selectedAnswerIndex == question.correctAnswerIndex ? "checkmark.seal.fill" : "lightbulb.fill")
                                .font(.headline).foregroundStyle(engine.selectedAnswerIndex == question.correctAnswerIndex ? .green : .orange)
                            Text(question.explanation).font(.subheadline).foregroundStyle(.secondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .cardStyle()
                    }
                }
                .padding()
                .animation(.easeInOut(duration: 0.25), value: engine.selectedAnswerIndex)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if engine.selectedAnswerIndex != nil {
                VStack(spacing: 0) {
                    Divider()
                    Button(engine.currentIndex == engine.questions.count - 1 ? "See results" : "Next question") {
                        engine.advance()
                    }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity, minHeight: 30)
                    .padding(.vertical, 14)
                    .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
                    .padding(.horizontal)
                    .padding(.vertical, 10)
                }
                .background(.regularMaterial)
                .transition(.move(edge: .bottom))
            }
        }
        .animation(.easeInOut(duration: 0.25), value: engine.selectedAnswerIndex)
    }

    private var answered: Bool { engine.selectedAnswerIndex != nil }

    /// Height budget for question artwork.
    ///
    /// Sized as a share of the round's actual height rather than a fixed
    /// number, so it adapts from an iPhone SE to a Pro Max instead of being
    /// tuned for one device. It also shrinks once an answer is revealed: the
    /// flag has served its purpose by then, and the explanation card plus the
    /// action bar need the room. The clamps stop it becoming a postage stamp
    /// on a small screen or swallowing the answers on a large one.
    private func visualHeight(fitting available: CGFloat) -> CGFloat {
        let share: CGFloat = answered ? 0.18 : 0.28
        return min(max(available * share, 88), 200)
    }

    private func answerButton(_ question: TriviaQuestion, index: Int) -> some View {
        Button { _ = engine.answer(index) } label: {
            HStack(spacing: 14) {
                Text(String(UnicodeScalar(65 + index)!)).font(.subheadline.bold()).frame(width: 34, height: 34).background(.secondary.opacity(0.12), in: Circle())
                Text(question.answers[index]).font(.body.weight(.semibold)).multilineTextAlignment(.leading)
                Spacer()
                if engine.selectedAnswerIndex != nil && index == question.correctAnswerIndex { Image(systemName: "checkmark.circle.fill").foregroundStyle(.green) }
                else if engine.selectedAnswerIndex == index { Image(systemName: "xmark.circle.fill").foregroundStyle(.red) }
            }
            .foregroundStyle(.primary).padding(15).background(answerBackground(question, index: index), in: RoundedRectangle(cornerRadius: 16)).overlay(RoundedRectangle(cornerRadius: 16).stroke(answerBorder(question, index: index), lineWidth: 2))
        }
        .buttonStyle(.plain).disabled(engine.selectedAnswerIndex != nil)
        .accessibilityLabel("Answer \(String(UnicodeScalar(65 + index)!)): \(question.answers[index])")
    }

    private func answerBackground(_ question: TriviaQuestion, index: Int) -> Color {
        guard engine.selectedAnswerIndex != nil else { return AppTheme.card }
        if index == question.correctAnswerIndex { return .green.opacity(0.12) }
        if index == engine.selectedAnswerIndex { return .red.opacity(0.1) }
        return AppTheme.card
    }

    private func answerBorder(_ question: TriviaQuestion, index: Int) -> Color {
        if engine.selectedAnswerIndex != nil && index == question.correctAnswerIndex { return .green }
        if index == engine.selectedAnswerIndex { return .red }
        return .clear
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

private struct QuestionVisual: View {
    let value: String
    /// Supplied by the round view, which scales it to the screen and to
    /// whether an answer has been revealed. See `visualHeight(fitting:)`.
    let maxHeight: CGFloat

    var body: some View {
        Image(value)
            .resizable()
            .scaledToFit()
            // Nepal is portrait, Switzerland and Vatican City are square, and
            // Qatar is unusually wide. scaledToFit never crops, so these
            // letterbox inside the card rather than fight it. Only a maximum is
            // given in each axis: a fixed minHeight would force a tall empty
            // card around a wide flag like Qatar's.
            .frame(maxWidth: 280, maxHeight: maxHeight)
            .padding(6)
            .background(AppTheme.card, in: RoundedRectangle(cornerRadius: 10))
            .shadow(color: .black.opacity(0.18), radius: 5, y: 2)
            .frame(maxWidth: .infinity)
            // Not hidden: a silent image leaves a VoiceOver user with a prompt
            // that refers to something they are never told exists.
            .accessibilityElement()
            .accessibilityLabel("Flag image for this question")
    }
}

private struct ResultView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager
    let category: TriviaCategory
    let difficulty: TriviaDifficulty
    let score: Int
    let total: Int
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
            Text(resultMessage).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal)
            Spacer()
            Button("Play \(total) more") { playAgain() }
                .font(.headline).foregroundStyle(.white).frame(maxWidth: .infinity).padding().background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
            Button("Back to categories") { finish() }.font(.headline).padding(.bottom)
        }
        .padding()
        .onAppear {
            guard !saved else { return }
            scores.record(category: category, difficulty: difficulty, score: score, total: total)
            gameCenter.submit(score: score, total: total, category: category)
            Telemetry.log("round_complete", parameters: [
                "category": category.rawValue,
                "difficulty": difficulty.rawValue,
                "score": score
            ])
            saved = true
        }
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
