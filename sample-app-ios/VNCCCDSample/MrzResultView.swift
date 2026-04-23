import SwiftUI
import VNCCCDSDK

struct MrzResultView: View {
    let mrzData: MrzData
    @Environment(\.dismiss) private var dismiss
    @State private var appearAnimation = false

    var body: some View {
        NavigationView {
            ZStack {
                AppTheme.background
                    .ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: AppTheme.spacingLG) {
                        // Header
                        mrzSuccessBanner
                            .opacity(appearAnimation ? 1 : 0)
                            .offset(y: appearAnimation ? 0 : -20)

                        // MRZ Data Card
                        mrzDataCard
                            .opacity(appearAnimation ? 1 : 0)
                            .offset(y: appearAnimation ? 0 : 20)

                        // Raw MRZ
                        if !mrzData.rawMrz.isEmpty {
                            rawMrzCard
                                .opacity(appearAnimation ? 1 : 0)
                                .offset(y: appearAnimation ? 0 : 20)
                        }

                        Spacer(minLength: AppTheme.spacing2XL)
                    }
                    .padding(.horizontal, AppTheme.spacingMD)
                    .padding(.top, AppTheme.spacingSM)
                }
            }
            .navigationTitle("Kết quả MRZ")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Đóng") {
                        dismiss()
                    }
                    .foregroundColor(AppTheme.primary)
                }
            }
            .onAppear {
                withAnimation(.spring(response: 0.6, dampingFraction: 0.8)) {
                    appearAnimation = true
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    // MARK: - Success Banner
    private var mrzSuccessBanner: some View {
        HStack(spacing: AppTheme.spacingMD) {
            ZStack {
                Circle()
                    .fill(AppTheme.success.opacity(0.2))
                    .frame(width: 48, height: 48)

                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 24))
                    .foregroundColor(AppTheme.success)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Quét MRZ thành công")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(AppTheme.textPrimary)

                Text("Số CCCD: \(mrzData.fullDocumentNumber)")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(AppTheme.textSecondary)
            }

            Spacer()
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                .fill(AppTheme.success.opacity(0.08))
                .overlay(
                    RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                        .stroke(AppTheme.success.opacity(0.2), lineWidth: 1)
                )
        )
    }

    // MARK: - MRZ Data Card
    private var mrzDataCard: some View {
        VStack(spacing: 0) {
            HStack(spacing: AppTheme.spacingSM) {
                Image(systemName: "barcode.viewfinder")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.primary)

                Text("THÔNG TIN MRZ")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)


                Spacer()
            }
            .padding(.horizontal, AppTheme.spacingMD)
            .padding(.vertical, AppTheme.spacingSM)
            .background(AppTheme.surfaceLight.opacity(0.3))

            VStack(spacing: 0) {
                dataRow(label: "Số CCCD", value: mrzData.fullDocumentNumber, icon: "number")
                divider
                dataRow(label: "Ngày sinh", value: formatMrzDate(mrzData.dateOfBirth), icon: "calendar")
                divider
                dataRow(label: "Hết hạn", value: formatMrzDate(mrzData.dateOfExpiry), icon: "calendar.badge.exclamationmark")
                divider
                dataRow(label: "Giới tính", value: genderDisplay(mrzData.gender), icon: "figure.stand")
                divider
                dataRow(label: "Quốc tịch", value: mrzData.nationality, icon: "flag.fill")
                divider
                dataRow(label: "Họ tên (MRZ)", value: mrzData.fullNameMrz.replacingOccurrences(of: "<", with: " ").trimmingCharacters(in: .whitespaces), icon: "person.fill")
            }
            .padding(.horizontal, AppTheme.spacingMD)
        }
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.radiusLG))
    }

    // MARK: - Raw MRZ Card
    private var rawMrzCard: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
            HStack(spacing: AppTheme.spacingSM) {
                Image(systemName: "doc.text.fill")
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.textTertiary)

                Text("MRZ gốc")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)

                Spacer()

                Button {
                    UIPasteboard.general.string = mrzData.rawMrz
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(AppTheme.primary)
                }
            }

            Text(mrzData.rawMrz)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundColor(AppTheme.nfcGreen)
                .padding(AppTheme.spacingSM)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(
                    RoundedRectangle(cornerRadius: AppTheme.radiusSM)
                        .fill(Color.black.opacity(0.3))
                )
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
    }

    // MARK: - Helpers
    private func dataRow(label: String, value: String, icon: String) -> some View {
        HStack(spacing: AppTheme.spacingSM) {
            Image(systemName: icon)
                .font(.system(size: 12))
                .foregroundColor(AppTheme.textTertiary)
                .frame(width: 20)

            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(AppTheme.textTertiary)
                .frame(width: 100, alignment: .leading)

            Text(value.isEmpty ? "—" : value)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(value.isEmpty ? AppTheme.textTertiary : AppTheme.textPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, AppTheme.spacingSM)
    }

    private var divider: some View {
        Divider()
            .background(AppTheme.border.opacity(0.3))
    }

    private func formatMrzDate(_ raw: String) -> String {
        let digits = raw.filter(\.isNumber)
        guard digits.count == 6 else { return raw }
        let yy = String(digits.prefix(2))
        let mm = String(digits.dropFirst(2).prefix(2))
        let dd = String(digits.suffix(2))
        let yyInt = Int(yy) ?? 0
        let yyyy = yyInt > 50 ? "19\(yy)" : "20\(yy)"
        return "\(dd)/\(mm)/\(yyyy)"
    }

    private func genderDisplay(_ raw: String) -> String {
        switch raw.uppercased() {
        case "M": return "Nam"
        case "F": return "Nữ"
        default: return raw
        }
    }
}

#Preview {
    MrzResultView(
        mrzData: MrzData(
            documentNumber: "001099123456",
            dateOfBirth: "900115",
            dateOfExpiry: "301115",
            gender: "M",
            nationality: "VNM",
            fullNameMrz: "NGUYEN<<VAN<<A",
            rawMrz: "I<VNM001099123456<<<<<<<<<<<\n9001154M3011150VNM<<<<<<<<<<<\nNGUYEN<<VAN<<A<<<<<<<<<<<<<<<<"
        )
    )
}
