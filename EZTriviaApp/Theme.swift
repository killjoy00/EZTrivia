import SwiftUI

enum AppTheme {
    static let background = Color(uiColor: .systemGroupedBackground)
    static let card = Color(uiColor: .secondarySystemGroupedBackground)
    static let gradient = LinearGradient(colors: [.indigo, .purple], startPoint: .topLeading, endPoint: .bottomTrailing)

    static func color(for category: TriviaCategory) -> Color {
        switch category {
        case .football: .orange
        case .basketball: .orange
        case .soccer: .green
        case .flags: .blue
        case .history: .brown
        case .science: .purple
        case .movies: .pink
        case .geography: .cyan
        case .music: .indigo
        case .animals: .teal
        case .food: .red
        }
    }
}

extension View {
    func cardStyle() -> some View {
        padding(18)
            .background(AppTheme.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
