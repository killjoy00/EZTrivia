import SwiftUI

struct LeaderboardView: View {
    @EnvironmentObject private var scores: ScoreStore
    @State private var showClearConfirmation = false

    var body: some View {
        Group {
            if scores.entries.isEmpty {
                ContentUnavailableView("No scores yet", systemImage: "trophy", description: Text("Finish a round and your best runs will appear here."))
            } else {
                List {
                    Section("Personal leaderboard") {
                        ForEach(Array(scores.entries.enumerated()), id: \.element.id) { rank, entry in
                            HStack(spacing: 14) {
                                Text("\(rank + 1)").font(.headline.monospacedDigit()).foregroundStyle(.secondary).frame(width: 28)
                                Image(systemName: entry.category.symbol).foregroundStyle(AppTheme.color(for: entry.category)).frame(width: 30)
                                VStack(alignment: .leading) {
                                    Text(entry.category.title).font(.headline)
                                    Text(entry.date, style: .date).font(.caption).foregroundStyle(.secondary)
                                }
                                Spacer()
                                Text("\(entry.score)/\(entry.total)").font(.title3.bold().monospacedDigit())
                            }
                            .padding(.vertical, 5)
                        }
                    }
                }
            }
        }
        .navigationTitle("Leaderboard")
        .toolbar {
            if !scores.entries.isEmpty { Button("Clear") { showClearConfirmation = true } }
        }
        .confirmationDialog("Clear all scores?", isPresented: $showClearConfirmation) {
            Button("Clear Scores", role: .destructive) { scores.clear() }
            Button("Cancel", role: .cancel) {}
        }
    }
}
