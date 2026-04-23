// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "VNCCCDSample",
    platforms: [
        .iOS(.v15)
    ],
    dependencies: [
        .package(path: "../vncccd-sdk-ios")
    ],
    targets: [
        .executableTarget(
            name: "VNCCCDSample",
            dependencies: [
                .product(name: "VNCCCDSDK", package: "vncccd-sdk-ios")
            ],
            path: "VNCCCDSample"
        )
    ]
)
