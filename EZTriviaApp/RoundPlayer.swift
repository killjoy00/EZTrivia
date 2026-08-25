import SwiftUI

/// The question-and-answers screen, shared by category rounds and the daily
/// challenge.
///
/// Extracted so the two modes cannot drift apart. The layout here carries
/// several fixes that were expensive to find — the artwork sizing, the pinned
/// action bar, the hidden tab bar — and a second hand-maintained copy would
/// quietly lose them.
struct RoundPlayer: View {
    @Binding var engine: TriviaEngine
    let tint: Color
    /// Label for the final button of the round.
    let finishTitle: String
    let onAnswer: (Bool) -> Void
    let onAdvance: () -> Void

    var body: some View {
        GeometryReader { proxy in
            ScrollView {
                VStack(spacing: answered ? 14 : 20) {
                    header
                    if let question = engine.currentQuestion {
                        if let visual = question.visual {
                            QuestionVisual(value: visual, maxHeight: visualHeight(fitting: proxy.size.height))
                        }
                        Text(question.prompt)
                            .font(.title2.bold())
                            .frame(
                                maxWidth: .infinity,
                                minHeight: question.visual == nil ? 100 : nil,
                                alignment: question.visual == nil ? .leading : .center
                            )
                            .multilineTextAlignment(question.visual == nil ? .leading : .center)
                            .accessibilityAddTraits(.isHeader)
                        VStack(spacing: 12) {
                            ForEach(question.answers.indices, id: \.self) { index in
                                answerButton(question, index: index)
                            }
                        }
                        if engine.selectedAnswerIndex != nil {
                            explanation(question)
                        }
                    }
                }
                .padding()
                .animation(.easeInOut(duration: 0.25), value: engine.selectedAnswerIndex)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) { actionBar }
        .animation(.easeInOut(duration: 0.25), value: engine.selectedAnswerIndex)
    }

    private var header: some View {
        VStack(spacing: 12) {
            HStack {
                Text("QUESTION \(engine.currentIndex + 1) OF \(engine.questions.count)")
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
                Spacer()
                Label("\(engine.score)", systemImage: "checkmark.circle.fill")
                    .font(.subheadline.bold())
                    .foregroundStyle(.green)
            }
            ProgressView(value: Double(engine.currentIndex + 1), total: Double(max(engine.questions.count, 1)))
                .tint(tint)
        }
    }

    private func explanation(_ question: TriviaQuestion) -> some View {
        let correct = engine.selectedAnswerIndex == question.correctAnswerIndex
        return VStack(alignment: .leading, spacing: 8) {
            Label(correct ? "Correct!" : "Good try!", systemImage: correct ? "checkmark.seal.fill" : "lightbulb.fill")
                .font(.headline)
                .foregroundStyle(correct ? .green : .orange)
            Text(question.explanation)
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .cardStyle()
    }

    @ViewBuilder
    private var actionBar: some View {
        if engine.selectedAnswerIndex != nil {
            VStack(spacing: 0) {
                Divider()
                Button(engine.currentIndex == engine.questions.count - 1 ? finishTitle : "Next question") {
                    engine.advance()
                    onAdvance()
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
        Button {
            let wasCorrect = engine.answer(index)
            onAnswer(wasCorrect)
        } label: {
            HStack(spacing: 14) {
                Text(String(UnicodeScalar(65 + index)!))
                    .font(.subheadline.bold())
                    .frame(width: 34, height: 34)
                    .background(.secondary.opacity(0.12), in: Circle())
                Text(question.answers[index])
                    .font(.body.weight(.semibold))
                    .multilineTextAlignment(.leading)
                Spacer()
                if engine.selectedAnswerIndex != nil && index == question.correctAnswerIndex {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                } else if engine.selectedAnswerIndex == index {
                    Image(systemName: "xmark.circle.fill").foregroundStyle(.red)
                }
            }
            .foregroundStyle(.primary)
            .padding(15)
            .background(answerBackground(question, index: index), in: RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(answerBorder(question, index: index), lineWidth: 2))
        }
        .buttonStyle(.plain)
        .disabled(engine.selectedAnswerIndex != nil)
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
}

struct QuestionVisual: View {
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
