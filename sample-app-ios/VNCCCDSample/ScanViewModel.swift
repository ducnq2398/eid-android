import SwiftUI
import VNCCCDSDK

// MARK: - View Model
@MainActor
final class ScanViewModel: ObservableObject {
    @Published var nfcStatus: NfcStatusType = .checking
    @Published var scanStatus: String = ""
    @Published var isScanning: Bool = false
    @Published var showResult: Bool = false
    @Published var cccdResult: CCCDData?
    @Published var mrzResult: MrzData?
    @Published var errorMessage: String?
    @Published var showMrzResult: Bool = false
    @Published var progressSteps: [ProgressStep] = []

    enum NfcStatusType {
        case checking
        case ready
        case disabled
        case notSupported
    }

    struct ProgressStep: Identifiable {
        let id = UUID()
        let status: ReadingStatus
        var isActive: Bool = false
        var isCompleted: Bool = false
    }

    private lazy var reader: CCCDReader = {
        CCCDReader(
            mrzScanner: VisionMrzScannerService(),
            nfcReader: CoreNfcCardReaderService()
        )
    }()

    func checkNfcStatus() {
        #if targetEnvironment(simulator)
        nfcStatus = .notSupported
        #else
        nfcStatus = .ready
        #endif
    }

    func startFullScan() {
        resetState()
        isScanning = true
        scanStatus = "Đang khởi tạo..."
        initProgressSteps()

        let config = CCCDConfig(
            readFaceImage: true,
            readPersonalInfo: true,
            nfcTimeoutMs: 30_000,
            mrzConsecutiveFrames: 3,
            enableVibration: true
        )

        reader.startFullFlow(config: config, callback: ScanCallbackHandler(viewModel: self))
    }

    func startMrzOnly() {
        resetState()
        isScanning = true
        scanStatus = "Đang mở camera..."

        let config = CCCDConfig(mrzConsecutiveFrames: 3)
        reader.startMrzScan(config: config, callback: MrzOnlyCallbackHandler(viewModel: self))
    }

    private func resetState() {
        errorMessage = nil
        cccdResult = nil
        mrzResult = nil
        showResult = false
        showMrzResult = false
        progressSteps = []
    }

    private func initProgressSteps() {
        progressSteps = ReadingStatus.allCases.map {
            ProgressStep(status: $0)
        }
    }

    func updateProgress(_ status: ReadingStatus) {
        for i in progressSteps.indices {
            if progressSteps[i].status == status {
                progressSteps[i].isActive = true
            } else if progressSteps[i].isActive {
                progressSteps[i].isActive = false
                progressSteps[i].isCompleted = true
            }
        }
    }
}

// MARK: - Full Flow Callback
private final class ScanCallbackHandler: CCCDCallback {
    private weak var viewModel: ScanViewModel?

    init(viewModel: ScanViewModel) {
        self.viewModel = viewModel
    }

    func onMrzScanned(_ mrzData: MrzData) {
        Task { @MainActor in
            #if DEBUG
            print("[VNCCCDSample][MRZ] number=\(mrzData.fullDocumentNumber) dob=\(mrzData.dateOfBirth) doe=\(mrzData.dateOfExpiry)")
            #endif
            viewModel?.scanStatus = "MRZ: \(mrzData.fullDocumentNumber)\nĐang chờ đọc NFC..."
        }
    }

    func onNfcProgress(_ status: ReadingStatus) {
        Task { @MainActor in
            #if DEBUG
            print("[VNCCCDSample][NFC] progress=\(status.description)")
            #endif
            viewModel?.scanStatus = status.description
            viewModel?.updateProgress(status)
        }
    }

    func onSuccess(_ cccdData: CCCDData) {
        Task { @MainActor in
            #if DEBUG
            print("[VNCCCDSample][NFC] success mrz=\(cccdData.mrzData.fullDocumentNumber) dg1=\(cccdData.rawDG1?.count ?? 0) dg2=\(cccdData.rawDG2?.count ?? 0) dg13=\(cccdData.rawDG13?.count ?? 0)")
            #endif
            viewModel?.cccdResult = cccdData
            viewModel?.isScanning = false
            viewModel?.scanStatus = "Thành công!"
            viewModel?.showResult = true
        }
    }

    func onError(_ error: CCCDError) {
        Task { @MainActor in
            #if DEBUG
            print("[VNCCCDSample][NFC] error=\(error.message)")
            #endif
            viewModel?.isScanning = false
            viewModel?.scanStatus = ""
            viewModel?.errorMessage = error.message
        }
    }
}

// MARK: - MRZ Only Callback
private final class MrzOnlyCallbackHandler: CCCDCallback {
    private weak var viewModel: ScanViewModel?

    init(viewModel: ScanViewModel) {
        self.viewModel = viewModel
    }

    func onMrzScanned(_ mrzData: MrzData) {
        Task { @MainActor in
            #if DEBUG
            print("[VNCCCDSample][MRZ] scanned number=\(mrzData.fullDocumentNumber)")
            #endif
            viewModel?.mrzResult = mrzData
            viewModel?.isScanning = false
            viewModel?.showMrzResult = true
        }
    }

    func onError(_ error: CCCDError) {
        Task { @MainActor in
            #if DEBUG
            print("[VNCCCDSample][MRZ] error=\(error.message)")
            #endif
            viewModel?.isScanning = false
            viewModel?.scanStatus = ""
            viewModel?.errorMessage = error.message
        }
    }
}
