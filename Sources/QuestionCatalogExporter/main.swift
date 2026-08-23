import EZTriviaCore
import Foundation

private func csvField(_ value: String) -> String {
    "\"\(value.replacingOccurrences(of: "\"", with: "\"\""))\""
}

let header = [
    "id", "category", "difficulty", "prompt", "visual", "answer_a", "answer_b",
    "answer_c", "answer_d", "correct_answer", "explanation"
]
print(header.map(csvField).joined(separator: ","))

for question in QuestionBank.all.sorted(by: {
    ($0.category.title, $0.difficulty.rawValue, $0.id) < ($1.category.title, $1.difficulty.rawValue, $1.id)
}) {
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
        question.explanation
    ]
    print(row.map(csvField).joined(separator: ","))
}
