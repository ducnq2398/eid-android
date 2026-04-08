// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "VNCCCDSDK",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "VNCCCDSDK",
            targets: ["VNCCCDSDK"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/AndyQ/NFCPassportReader.git", from: "2.3.0")
    ],
    targets: [
        .target(
            name: "VNCCCDSDK",
            dependencies: [
                .product(name: "NFCPassportReader", package: "NFCPassportReader")
            ]
        ),
        .testTarget(
            name: "VNCCCDSDKTests",
            dependencies: ["VNCCCDSDK"]
        )
    ]
)
