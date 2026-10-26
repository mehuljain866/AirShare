// swift-tools-version:5.9
// (language mode 5 is the default for tools-version 5.9)
import PackageDescription

let package = Package(
    name: "AirShareCore",
    defaultLocalization: "en",
    platforms: [.macOS(.v14), .iOS(.v17)],
    products: [
        .library(name: "AirShareCore", targets: ["AirShareCore"]),
    ],
    targets: [
        .target(
            name: "AirShareCore",
            path: "Sources/AirShareCore",
            resources: [.process("Resources")],
            swiftSettings: []
        ),
        .testTarget(
            name: "AirShareCoreTests",
            dependencies: ["AirShareCore"],
            path: "Tests/AirShareCoreTests",
            resources: [.copy("Fixtures")],
            swiftSettings: []
        ),
    ]
)
