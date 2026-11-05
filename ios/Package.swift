// swift-tools-version: 6.0
import PackageDescription

// xtool SwiftPM project: exactly one library product represents the app.
let package = Package(
    name: "ShopLocal",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(
            name: "ShopLocal",
            targets: ["ShopLocal"]
        ),
    ],
    targets: [
        .target(
            name: "ShopLocal"
        ),
    ]
)
