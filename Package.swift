// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "EZTriviaCore",
    platforms: [.iOS(.v17)],
    products: [.library(name: "EZTriviaCore", targets: ["EZTriviaCore"])],
    targets: [
        .target(name: "EZTriviaCore"),
        .executableTarget(name: "QuestionCatalogExporter", dependencies: ["EZTriviaCore"]),
        .testTarget(name: "EZTriviaCoreTests", dependencies: ["EZTriviaCore"])
    ]
)
