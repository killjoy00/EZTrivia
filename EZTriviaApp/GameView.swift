import SwiftUI

struct GameView: View {
    @EnvironmentObject private var scores: ScoreStore
    @Environment(\.dismiss) private var dismiss
    let category: TriviaCategory
    @State private var engine: TriviaEngine
    @State private var seenIDs: Set<String>
    @State private var showExitConfirmation = false

    init(category: TriviaCategory) {
        self.category = category
        let round = QuestionPicker.round(category: category)
        _engine = State(initialValue: TriviaEngine(questions: round))
        _seenIDs = State(initialValue: Set(round.map(\.id)))
    }

    var body: some View {
        Group {
            if engine.isRoundComplete { ResultView(category: category, score: engine.score, playAgain: nextRound, finish: { dismiss() }) }
            else if let question = engine.currentQuestion { questionView(question) }
        }
        .background(AppTheme.background.ignoresSafeArea())
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) {
                Button { showExitConfirmation = true } label: { Image(systemName: "xmark") }
                    .accessibilityLabel("Exit round")
            }
            ToolbarItem(placement: .principal) { Text(category.title).font(.headline) }
        }
        .confirmationDialog("Leave this round?", isPresented: $showExitConfirmation, titleVisibility: .visible) {
            Button("Leave Round", role: .destructive) { dismiss() }
            Button("Keep Playing", role: .cancel) {}
        } message: { Text("Your current score won't be saved.") }
    }

    private func questionView(_ question: TriviaQuestion) -> some View {
        ScrollView {
            VStack(spacing: 20) {
                HStack {
                    Text("QUESTION \(engine.currentIndex + 1) OF \(engine.questions.count)").font(.caption.bold()).foregroundStyle(.secondary)
                    Spacer()
                    Label("\(engine.score)", systemImage: "checkmark.circle.fill").font(.subheadline.bold()).foregroundStyle(.green)
                }
                ProgressView(value: Double(engine.currentIndex + 1), total: Double(engine.questions.count)).tint(AppTheme.color(for: category))
                if let visual = question.visual {
                    Text(visual)
                        .font(.system(size: 104))
                        .frame(maxWidth: .infinity, minHeight: 116)
                        .accessibilityHidden(true)
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
                    Button(engine.currentIndex == engine.questions.count - 1 ? "See results" : "Next question") { engine.advance() }
                        .font(.headline).foregroundStyle(.white).frame(maxWidth: .infinity).padding()
                        .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
                }
            }
            .padding()
        }
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
        let next = QuestionPicker.round(category: category, excluding: seenIDs)
        seenIDs.formUnion(next.map(\.id))
        engine = TriviaEngine(questions: next)
    }
}

private struct ResultView: View {
    @EnvironmentObject private var scores: ScoreStore
    let category: TriviaCategory
    let score: Int
    let playAgain: () -> Void
    let finish: () -> Void
    @State private var saved = false

    var body: some View {
        VStack(spacing: 22) {
            Spacer()
            Image(systemName: score >= 8 ? "trophy.fill" : "sparkles")
                .font(.system(size: 54)).foregroundStyle(.yellow).frame(width: 108, height: 108).background(.yellow.opacity(0.15), in: Circle())
            Text(score >= 8 ? "Trivia champion!" : score >= 5 ? "Nice work!" : "Keep learning!").font(.largeTitle.bold())
            Text("You scored").foregroundStyle(.secondary)
            Text("\(score) / 10").font(.system(size: 52, weight: .bold, design: .rounded)).foregroundStyle(AppTheme.color(for: category))
            Text(resultMessage).font(.body).foregroundStyle(.secondary).multilineTextAlignment(.center).padding(.horizontal)
            Spacer()
            Button("Play 10 more") { playAgain() }
                .font(.headline).foregroundStyle(.white).frame(maxWidth: .infinity).padding().background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
            Button("Back to categories") { finish() }.font(.headline).padding(.bottom)
        }
        .padding()
        .onAppear {
            guard !saved else { return }
            scores.record(category: category, score: score, total: 10)
            saved = true
        }
    }

    private var resultMessage: String {
        switch score {
        case 9...10: "Outstanding — you really know your stuff."
        case 6...8: "A strong round. Can you beat it next time?"
        default: "Every question is a chance to learn something new."
        }
    }
}
