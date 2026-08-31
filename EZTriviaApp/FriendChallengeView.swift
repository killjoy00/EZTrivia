import SwiftUI
import UIKit

enum FriendChallengeRoute: Hashable {
    case lobby
    case play(seed: UInt64, invitation: FriendChallengeCode?)
}

struct FriendChallengeCard: View {
    var body: some View {
        NavigationLink(value: FriendChallengeRoute.lobby) {
            HStack(spacing: 16) {
                Image(systemName: "person.2.fill")
                    .font(.system(size: 25, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 62, height: 62)
                    .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 16))

                VStack(alignment: .leading, spacing: 3) {
                    Text("Friend Challenge").font(.headline)
                    Text("Play a random set, then share it.")
                        .font(.subheadline)
                        .opacity(0.9)
                }
                .foregroundStyle(.white)

                Spacer(minLength: 0)
                Image(systemName: "chevron.right")
                    .font(.body.bold())
                    .foregroundStyle(.white.opacity(0.8))
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                LinearGradient(
                    colors: [.indigo, .blue],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ),
                in: RoundedRectangle(cornerRadius: 22, style: .continuous)
            )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Friend Challenge. Play a random set and share it with a friend.")
    }
}

struct FriendChallengeLobbyView: View {
    @EnvironmentObject private var router: PlayRouter
    @EnvironmentObject private var scores: ScoreStore
    @State private var codeText = ""
    @State private var triedInvalidCode = false

    private var parsedCode: FriendChallengeCode? { FriendChallengeCode(codeText) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Same questions. One attempt.")
                        .font(.title.bold())
                    Text("Each challenge draws ten categories with the same Easy-to-Hard progression as the Daily Challenge.")
                        .foregroundStyle(.secondary)
                }

                Button(action: createChallenge) {
                    HStack(spacing: 14) {
                        Image(systemName: "sparkles")
                            .font(.title2.bold())
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Create a challenge").font(.headline)
                            Text("Your share code appears after the round.")
                                .font(.caption)
                                .opacity(0.85)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.body.bold())
                    }
                    .foregroundStyle(.white)
                    .padding(18)
                    .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 18))
                }
                .buttonStyle(.plain)

                VStack(alignment: .leading, spacing: 12) {
                    Text("Have a challenge code?").font(.headline)
                    TextField("EZ2-XXXX-XXXX-XXXX-XXXX-XXX", text: $codeText)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .keyboardType(.asciiCapable)
                        .font(.body.monospaced())
                        .textFieldStyle(.roundedBorder)
                        .submitLabel(.go)
                        .onSubmit(playEnteredCode)
                        .onChange(of: codeText) { _, _ in triedInvalidCode = false }

                    if let parsedCode {
                        Label(
                            "Target: \(parsedCode.targetScore)/10 · \(parsedCode.targetPoints.formatted()) points",
                            systemImage: scores.friendResult(for: parsedCode) == nil
                                ? "checkmark.circle.fill"
                                : "checkmark.seal.fill"
                        )
                        .font(.subheadline.bold())
                        .foregroundStyle(.green)
                    } else if triedInvalidCode {
                        Label("That code is incomplete or has a typo.", systemImage: "exclamationmark.triangle.fill")
                            .font(.subheadline)
                            .foregroundStyle(.orange)
                    }

                    Button(scores.friendResult(for: parsedCode) == nil ? "Play this challenge" : "View your result") {
                        playEnteredCode()
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(parsedCode == nil)
                }
                .cardStyle()
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationTitle("Friend Challenge")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func createChallenge() {
        var seed = UInt64.random(in: UInt64.min...UInt64.max)
        while scores.friendResult(forAttemptID: "v\(FriendChallenge.codeVersion)-\(seed)") != nil {
            seed = UInt64.random(in: UInt64.min...UInt64.max)
        }
        router.path.append(FriendChallengeRoute.play(seed: seed, invitation: nil))
    }

    private func playEnteredCode() {
        guard let parsedCode else {
            triedInvalidCode = true
            return
        }
        codeText = parsedCode.displayString
        router.path.append(FriendChallengeRoute.play(seed: parsedCode.seed, invitation: parsedCode))
    }
}

struct FriendChallengeGameView: View {
    @EnvironmentObject private var scores: ScoreStore
    @EnvironmentObject private var gameCenter: GameCenterManager
    @EnvironmentObject private var feedback: Feedback
    @EnvironmentObject private var router: PlayRouter
    @Environment(\.dismiss) private var dismiss

    let seed: UInt64
    let invitation: FriendChallengeCode?

    @State private var engine = TriviaEngine(questions: [])
    @State private var started = false
    @State private var showExitConfirmation = false

    private var attemptID: String { "v\(FriendChallenge.codeVersion)-\(seed)" }
    private var savedResult: FriendChallengeResult? {
        scores.friendResult(forAttemptID: attemptID)
    }

    var body: some View {
        Group {
            if let savedResult {
                FriendChallengeResultView(result: savedResult) { router.popToRoot() }
            } else if engine.isRoundComplete && !engine.questions.isEmpty {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if engine.currentQuestion != nil {
                RoundPlayer(
                    engine: $engine,
                    tint: .indigo,
                    finishTitle: invitation == nil ? "Create challenge" : "See results",
                    onAnswer: { correct in
                        if correct { feedback.correct() } else { feedback.wrong() }
                    },
                    onAdvance: { if engine.isRoundComplete { finishRound() } }
                )
            } else {
                ProgressView("Preparing challenge…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(AppTheme.background.ignoresSafeArea())
        .toolbar(.hidden, for: .tabBar)
        .navigationBarBackButtonHidden(savedResult == nil)
        .toolbar {
            if savedResult == nil {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showExitConfirmation = true } label: { Image(systemName: "xmark") }
                        .accessibilityLabel("Exit friend challenge")
                }
            }
            ToolbarItem(placement: .principal) {
                Text("Friend Challenge").font(.headline)
            }
        }
        .confirmationDialog("Leave this challenge?", isPresented: $showExitConfirmation, titleVisibility: .visible) {
            Button("Leave", role: .destructive) { dismiss() }
            Button("Keep Playing", role: .cancel) {}
        } message: {
            Text("Your attempt is saved only after all ten questions. You can enter the same code again if you leave now.")
        }
        .onAppear {
            guard !started, savedResult == nil else { return }
            started = true
            feedback.prepare()
            engine = TriviaEngine(questions: FriendChallenge.challenge(for: seed).questions)
        }
    }

    private func finishRound() {
        feedback.roundComplete()
        let code = invitation ?? FriendChallengeCode(
            seed: seed,
            targetScore: engine.score,
            targetPoints: engine.points
        )
        let result = FriendChallengeResult(
            code: code,
            score: engine.score,
            total: engine.questions.count,
            points: engine.points,
            outcomes: engine.outcomes,
            createdChallenge: invitation == nil
        )
        scores.recordFriendChallenge(result, categories: Set(engine.questions.map(\.category)))
        Task {
            await gameCenter.syncAchievements(localProgress: AchievementCatalog.progress(using: scores))
        }
        Telemetry.log("friend_challenge_complete", parameters: [
            "created": invitation == nil,
            "score": result.score,
            "points": result.points
        ])
    }
}

private struct FriendChallengeResultView: View {
    let result: FriendChallengeResult
    let finish: () -> Void
    @State private var copied = false

    private var beatTarget: Bool { result.points > result.code.targetPoints }
    private var tiedTarget: Bool { result.points == result.code.targetPoints }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Image(systemName: result.createdChallenge ? "paperplane.fill" : resultIcon)
                    .font(.system(size: 48))
                    .foregroundStyle(.indigo)
                    .frame(width: 104, height: 104)
                    .background(.indigo.opacity(0.14), in: Circle())

                Text(resultTitle)
                    .font(.title.bold())
                    .multilineTextAlignment(.center)

                Text("\(result.score) / \(result.total)")
                    .font(.system(size: 50, weight: .bold, design: .rounded))
                    .foregroundStyle(.indigo)

                Text(RoundSummary.grid(result.outcomes))
                    .font(.title3)
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)

                HStack(spacing: 28) {
                    stat(result.points.formatted(), "your points")
                    if !result.createdChallenge {
                        stat(result.code.targetPoints.formatted(), "target")
                    }
                }

                VStack(spacing: 10) {
                    Text(result.createdChallenge ? "Share this code" : "Challenge code")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(result.code.displayString)
                        .font(.callout.bold().monospaced())
                        .lineLimit(1)
                        .minimumScaleFactor(0.65)
                        .textSelection(.enabled)
                    Button {
                        UIPasteboard.general.string = result.code.displayString
                        copied = true
                    } label: {
                        Label(copied ? "Copied" : "Copy code", systemImage: copied ? "checkmark" : "doc.on.doc")
                    }
                    .buttonStyle(.bordered)
                }
                .frame(maxWidth: .infinity)
                .cardStyle()

                ShareResultButton(
                    message: shareText,
                    headline: shareHeadline,
                    card: shareCard,
                    label: result.createdChallenge ? "Challenge a friend" : "Share result"
                )
                .font(.headline)

                Button("Back to categories") { finish() }
                    .font(.headline)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 16))
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

    private var resultIcon: String {
        if beatTarget { return "trophy.fill" }
        if tiedTarget { return "equal.circle.fill" }
        return "sparkles"
    }

    private var resultTitle: String {
        if result.createdChallenge { return "Challenge ready" }
        if beatTarget { return "You beat the challenge!" }
        if tiedTarget { return "It’s a tie!" }
        return "Challenge complete"
    }

    private var shareHeadline: String {
        result.createdChallenge
            ? "Can you beat \(result.points.formatted()) points?"
            : "I scored \(result.score)/\(result.total) on an EZ Trivia challenge"
    }

    private var shareText: String {
        if result.createdChallenge {
            return [
                "I challenge you to EZ Trivia!",
                "Beat \(result.score)/\(result.total) — \(result.points.formatted()) points — on the exact same questions.",
                "Challenge code: \(result.code.displayString)",
                "Open EZ Trivia, choose Friend Challenge, and enter the code.",
                RoundSummary.appStoreURL
            ].joined(separator: "\n")
        }
        return [
            "EZ Trivia Friend Challenge — \(result.score)/\(result.total)",
            RoundSummary.grid(result.outcomes),
            "\(result.points.formatted()) points · target \(result.code.targetPoints.formatted())",
            "Challenge code: \(result.code.displayString)",
            RoundSummary.appStoreURL
        ].joined(separator: "\n")
    }

    private var shareCard: ScoreCard {
        ScoreCard(
            title: "Friend Challenge",
            subtitle: result.createdChallenge ? "Can you beat me?" : "Challenge result",
            headline: "\(result.score)/\(result.total)",
            grid: RoundSummary.grid(result.outcomes),
            footnote: result.createdChallenge
                ? "\(result.points.formatted()) point target"
                : "Target: \(result.code.targetPoints.formatted()) points",
            tint: .indigo
        )
    }
}

private extension ScoreStore {
    func friendResult(for code: FriendChallengeCode?) -> FriendChallengeResult? {
        guard let code else { return nil }
        return friendResult(for: code)
    }
}
