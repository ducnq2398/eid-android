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

    // Scan line animation
    private var scanLineView: UIView!
    private var scanLineTopConstraint: NSLayoutConstraint!

    // MARK: - UI Elements

    private lazy var navBar: UIView = {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.backgroundColor = UIColor(white: 0.12, alpha: 0.95)
        return view
    }()

    private lazy var backButton: UIButton = {
        let button = UIButton(type: .system)
        button.translatesAutoresizingMaskIntoConstraints = false
        let config = UIImage.SymbolConfiguration(pointSize: 18, weight: .medium)
        button.setImage(UIImage(systemName: "chevron.left", withConfiguration: config), for: .normal)
        button.tintColor = .white
        button.addTarget(self, action: #selector(closeTapped), for: .touchUpInside)
        return button
    }()

    private lazy var titleLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "Đọc NFC"
        label.textColor = .white
        label.font = .systemFont(ofSize: 17, weight: .semibold)
        label.textAlignment = .center
        return label
    }()

    private lazy var instructionLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "Dùng camera để quét chuỗi ký tự mặt sau thẻ CCCD gần chip ở phía dưới"
        label.textColor = UIColor(white: 0.85, alpha: 1)
        label.font = .systemFont(ofSize: 14, weight: .regular)
        label.textAlignment = .center
        label.numberOfLines = 2
        return label
    }()

    private lazy var guideView: UIView = {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.backgroundColor = .clear
        return view
    }()

    private lazy var overlayView: UIView = {
        let view = UIView()
        view.translatesAutoresizingMaskIntoConstraints = false
        view.backgroundColor = .clear
        view.isUserInteractionEnabled = false
        return view
    }()

    private lazy var hintLabel: UILabel = {
        let label = UILabel()
        label.translatesAutoresizingMaskIntoConstraints = false
        label.text = "Vui lòng đặt chuỗi kí tự nằm vừa khung hình chữ nhật, chụp đủ ánh sáng và rõ nét."
        label.textColor = UIColor(white: 0.8, alpha: 1)
        label.font = .systemFont(ofSize: 13, weight: .regular)
        label.textAlignment = .center
        label.numberOfLines = 2
        return label
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

        // Camera preview
        previewLayer.videoGravity = .resizeAspectFill
        previewLayer.session = session
        view.layer.addSublayer(previewLayer)

        // Overlay + Guide
        view.addSubview(overlayView)
        view.addSubview(guideView)

        // Navigation bar
        view.addSubview(navBar)
        navBar.addSubview(backButton)
        navBar.addSubview(titleLabel)

        // Instruction
        view.addSubview(instructionLabel)

        // Hint
        view.addSubview(hintLabel)

        // Scan line
        scanLineView = UIView()
        scanLineView.translatesAutoresizingMaskIntoConstraints = false
        scanLineView.backgroundColor = .clear
        scanLineView.isUserInteractionEnabled = false
        guideView.addSubview(scanLineView)
        guideView.clipsToBounds = true

        setupConstraints()
        setupCornerBrackets()
        setupScanLineGradient()
        configureCaptureSession()
    }

    private func setupConstraints() {
        scanLineTopConstraint = scanLineView.topAnchor.constraint(equalTo: guideView.topAnchor, constant: 0)

        NSLayoutConstraint.activate([
            // Nav bar
            navBar.topAnchor.constraint(equalTo: view.topAnchor),
            navBar.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            navBar.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            navBar.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 44),

            backButton.leadingAnchor.constraint(equalTo: navBar.leadingAnchor, constant: 8),
            backButton.bottomAnchor.constraint(equalTo: navBar.bottomAnchor, constant: -8),
            backButton.widthAnchor.constraint(equalToConstant: 44),
            backButton.heightAnchor.constraint(equalToConstant: 32),

            titleLabel.centerXAnchor.constraint(equalTo: navBar.centerXAnchor),
            titleLabel.centerYAnchor.constraint(equalTo: backButton.centerYAnchor),

            // Instruction
            instructionLabel.topAnchor.constraint(equalTo: navBar.bottomAnchor, constant: 16),
            instructionLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
            instructionLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),

            // Guide (scan area)
            guideView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            guideView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
            guideView.heightAnchor.constraint(equalTo: guideView.widthAnchor, multiplier: 0.32),
            guideView.centerYAnchor.constraint(equalTo: view.centerYAnchor, constant: 20),

            // Overlay fills screen
            overlayView.topAnchor.constraint(equalTo: view.topAnchor),
            overlayView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            overlayView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayView.trailingAnchor.constraint(equalTo: view.trailingAnchor),

            // Scan line
            scanLineTopConstraint,
            scanLineView.leadingAnchor.constraint(equalTo: guideView.leadingAnchor),
            scanLineView.trailingAnchor.constraint(equalTo: guideView.trailingAnchor),
            scanLineView.heightAnchor.constraint(equalToConstant: 3),

            // Hint
            hintLabel.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -32),
            hintLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 24),
            hintLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -24),
        ])
    }

    private func setupCornerBrackets() {
        let cornerLength: CGFloat = 24
        let lineWidth: CGFloat = 3
        let color = UIColor(red: 1.0, green: 0.42, blue: 0.21, alpha: 1.0).cgColor // #FF6B35

        let corners: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
            (0, 0, 1, 1),     // top-left
            (1, 0, -1, 1),    // top-right
            (0, 1, 1, -1),    // bottom-left
            (1, 1, -1, -1),   // bottom-right
        ]

        for (xAnchor, yAnchor, xDir, yDir) in corners {
            let bracket = UIView()
            bracket.translatesAutoresizingMaskIntoConstraints = false
            bracket.backgroundColor = .clear
            guideView.addSubview(bracket)

            let w = cornerLength + lineWidth
            let h = cornerLength + lineWidth
            NSLayoutConstraint.activate([
                bracket.widthAnchor.constraint(equalToConstant: w),
                bracket.heightAnchor.constraint(equalToConstant: h),
                xAnchor == 0 ?
                    bracket.leadingAnchor.constraint(equalTo: guideView.leadingAnchor, constant: -lineWidth / 2) :
                    bracket.trailingAnchor.constraint(equalTo: guideView.trailingAnchor, constant: lineWidth / 2),
                yAnchor == 0 ?
                    bracket.topAnchor.constraint(equalTo: guideView.topAnchor, constant: -lineWidth / 2) :
                    bracket.bottomAnchor.constraint(equalTo: guideView.bottomAnchor, constant: lineWidth / 2),
            ])

            // Horizontal line
            let hLine = UIView()
            hLine.translatesAutoresizingMaskIntoConstraints = false
            hLine.backgroundColor = UIColor(cgColor: color)
            hLine.layer.cornerRadius = lineWidth / 2
            bracket.addSubview(hLine)

            // Vertical line
            let vLine = UIView()
            vLine.translatesAutoresizingMaskIntoConstraints = false
            vLine.backgroundColor = UIColor(cgColor: color)
            vLine.layer.cornerRadius = lineWidth / 2
            bracket.addSubview(vLine)

            NSLayoutConstraint.activate([
                hLine.heightAnchor.constraint(equalToConstant: lineWidth),
                hLine.widthAnchor.constraint(equalToConstant: cornerLength),
                vLine.widthAnchor.constraint(equalToConstant: lineWidth),
                vLine.heightAnchor.constraint(equalToConstant: cornerLength),
            ])

            if xDir > 0 { // left
                NSLayoutConstraint.activate([
                    hLine.leadingAnchor.constraint(equalTo: bracket.leadingAnchor),
                    vLine.leadingAnchor.constraint(equalTo: bracket.leadingAnchor),
                ])
            } else { // right
                NSLayoutConstraint.activate([
                    hLine.trailingAnchor.constraint(equalTo: bracket.trailingAnchor),
                    vLine.trailingAnchor.constraint(equalTo: bracket.trailingAnchor),
                ])
            }
            if yDir > 0 { // top
                NSLayoutConstraint.activate([
                    hLine.topAnchor.constraint(equalTo: bracket.topAnchor),
                    vLine.topAnchor.constraint(equalTo: bracket.topAnchor),
                ])
            } else { // bottom
                NSLayoutConstraint.activate([
                    hLine.bottomAnchor.constraint(equalTo: bracket.bottomAnchor),
                    vLine.bottomAnchor.constraint(equalTo: bracket.bottomAnchor),
                ])
            }
        }
    }

    private func setupScanLineGradient() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { [weak self] in
            guard let self else { return }
            let scanColor = UIColor(red: 1.0, green: 0.42, blue: 0.21, alpha: 1.0) // #FF6B35
            let gradient = CAGradientLayer()
            gradient.frame = CGRect(x: 0, y: 0, width: self.view.bounds.width, height: 3)
            gradient.colors = [
                scanColor.withAlphaComponent(0).cgColor,
                scanColor.withAlphaComponent(0.8).cgColor,
                scanColor.withAlphaComponent(0).cgColor,
            ]
            gradient.startPoint = CGPoint(x: 0, y: 0.5)
            gradient.endPoint = CGPoint(x: 1, y: 0.5)
            self.scanLineView.layer.sublayers?.forEach { $0.removeFromSuperlayer() }
            self.scanLineView.layer.addSublayer(gradient)
        }
    }

    private func startScanAnimation() {
        // Reset position
        scanLineTopConstraint.constant = 0
        view.layoutIfNeeded()

        let maxY = guideView.bounds.height - 3
        guard maxY > 0 else {
            // Layout not ready, retry after a short delay
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) { [weak self] in
                self?.startScanAnimation()
            }
            return
        }

        scanLineTopConstraint.constant = 0
        view.layoutIfNeeded()

        UIView.animate(
            withDuration: 2.0,
            delay: 0,
            options: [.repeat, .curveLinear],
            animations: { [weak self] in
                guard let self else { return }
                self.scanLineTopConstraint.constant = maxY
                self.view.layoutIfNeeded()
            }
        )
    }

    private func stopScanAnimation() {
        scanLineView.layer.removeAllAnimations()
        view.layer.removeAllAnimations()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer.frame = view.bounds
        updateOverlayMask()

        // Update scan line gradient width
        if let gradient = scanLineView.layer.sublayers?.first as? CAGradientLayer {
            gradient.frame = scanLineView.bounds
        }
    }

    private func updateOverlayMask() {
        let fullPath = UIBezierPath(rect: overlayView.bounds)
        let guideFrame = overlayView.convert(guideView.frame, from: guideView.superview)
        let cutoutPath = UIBezierPath(roundedRect: guideFrame, cornerRadius: 4)
        fullPath.append(cutoutPath)
        fullPath.usesEvenOddFillRule = true

        let maskLayer = CAShapeLayer()
        maskLayer.path = fullPath.cgPath
        maskLayer.fillRule = .evenOdd
        maskLayer.fillColor = UIColor.black.withAlphaComponent(0.5).cgColor

        overlayView.layer.sublayers?.removeAll(where: { $0 is CAShapeLayer })
        overlayView.layer.addSublayer(maskLayer)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        if !session.isRunning {
            outputQueue.async { [weak self] in
                self?.session.startRunning()
            }
        }
        startScanAnimation()
    }

    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        stopCaptureSession()
        stopScanAnimation()
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
