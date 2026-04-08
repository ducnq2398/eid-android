import Foundation

public protocol MrzScannerService: Sendable {
    func scan(config: CCCDConfig) async throws -> MrzData
}

public protocol NfcCardReaderService: Sendable {
    func readCard(
        mrzData: MrzData,
        config: CCCDConfig,
        onProgress: @escaping @Sendable (ReadingStatus) -> Void
    ) async throws -> CCCDData
}

public struct UnsupportedMrzScannerService: MrzScannerService {
    public init() {}

    public func scan(config: CCCDConfig) async throws -> MrzData {
        throw CCCDError.cameraNotAvailable
    }
}

public struct UnsupportedNfcCardReaderService: NfcCardReaderService {
    public init() {}

    public func readCard(
        mrzData: MrzData,
        config: CCCDConfig,
        onProgress: @escaping @Sendable (ReadingStatus) -> Void
    ) async throws -> CCCDData {
        _ = (mrzData, config, onProgress)
        throw CCCDError.nfcNotSupported
    }
}
