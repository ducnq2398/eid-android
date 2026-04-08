import Foundation

public final class CCCDReader: @unchecked Sendable {
    private let mrzScanner: MrzScannerService
    private let nfcReader: NfcCardReaderService

    public init(
        mrzScanner: MrzScannerService = UnsupportedMrzScannerService(),
        nfcReader: NfcCardReaderService = UnsupportedNfcCardReaderService()
    ) {
        self.mrzScanner = mrzScanner
        self.nfcReader = nfcReader
    }

    public func startFullFlow(
        config: CCCDConfig = .defaultConfig,
        callback: CCCDCallback
    ) {
        Task {
            do {
                let mrzData = try await mrzScanner.scan(config: config)
                await MainActor.run { callback.onMrzScanned(mrzData) }

                let cccdData = try await nfcReader.readCard(
                    mrzData: mrzData,
                    config: config,
                    onProgress: { status in
                        Task { @MainActor in
                            callback.onNfcProgress(status)
                        }
                    }
                )
                await MainActor.run { callback.onSuccess(cccdData) }
            } catch let error as CCCDError {
                await MainActor.run { callback.onError(error) }
            } catch {
                await MainActor.run { callback.onError(.unknown(details: error.localizedDescription)) }
            }
        }
    }

    public func startMrzScan(
        config: CCCDConfig = .defaultConfig,
        callback: CCCDCallback
    ) {
        Task {
            do {
                let mrzData = try await mrzScanner.scan(config: config)
                await MainActor.run { callback.onMrzScanned(mrzData) }
            } catch let error as CCCDError {
                await MainActor.run { callback.onError(error) }
            } catch {
                await MainActor.run { callback.onError(.unknown(details: error.localizedDescription)) }
            }
        }
    }

    public func startNfcRead(
        mrzData: MrzData,
        config: CCCDConfig = .defaultConfig,
        callback: CCCDCallback
    ) {
        Task {
            do {
                let cccdData = try await nfcReader.readCard(
                    mrzData: mrzData,
                    config: config,
                    onProgress: { status in
                        Task { @MainActor in
                            callback.onNfcProgress(status)
                        }
                    }
                )
                await MainActor.run { callback.onSuccess(cccdData) }
            } catch let error as CCCDError {
                await MainActor.run { callback.onError(error) }
            } catch {
                await MainActor.run { callback.onError(.unknown(details: error.localizedDescription)) }
            }
        }
    }
}
