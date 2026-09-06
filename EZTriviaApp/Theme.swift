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
        case .tv: .mint
        case .geography: .cyan
        case .music: .indigo
        case .animals: .teal
        case .food: .red
        case .literature: Color(red: 0.36, green: 0.24, blue: 0.72)
        case .art: Color(red: 0.86, green: 0.36, blue: 0.20)
        case .mythology: Color(red: 0.55, green: 0.44, blue: 0.16)
        case .videoGames: Color(red: 0.18, green: 0.55, blue: 0.55)
        }
    }
}

extension View {
    /// Caps content at a comfortable measure and centers it.
    ///
    /// iPad is not a large iPhone: a two-column category grid stretched across
    /// ten inches, or a question whose lines run the full width of the screen,
    /// reads as an app that was never opened on the device. Everything stays
    /// full-width on phones, where these caps are wider than the screen.
    ///
    /// The default suits prose and answer buttons. Grids pass a wider value,
    /// since they gain a column rather than growing each card.
    func readableWidth(_ maxWidth: CGFloat = 640) -> some View {
        frame(maxWidth: maxWidth)
            .frame(maxWidth: .infinity)
    }

    func cardStyle() -> some View {
        padding(18)
            .background(AppTheme.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}
