import EZTriviaCore
import Foundation

// Emits the full question catalog as CSV for content review.
//
// Usage: swift run QuestionCatalogExporter > QuestionReview.csv

private func csvField(_ value: String) -> String {
    "\"\(value.replacingOccurrences(of: "\"", with: "\"\""))\""
}

/// Flags whose artwork or naming is genuinely contested, surfaced in the export
/// so a reviewer can make a call rather than discover it from a user complaint.
private let flagReviewNotes: [String: String] = [
    "flag-aq": "Artwork is Graham Bartram's unofficial design; Antarctica has no officially adopted sovereign flag.",
    "flag-bq": "Artwork is Bonaire's flag; the Caribbean Netherlands has no collective flag.",
    "flag-gp": "Artwork is an unofficial local flag. The official flag of Guadeloupe is the French tricolour.",
    "flag-gf": "Artwork is an unofficial local flag. The official flag of French Guiana is the French tricolour.",
    "flag-bl": "Artwork is an unofficial armorial flag. The official flag of Saint Barthelemy is the French tricolour.",
    "flag-mq": "Artwork is a local flag rather than the official French tricolour.",
    "flag-re": "Artwork is an unofficial local flag. The official flag of Reunion is the French tricolour.",
    "flag-yt": "Artwork is an unofficial local flag. The official flag of Mayotte is the French tricolour.",
    "flag-wf": "Artwork is a local flag rather than the official French tricolour.",
    "flag-pm": "Artwork is a widely used unofficial local flag. The official flag is the French tricolour.",
    "flag-ps": "Palestine's status is disputed; included as a territory.",
    "flag-tw": "Taiwan's status is disputed. Listed under its common name rather than the ISO label.",
    "flag-np": "Artwork is portrait (320x390) against a landscape norm; it letterboxes in the question view.",
    "flag-ch": "Artwork is square (320x320) against a landscape norm; it letterboxes in the question view.",
    "flag-va": "Artwork is square (320x320) against a landscape norm; it letterboxes in the question view.",
    "flag-qa": "Artwork is unusually wide (320x126); it letterboxes vertically in the question view."
]

private let difficultyOrder: [TriviaDifficulty: Int] = [.easy: 0, .medium: 1, .hard: 2]

/// Trailing integer of an ID such as "football-easy-12", so rows sort 2, 3, 10
/// rather than 10, 2, 3.
///
/// Flag ids end in a country code rather than a number and all return 0 here;
/// the id itself is the tiebreaker below, which orders them by code.
private func sequence(of id: String) -> Int {
    Int(id.split(separator: "-").last.map(String.init) ?? "") ?? 0
}

let header = [
    "id", "category", "difficulty", "prompt", "visual", "answer_a", "answer_b",
    "answer_c", "answer_d", "correct_answer", "explanation", "review_note"
]
print(header.map(csvField).joined(separator: ","))

// The id is the final tiebreaker so the ordering is total. Swift's sort is not
// stable, so a comparator that called two rows equal would leave their order up
// to the sort implementation -- and this file is diffed in CI.
let sorted = QuestionBank.all.sorted {
    let left = ($0.category.title, difficultyOrder[$0.difficulty] ?? 0, sequence(of: $0.id), $0.id)
    let right = ($1.category.title, difficultyOrder[$1.difficulty] ?? 0, sequence(of: $1.id), $1.id)
    return left < right
}

for question in sorted {
    let row = [
        question.id,
        question.category.title,
        question.difficulty.title,
        question.prompt,
        question.visual ?? "",
        question.answers[0],
        question.answers[1],
        question.answers[2],
        question.answers[3],
        question.answers[question.correctAnswerIndex],
        question.explanation,
        question.visual.flatMap { flagReviewNotes[$0] } ?? ""
    ]
    print(row.map(csvField).joined(separator: ","))
}
