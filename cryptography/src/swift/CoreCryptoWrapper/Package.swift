// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "CoreCryptoSwift",
    platforms: [.iOS(.v16)],
    products: [
        .library(
            name: "CoreCryptoSwift",
            type: .static,
            targets: ["CoreCryptoSwift"]
        )
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "WireCoreCrypto",
            path: "../../../frameworks/WireCoreCrypto.xcframework"
        ),
        .binaryTarget(
            name: "WireCoreCryptoUniffi",
            path: "../../../frameworks/WireCoreCryptoUniffi.xcframework"
        ),
        .target(
            name: "CoreCryptoSwift",
            dependencies: ["WireCoreCrypto", "WireCoreCryptoUniffi"],
            path: "."
        )
    ]
)
