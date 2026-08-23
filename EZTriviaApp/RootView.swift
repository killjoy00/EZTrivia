import SwiftUI

struct RootView: View {
    var body: some View {
        TabView {
            NavigationStack { HomeView() }
                .tabItem { Label("Play", systemImage: "play.fill") }
            NavigationStack { LeaderboardView() }
                .tabItem { Label("Scores", systemImage: "trophy.fill") }
        }
    }
}

struct HomeView: View {
    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 22) {
                header
                Text("Choose a category")
                    .font(.title2.bold())
                    .accessibilityAddTraits(.isHeader)
                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(TriviaCategory.allCases) { category in
                        NavigationLink(value: category) { CategoryCard(category: category) }
                            .buttonStyle(.plain)
                    }
                }
                AdBannerView()
            }
            .padding()
        }
        .background(AppTheme.background)
        .navigationDestination(for: TriviaCategory.self) { GameView(category: $0) }
        .navigationTitle("EZTrivia")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var header: some View {
        HStack(spacing: 16) {
            Image(systemName: "brain.head.profile.fill")
                .font(.system(size: 38))
                .foregroundStyle(.white)
                .frame(width: 68, height: 68)
                .background(.white.opacity(0.18), in: RoundedRectangle(cornerRadius: 18))
            VStack(alignment: .leading, spacing: 3) {
                Text("Ready to play?").font(.title2.bold())
                Text("Ten quick questions. One great score.").font(.subheadline).opacity(0.9)
            }
        }
        .foregroundStyle(.white)
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(AppTheme.gradient, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
    }
}

private struct CategoryCard: View {
    @EnvironmentObject private var scores: ScoreStore
    let category: TriviaCategory

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: category.symbol)
                .font(.title2.bold())
                .foregroundStyle(.white)
                .frame(width: 44, height: 44)
                .background(AppTheme.color(for: category), in: RoundedRectangle(cornerRadius: 13))
            Text(category.title).font(.headline).foregroundStyle(.primary)
            Text(category.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
            if let best = scores.bestScore(for: category) {
                Label("Best \(best)/10", systemImage: "star.fill").font(.caption2.bold()).foregroundStyle(AppTheme.color(for: category))
            } else {
                Text("10 questions").font(.caption2.bold()).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, minHeight: 142, alignment: .leading)
        .cardStyle()
        .accessibilityElement(children: .combine)
        .accessibilityHint("Starts a ten-question round")
    }
}

struct AdBannerView: View {
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "sparkles").foregroundStyle(.indigo)
            VStack(alignment: .leading, spacing: 2) {
                Text("Your ad could be here").font(.subheadline.bold())
                Text("Sponsored").font(.caption2).foregroundStyle(.secondary)
            }
            Spacer()
            Text("AD").font(.caption2.bold()).padding(5).overlay(RoundedRectangle(cornerRadius: 4).stroke(.secondary))
        }
        .cardStyle()
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Sponsored advertisement placeholder")
    }
}
