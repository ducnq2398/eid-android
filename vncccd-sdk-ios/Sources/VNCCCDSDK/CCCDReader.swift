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
        debugLog("startFullFlow")
        Task {
            do {
                let mrzData = try await mrzScanner.scan(config: config)
                await MainActor.run {
                    debugLog("callback.onMrzScanned number=\(mrzData.fullDocumentNumber)")
                    callback.onMrzScanned(mrzData)
                }

                let cccdData = try await nfcReader.readCard(
                    mrzData: mrzData,
                    config: config,
                    onProgress: { status in
                        Task { @MainActor in
                            self.debugLog("callback.onNfcProgress status=\(status.description)")
                            callback.onNfcProgress(status)
                        }
                    }
                )
                await MainActor.run {
                    debugLog("callback.onSuccess mrz=\(cccdData.mrzData.fullDocumentNumber)")
                    callback.onSuccess(cccdData)
                }
            } catch let error as CCCDError {
                await MainActor.run {
                    debugLog("callback.onError cccdError=\(error.message)")
                    callback.onError(error)
                }
            } catch {
                await MainActor.run {
                    let mapped = CCCDError.unknown(details: error.localizedDescription)
                    debugLog("callback.onError unknown=\(mapped.message)")
                    callback.onError(mapped)
                }
            }
        }
    }

    public func startMrzScan(
        config: CCCDConfig = .defaultConfig,
        callback: CCCDCallback
    ) {
        debugLog("startMrzScan")
        Task {
            do {
                let mrzData = try await mrzScanner.scan(config: config)
                await MainActor.run {
                    debugLog("callback.onMrzScanned number=\(mrzData.fullDocumentNumber)")
                    callback.onMrzScanned(mrzData)
                }
            } catch let error as CCCDError {
                await MainActor.run {
                    debugLog("callback.onError cccdError=\(error.message)")
                    callback.onError(error)
                }
            } catch {
                await MainActor.run {
                    let mapped = CCCDError.unknown(details: error.localizedDescription)
                    debugLog("callback.onError unknown=\(mapped.message)")
                    callback.onError(mapped)
                }
            }
        }
    }

    public func startNfcRead(
        mrzData: MrzData,
        config: CCCDConfig = .defaultConfig,
        callback: CCCDCallback
    ) {
        debugLog("startNfcRead number=\(mrzData.fullDocumentNumber)")
        Task {
            do {
                let cccdData = try await nfcReader.readCard(
                    mrzData: mrzData,
                    config: config,
                    onProgress: { status in
                        Task { @MainActor in
                            self.debugLog("callback.onNfcProgress status=\(status.description)")
                            callback.onNfcProgress(status)
                        }
                    }
                )
                await MainActor.run {
                    debugLog("callback.onSuccess mrz=\(cccdData.mrzData.fullDocumentNumber)")
                    callback.onSuccess(cccdData)
                }
            } catch let error as CCCDError {
                await MainActor.run {
                    debugLog("callback.onError cccdError=\(error.message)")
                    callback.onError(error)
                }
            } catch {
                await MainActor.run {
                    let mapped = CCCDError.unknown(details: error.localizedDescription)
                    debugLog("callback.onError unknown=\(mapped.message)")
                    callback.onError(mapped)
                }
            }
        }
    }

    private func debugLog(_ message: String) {
        #if DEBUG
        print("[VNCCCDSDK][CALLBACK] \(message)")
        #endif
    }
}
