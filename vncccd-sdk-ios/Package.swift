// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "VNCCCDSDK",
    platforms: [
        .iOS(.v15),
        .macOS(.v10_15)
    ],
    products: [
        .library(
            name: "VNCCCDSDK",
            type: .dynamic,
            targets: ["VNCCCDSDK"]
        )
    ],
    dependencies: [
        .package(url: "https://github.com/AndyQ/NFCPassportReader.git", exact: "2.1.2")
    ],
    targets: [
        .target(
            name: "VNCCCDSDK",
            dependencies: [
                .product(
                    name: "NFCPassportReader",
                    package: "NFCPassportReader",
                    condition: .when(platforms: [.iOS])
                )
            ]
        ),
        .testTarget(
            name: "VNCCCDSDKTests",
            dependencies: ["VNCCCDSDK"]
        )
    ]
)
