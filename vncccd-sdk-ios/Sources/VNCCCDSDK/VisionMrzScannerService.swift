#if canImport(AVFoundation) && canImport(UIKit) && canImport(Vision)
import AVFoundation
import Foundation
import UIKit
import Vision

public struct VisionMrzScannerService: MrzScannerService {
    public init() {}

    public func scan(config: CCCDConfig) async throws -> MrzData {
        try await VisionMrzFlowController.scan(config: config)
    }
}

@MainActor
private final class VisionMrzFlowController {
    private let config: CCCDConfig
    private var continuation: CheckedContinuation<MrzData, Error>?
    private var timeoutTask: Task<Void, Never>?
    private weak var presentedController: UIViewController?

    private init(config: CCCDConfig) {
        self.config = config
    }

    static func scan(config: CCCDConfig) async throws -> MrzData {
        let controller = VisionMrzFlowController(config: config)
        return try await controller.start()
    }

    private func start() async throws -> MrzData {
        guard let presenter = Self.topViewController() else {
            throw CCCDError.cameraNotAvailable
        }

        let cameraStatus = AVCaptureDevice.authorizationStatus(for: .video)
        if cameraStatus == .denied || cameraStatus == .restricted {
            throw CCCDError.cameraNotAvailable
        }

        if cameraStatus == .notDetermined {
            let granted = await Self.requestCameraAccess()
            if !granted {
                throw CCCDError.cancelled
            }
        }

        return try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<MrzData, Error>) in
            self.continuation = continuation

            let scannerVC = MrzScannerViewController(config: config)
            scannerVC.onResult = { [weak self] result in
                guard let self else { return }
                self.finish(result)
            }

            scannerVC.modalPresentationStyle = .fullScreen
            presenter.present(scannerVC, animated: true)
            presentedController = scannerVC

            if config.mrzTimeoutMs > 0 {
                timeoutTask = Task { [weak self] in
                    guard let self else { return }
                    let ns = UInt64(max(0, config.mrzTimeoutMs)) * 1_000_000
                    try? await Task.sleep(nanoseconds: ns)
                    await MainActor.run {
                        self.finish(.failure(CCCDError.timeout))
                    }
                }
            }
        }
    }

    private func finish(_ result: Result<MrzData, Error>) {
        guard let continuation else { return }
        self.continuation = nil
        timeoutTask?.cancel()
        timeoutTask = nil

        let dismissTarget = presentedController
        presentedController = nil

        if let dismissTarget, dismissTarget.presentingViewController != nil {
            dismissTarget.dismiss(animated: true)
        }

        switch result {
        case .success(let data):
            continuation.resume(returning: data)
        case .failure(let error):
            continuation.resume(throwing: error)
        }
    }

    private static func topViewController(
        base: UIViewController? = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })?
            .rootViewController
    ) -> UIViewController? {
        if let nav = base as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        }
        if let tab = base as? UITabBarController {
            return topViewController(base: tab.selectedViewController)
        }
        if let presented = base?.presentedViewController {
            return topViewController(base: presented)
        }
        return base
    }

    private static func requestCameraAccess() async -> Bool {
        await withCheckedContinuation { continuation in
            AVCaptureDevice.requestAccess(for: .video) { granted in
                continuation.resume(returning: granted)
            }
        }
    }
}

@MainActor
private final class MrzScannerViewController: UIViewController {
    var onResult: ((Result<MrzData, Error>) -> Void)?

    private let config: CCCDConfig
    private let session = AVCaptureSession()
    private let outputQueue = DispatchQueue(label: "vncccd.mrz.ocr")
    private let videoOutput = AVCaptureVideoDataOutput()
    private let previewLayer = AVCaptureVideoPreviewLayer()

    private var isCompleted = false
    private var isProcessingFrame = false
    private var lastMrzCandidate: String?
    private var consecutiveCount = 0

    private lazy var closeButton: UIButton = {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        button.setTitle("Dong", for: .normal)
        button.tintColor = .white
        button.backgroundColor = UIColor.black.withAlphaComponent(0.4)
        button.layer.cornerRadius = 8
        button.contentEdgeInsets = UIEdgeInsets(top: 8, left: 12, bottom: 8, right: 12)
        button.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        return button
    }()

    private lazy var hintLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "Dua 3 dong MRZ vao khung de quet"
        label.textColor = .white
        label.font = .systemFont(ofSize: 15, weight: .medium)
        label.textAlignment = .center
        label.numberOfLines = 2
        return label
    }()

    private lazy var guideView: UIView = {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.layer.borderColor = UIColor.systemGreen.cgColor
        view.layer.borderWidth = 2
        view.layer.cornerRadius = 12
        view.backgroundColor = .clear
        return view
    }()

    init(config: CCCDConfig) {
        self.config = config
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .black

        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.session = session
        view.layer.addSublayer(previewLayer)

        view.addSubview(guideView)
        view.addSubview(closeButton)
        view.addSubview(hintLabel)

        NSLayoutConstraint.activate([
            closeButton.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 12),
            closeButton.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),

            guideView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            guideView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
            guideView.heightAnchor.constraint(equalTo: view.heightAnchor, multiplier: 0.24),
            guideView.centerYAnchor.constraint(equalTo: view.centerYAnchor),

            hintLabel.topAnchor.constraint(equalTo: guideView.bottomAnchor, constant: 16),
            hintLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
            hintLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20)
        ])

        configureCaptureSession()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if !session.isRunning {
            outputQueue.async { [weak self] in
                self?.session.startRunning()
            }
        }
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopCaptureSession()
    }

    @objc
    private func closeTapped() {
        complete(with: .failure(CCCDError.cancelled))
    }

    private func configureCaptureSession() {
        session.beginConfiguration()
        session.sessionPreset = .high

        guard let camera = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
            session.commitConfiguration()
            complete(with: .failure(CCCDError.cameraNotAvailable))
            return
        }

        do {
            let input = try AVCaptureDeviceInput(device: camera)
            if session.canAddInput(input) {
                session.addInput(input)
            }
        } catch {
            session.commitConfiguration()
            complete(with: .failure(CCCDError.cameraNotAvailable))
            return
        }

        videoOutput.videoSettings = [
            kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
        ]
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: outputQueue)

        if session.canAddOutput(videoOutput) {
            session.addOutput(videoOutput)
        }

        if let connection = videoOutput.connection(with: .video), connection.isVideoOrientationSupported {
            connection.videoOrientation = .portrait
        }

        session.commitConfiguration()
    }

    private func stopCaptureSession() {
        if session.isRunning {
            outputQueue.async { [session] in
                session.stopRunning()
            }
        }
    }

    private func handleRecognizedText(_ fullText: String) {
        guard !isCompleted else { return }

        let lines = fullText
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { $0.count >= 25 }

        guard let mrzLines = findMrzLines(lines) else { return }

        let mrzString = mrzLines.joined(separator: "\n")
        if mrzString == lastMrzCandidate {
            consecutiveCount += 1
        } else {
            lastMrzCandidate = mrzString
            consecutiveCount = 1
        }

        if consecutiveCount >= max(1, config.mrzConsecutiveFrames) {
            if let mrzData = MrzParser.parse(mrzLines: mrzLines) {
                complete(with: .success(mrzData))
            } else {
                lastMrzCandidate = nil
                consecutiveCount = 0
            }
        }
    }

    private func findMrzLines(_ lines: [String]) -> [String]? {
        guard lines.count >= 3 else { return nil }

        for idx in 0..<(lines.count - 2) {
            let line1 = cleanAndValidateLine(lines[idx], lineNumber: 1)
            let line2 = cleanAndValidateLine(lines[idx + 1], lineNumber: 2)
            let line3 = cleanAndValidateLine(lines[idx + 2], lineNumber: 3)

            if let line1, let line2, let line3, isVietnamCCCD(line1) {
                return [line1, line2, line3]
            }
        }

        for line in lines {
            guard let l1 = cleanAndValidateLine(line, lineNumber: 1), isVietnamCCCD(l1) else { continue }
            for second in lines where second != line {
                guard let l2 = cleanAndValidateLine(second, lineNumber: 2) else { continue }
                for third in lines where third != line && third != second {
                    guard let l3 = cleanAndValidateLine(third, lineNumber: 3) else { continue }
                    return [l1, l2, l3]
                }
            }
        }

        return nil
    }

    private func cleanAndValidateLine(_ rawLine: String, lineNumber: Int) -> String? {
        let targetLength = 30
        let minLength = 25

        var cleaned = MrzParser.cleanOcrText(rawLine)
        cleaned = MrzParser.smartCleanMrzLine(cleaned, lineNumber: lineNumber)

        if cleaned.count > targetLength {
            cleaned = String(cleaned.prefix(targetLength))
        }
        if cleaned.count < minLength {
            return nil
        }
        if cleaned.count < targetLength {
            cleaned += String(repeating: "<", count: targetLength - cleaned.count)
        }

        switch lineNumber {
        case 1:
            return (cleaned.hasPrefix("I") || cleaned.hasPrefix("A") || cleaned.hasPrefix("C")) ? cleaned : nil
        case 2:
            let prefix = String(cleaned.prefix(6))
            let valid = prefix.allSatisfy { $0.isNumber || $0 == "<" }
            return valid ? cleaned : nil
        case 3:
            return cleaned
        default:
            return nil
        }
    }

    private func isVietnamCCCD(_ line1: String) -> Bool {
        guard line1.count >= 5 else { return false }
        let prefix5 = String(line1.prefix(5))
        let middle = String(line1.dropFirst(2).prefix(3))
        return ["I<VNM", "IDVNM", "I0VNM", "ICVNM"].contains(prefix5) || middle == "VNM"
    }

    private func complete(with result: Result<MrzData, Error>) {
        guard !isCompleted else { return }
        isCompleted = true
        stopCaptureSession()

        Task { @MainActor [weak self] in
            self?.onResult?(result)
            self?.onResult = nil
        }
    }
}

extension MrzScannerViewController: AVCaptureVideoDataOutputSampleBufferDelegate {
    nonisolated func captureOutput(
        _ output: AVCaptureOutput,
        didOutput sampleBuffer: CMSampleBuffer,
        from connection: AVCaptureConnection
    ) {
        Task { @MainActor [weak self] in
            guard let self else { return }
            guard !isCompleted, !isProcessingFrame else { return }
            isProcessingFrame = true

            defer { isProcessingFrame = false }

            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }

            let request = VNRecognizeTextRequest { [weak self] request, _ in
                guard let self else { return }
                let observations = (request.results as? [VNRecognizedTextObservation]) ?? []
                let recognized = observations.compactMap { $0.topCandidates(1).first?.string }
                self.handleRecognizedText(recognized.joined(separator: "\n"))
            }
            request.recognitionLanguages = ["en-US"]
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = false

            let handler = VNImageRequestHandler(
                cvPixelBuffer: pixelBuffer,
                orientation: .right,
                options: [:]
            )
            try? handler.perform([request])
        }
    }
}
#else
import Foundation

public struct VisionMrzScannerService: MrzScannerService {
    public init() {}

    public func scan(config: CCCDConfig) async throws -> MrzData {
        _ = config
        throw CCCDError.cameraNotAvailable
    }
}
#endif
