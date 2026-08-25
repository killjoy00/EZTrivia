import SwiftUI

/// The card rendered into an image when a player shares a result.
///
/// Drawn at a fixed 600×600 rather than sized to the device so a shared image
/// looks the same whoever sent it, and so it lands as a square in the places
/// these get pasted.
struct ScoreCard: View {
    let title: String
    let subtitle: String
    let headline: String
    let grid: String
    let footnote: String?
    let tint: Color

    var body: some View {
        VStack(spacing: 26) {
            VStack(spacing: 6) {
                Text("EZ TRIVIA")
                    .font(.system(size: 22, weight: .heavy, design: .rounded))
                    .tracking(4)
                    .foregroundStyle(.white.opacity(0.75))
                Text(title)
                    .font(.system(size: 40, weight: .bold, design: .rounded))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                Text(subtitle)
                    .font(.system(size: 22, weight: .medium, design: .rounded))
                    .foregroundStyle(.white.opacity(0.8))
            }

            Text(headline)
                .font(.system(size: 92, weight: .heavy, design: .rounded))
                .foregroundStyle(.white)
                .minimumScaleFactor(0.5)
                .lineLimit(1)

            Text(grid)
                .font(.system(size: 34))
                .lineLimit(1)
                .minimumScaleFactor(0.4)

            if let footnote {
                Text(footnote)
                    .font(.system(size: 24, weight: .semibold, design: .rounded))
                    .foregroundStyle(.white.opacity(0.9))
            }
        }
        .padding(44)
        .frame(width: 600, height: 600)
        .background(
            LinearGradient(colors: [tint, tint.opacity(0.55)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
    }
}

/// Share button for a finished round.
///
/// Shares the App Store link as the item, with the rendered score card as that
/// link's preview image. The card is therefore the tappable thing: a recipient
/// gets one rich bubble showing the score that opens the App Store when tapped,
/// rather than an image attachment sitting next to a bare URL that reads like
/// an advert stapled to a screenshot.
///
/// Renders the card up front rather than on tap. `ImageRenderer` is main-actor
/// work, and doing it while the share sheet is already animating in produces a
/// visible hitch.
struct ShareResultButton: View {
    let message: String
    let headline: String
    let card: ScoreCard
    @State private var rendered: Image?

    private var link: URL {
        // The literal is a compile-time constant that is known to parse, so the
        // fallback is unreachable; it exists only to avoid forcing.
        URL(string: RoundSummary.appStoreURL) ?? URL(string: "https://apps.apple.com")!
    }

    var body: some View {
        Group {
            if let rendered {
                ShareLink(
                    item: link,
                    subject: Text("EZ Trivia"),
                    message: Text(message),
                    preview: SharePreview(headline, image: rendered)
                ) {
                    Label("Share result", systemImage: "square.and.arrow.up")
                }
            } else {
                // Used only if rendering ever fails. The link is still the item,
                // so the share is never reduced to a bare URL -- it just loses
                // the card.
                ShareLink(
                    item: link,
                    subject: Text("EZ Trivia"),
                    message: Text(message),
                    preview: SharePreview(headline)
                ) {
                    Label("Share result", systemImage: "square.and.arrow.up")
                }
            }
        }
        .task {
            guard rendered == nil else { return }
            let renderer = ImageRenderer(content: card)
            renderer.scale = 2
            if let image = renderer.uiImage {
                rendered = Image(uiImage: image)
            }
        }
    }
}
