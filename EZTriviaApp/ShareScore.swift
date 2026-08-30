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
/// Shares the rendered card as the item, with the App Store link left in
/// `message`'s trailing line for Messages to turn into its own link bubble.
///
/// An earlier version tried to make the card itself the tappable thing by
/// sharing the App Store URL as the item and the card as that URL's preview
/// image. That does not work: Messages generates a link's preview by fetching
/// the destination itself rather than trusting an app-supplied image, so
/// before launch -- while the App Store URL still 404s -- the recipient sees a
/// bare "apps.apple.com" bubble with no image at all. After launch it would
/// likely show Apple's own App Store icon instead of the score card, which is
/// not an improvement either. There is no supported way to make a plain image
/// attachment itself open a link when tapped: only a real link preview is
/// tappable, and Messages owns generating those for the URLs it recognises.
///
/// Renders the card up front rather than on tap. `ImageRenderer` is main-actor
/// work, and doing it while the share sheet is already animating in produces a
/// visible hitch.
struct ShareResultButton: View {
    let message: String
    let headline: String
    let card: ScoreCard
    let label: String
    @State private var rendered: Image?

    init(message: String, headline: String, card: ScoreCard, label: String = "Share result") {
        self.message = message
        self.headline = headline
        self.card = card
        self.label = label
    }

    var body: some View {
        Group {
            if let rendered {
                ShareLink(
                    item: rendered,
                    subject: Text("EZ Trivia"),
                    message: Text(message),
                    preview: SharePreview(headline, image: rendered)
                ) {
                    Label(label, systemImage: "square.and.arrow.up")
                }
            } else {
                // Text-only fallback, used if rendering ever fails. Sharing
                // something is much better than a dead button.
                ShareLink(item: message) {
                    Label(label, systemImage: "square.and.arrow.up")
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
