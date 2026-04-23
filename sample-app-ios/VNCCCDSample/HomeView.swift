import SwiftUI
import VNCCCDSDK

struct HomeView: View {
    @StateObject private var viewModel = ScanViewModel()
    @State private var animateGlow = false
    @State private var showAbout = false

    var body: some View {
        NavigationView {
            ZStack {
                // Background
                AppTheme.background
                    .ignoresSafeArea()

                // Subtle gradient orbs
                backgroundOrbs

                ScrollView(showsIndicators: false) {
                    VStack(spacing: AppTheme.spacingXL) {
                        // Header
                        headerSection

                        // NFC Status Card
                        nfcStatusCard

                        // Scan Buttons
                        scanButtonsSection

                        // Status Section
                        if !viewModel.scanStatus.isEmpty || viewModel.isScanning {
                            statusSection
                        }

                        // Error Alert
                        if let error = viewModel.errorMessage {
                            errorCard(error)
                        }

                        // Instructions
                        instructionsCard

                        Spacer(minLength: AppTheme.spacingXL)
                    }
                    .padding(.horizontal, AppTheme.spacingMD)
                    .padding(.top, AppTheme.spacingSM)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showAbout = true
                    } label: {
                        Image(systemName: "info.circle")
                            .foregroundColor(AppTheme.textSecondary)
                    }
                }
            }
            .sheet(isPresented: $showAbout) {
                AboutView()
            }
            .fullScreenCover(isPresented: $viewModel.showResult) {
                if let data = viewModel.cccdResult {
                    ResultView(cccdData: data)
                }
            }
            .sheet(isPresented: $viewModel.showMrzResult) {
                if let mrz = viewModel.mrzResult {
                    MrzResultView(mrzData: mrz)
                }
            }
            .onAppear {
                viewModel.checkNfcStatus()
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Background Orbs
    private var backgroundOrbs: some View {
        GeometryReader { geo in
            Circle()
                .fill(AppTheme.primary.opacity(0.08))
                .frame(width: 300, height: 300)
                .blur(radius: 80)
                .offset(x: -50, y: -100)

            Circle()
                .fill(AppTheme.secondary.opacity(0.06))
                .frame(width: 250, height: 250)
                .blur(radius: 60)
                .offset(x: geo.size.width - 100, y: geo.size.height * 0.4)
        }
        .ignoresSafeArea()
    }

    // MARK: - Header
    private var headerSection: some View {
        VStack(spacing: AppTheme.spacingSM) {
            // App Icon
            ZStack {
                Circle()
                    .fill(AppTheme.primaryGradient)
                    .frame(width: 72, height: 72)
                    .shadow(color: AppTheme.primary.opacity(0.4), radius: animateGlow ? 20 : 10)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 2).repeatForever(autoreverses: true)) {
                            animateGlow = true
                        }
                    }

                Image(systemName: "creditcard.and.123")
                    .font(.system(size: 28, weight: .semibold))
                    .foregroundColor(.white)
            }

            Text("VNCCCD SDK")
                .font(.system(size: 28, weight: .bold, design: .rounded))
                .foregroundColor(AppTheme.textPrimary)

            Text("Đọc thẻ Căn cước công dân gắn chip")
                .font(.system(size: 15, weight: .medium))
                .foregroundColor(AppTheme.textSecondary)
                .multilineTextAlignment(.center)
        }
        .padding(.top, AppTheme.spacingLG)
    }

    // MARK: - NFC Status Card
    private var nfcStatusCard: some View {
        HStack(spacing: AppTheme.spacingMD) {
            nfcStatusIcon

            VStack(alignment: .leading, spacing: 2) {
                Text("Trạng thái NFC")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(AppTheme.textTertiary)
                    .textCase(.uppercase)


                Text(nfcStatusText)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(nfcStatusColor)
            }

            Spacer()
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                .fill(AppTheme.surface.opacity(0.6))
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                        .stroke(nfcStatusColor.opacity(0.3), lineWidth: 1)
                )
        )
    }

    private var nfcStatusIcon: some View {
        ZStack {
            Circle()
                .fill(nfcStatusColor.opacity(0.15))
                .frame(width: 40, height: 40)

            Image(systemName: nfcStatusIconName)
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(nfcStatusColor)
        }
    }

    private var nfcStatusText: String {
        switch viewModel.nfcStatus {
        case .checking: return "Đang kiểm tra..."
        case .ready: return "NFC đã sẵn sàng"
        case .disabled: return "NFC chưa được bật"
        case .notSupported: return "Thiết bị không hỗ trợ NFC"
        }
    }

    private var nfcStatusColor: Color {
        switch viewModel.nfcStatus {
        case .checking: return AppTheme.textSecondary
        case .ready: return AppTheme.nfcGreen
        case .disabled: return AppTheme.nfcOrange
        case .notSupported: return AppTheme.nfcRed
        }
    }

    private var nfcStatusIconName: String {
        switch viewModel.nfcStatus {
        case .checking: return "antenna.radiowaves.left.and.right"
        case .ready: return "checkmark.circle.fill"
        case .disabled: return "exclamationmark.triangle.fill"
        case .notSupported: return "xmark.circle.fill"
        }
    }

    // MARK: - Scan Buttons
    private var scanButtonsSection: some View {
        VStack(spacing: AppTheme.spacingMD) {
            // Full Scan Button
            Button {
                viewModel.startFullScan()
            } label: {
                HStack(spacing: AppTheme.spacingMD) {
                    ZStack {
                        Circle()
                            .fill(.white.opacity(0.2))
                            .frame(width: 44, height: 44)

                        Image(systemName: "wave.3.right.circle.fill")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Quét đầy đủ")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(.white)

                        Text("MRZ + NFC • Đọc toàn bộ thông tin")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(.white.opacity(0.7))
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(.white.opacity(0.5))
                }
                .padding(AppTheme.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                        .fill(AppTheme.primaryGradient)
                        .shadow(color: AppTheme.primary.opacity(0.4), radius: 12, y: 4)
                )
            }
            .disabled(viewModel.isScanning)
            .opacity(viewModel.isScanning ? 0.6 : 1)

            // MRZ Only Button
            Button {
                viewModel.startMrzOnly()
            } label: {
                HStack(spacing: AppTheme.spacingMD) {
                    ZStack {
                        Circle()
                            .fill(AppTheme.primary.opacity(0.15))
                            .frame(width: 44, height: 44)

                        Image(systemName: "camera.viewfinder")
                            .font(.system(size: 20))
                            .foregroundColor(AppTheme.primary)
                    }

                    VStack(alignment: .leading, spacing: 2) {
                        Text("Chỉ quét MRZ")
                            .font(.system(size: 17, weight: .semibold))
                            .foregroundColor(AppTheme.textPrimary)

                        Text("Quét nhanh mã MRZ trên thẻ")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(AppTheme.textSecondary)
                    }

                    Spacer()

                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundColor(AppTheme.textTertiary)
                }
                .padding(AppTheme.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                        .fill(AppTheme.surface.opacity(0.6))
                        .overlay(
                            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                                .stroke(AppTheme.border, lineWidth: 1)
                        )
                )
            }
            .disabled(viewModel.isScanning)
            .opacity(viewModel.isScanning ? 0.6 : 1)
        }
    }

    // MARK: - Status Section
    private var statusSection: some View {
        VStack(spacing: AppTheme.spacingMD) {
            if viewModel.isScanning {
                HStack(spacing: AppTheme.spacingSM) {
                    ProgressView()
                        .tint(AppTheme.primary)

                    Text(viewModel.scanStatus)
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(AppTheme.textSecondary)

                    Spacer()
                }
                .padding(AppTheme.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                        .fill(AppTheme.surface.opacity(0.6))
                )
            }

            // Progress Steps
            if !viewModel.progressSteps.isEmpty {
                VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
                    ForEach(viewModel.progressSteps) { step in
                        HStack(spacing: AppTheme.spacingSM) {
                            ZStack {
                                Circle()
                                    .fill(stepColor(step).opacity(0.15))
                                    .frame(width: 24, height: 24)

                                if step.isCompleted {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(AppTheme.success)
                                } else if step.isActive {
                                    ProgressView()
                                        .scaleEffect(0.5)
                                        .tint(AppTheme.primary)
                                } else {
                                    Circle()
                                        .fill(AppTheme.textTertiary)
                                        .frame(width: 6, height: 6)
                                }
                            }

                            Text(step.status.description)
                                .font(.system(size: 13, weight: step.isActive ? .semibold : .regular))
                                .foregroundColor(step.isActive ? AppTheme.textPrimary : AppTheme.textTertiary)

                            Spacer()
                        }
                    }
                }
                .padding(AppTheme.spacingMD)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                        .fill(AppTheme.surface.opacity(0.4))
                )
            }
        }
    }

    private func stepColor(_ step: ScanViewModel.ProgressStep) -> Color {
        if step.isCompleted { return AppTheme.success }
        if step.isActive { return AppTheme.primary }
        return AppTheme.textTertiary
    }

    // MARK: - Error Card
    private func errorCard(_ message: String) -> some View {
        HStack(spacing: AppTheme.spacingMD) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 20))
                .foregroundColor(AppTheme.error)

            Text(message)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(AppTheme.error)

            Spacer()

            Button {
                withAnimation { viewModel.errorMessage = nil }
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(AppTheme.textTertiary)
            }
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                .fill(AppTheme.error.opacity(0.1))
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                        .stroke(AppTheme.error.opacity(0.3), lineWidth: 1)
                )
        )
        .transition(.opacity.combined(with: .move(edge: .top)))
    }

    // MARK: - Instructions Card
    private var instructionsCard: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingMD) {
            HStack(spacing: AppTheme.spacingSM) {
                Image(systemName: "lightbulb.fill")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.warning)

                Text("Hướng dẫn sử dụng")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(AppTheme.textPrimary)
            }

            VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
                instructionRow(number: "1", text: "Đưa mặt sau CCCD (có 3 dòng MRZ) vào khung camera")
                instructionRow(number: "2", text: "Sau khi quét MRZ xong, đặt thẻ lên mặt sau điện thoại")
                instructionRow(number: "3", text: "Giữ thẻ ổn định cho đến khi đọc xong NFC")
            }
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                .fill(AppTheme.surface.opacity(0.4))
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                        .stroke(AppTheme.border.opacity(0.5), lineWidth: 1)
                )
        )
    }

    private func instructionRow(number: String, text: String) -> some View {
        HStack(alignment: .top, spacing: AppTheme.spacingSM) {
            Text(number)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundColor(AppTheme.primary)
                .frame(width: 20, height: 20)
                .background(
                    Circle()
                        .fill(AppTheme.primary.opacity(0.15))
                )

            Text(text)
                .font(.system(size: 13, weight: .regular))
                .foregroundColor(AppTheme.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

#Preview {
    HomeView()
}
