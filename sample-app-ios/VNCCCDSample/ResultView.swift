import SwiftUI
import VNCCCDSDK

struct ResultView: View {
    let cccdData: CCCDData
    @Environment(\.dismiss) private var dismiss
    @State private var showFullImage = false
    @State private var appearAnimation = false
    @State private var showShareSheet = false

    var body: some View {
        NavigationView {
            ZStack {
                AppTheme.background
                    .ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: AppTheme.spacingLG) {
                        // Success Banner
                        successBanner
                            .opacity(appearAnimation ? 1 : 0)
                            .offset(y: appearAnimation ? 0 : -20)

                        // Face Photo Card
                        if let base64 = cccdData.faceImageBase64,
                           let imageData = Data(base64Encoded: base64),
                           let uiImage = UIImage(data: imageData) {
                            facePhotoCard(uiImage)
                                .opacity(appearAnimation ? 1 : 0)
                                .scaleEffect(appearAnimation ? 1 : 0.9)
                        }

                        // Personal Info Section
                        if let info = cccdData.personalInfo {
                            personalInfoSection(info)
                                .opacity(appearAnimation ? 1 : 0)
                                .offset(y: appearAnimation ? 0 : 20)
                        }

                        // MRZ Info Section
                        mrzInfoSection
                            .opacity(appearAnimation ? 1 : 0)
                            .offset(y: appearAnimation ? 0 : 20)

                        // Technical Info
                        technicalInfoSection
                            .opacity(appearAnimation ? 1 : 0)
                            .offset(y: appearAnimation ? 0 : 20)

                        Spacer(minLength: AppTheme.spacing2XL)
                    }
                    .padding(.horizontal, AppTheme.spacingMD)
                    .padding(.top, AppTheme.spacingSM)
                }
            }
            .navigationTitle("Kết quả")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button {
                        dismiss()
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.left")
                            Text("Đóng")
                        }
                        .foregroundColor(AppTheme.primary)
                    }
                }

                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        showShareSheet = true
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                            .foregroundColor(AppTheme.textSecondary)
                    }
                    .sheet(isPresented: $showShareSheet) {
                        ShareSheet(items: [generateShareText()])
                    }
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
    private var successBanner: some View {
        HStack(spacing: AppTheme.spacingMD) {
            ZStack {
                Circle()
                    .fill(AppTheme.success.opacity(0.2))
                    .frame(width: 48, height: 48)

                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 24))
                    .foregroundColor(AppTheme.success)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text("Đọc thẻ thành công")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundColor(AppTheme.textPrimary)

                if let authResult = cccdData.isPassiveAuthSuccess {
                    Text(authResult ? "✓ Xác thực thụ động thành công" : "⚠ Xác thực thụ động thất bại")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(authResult ? AppTheme.success : AppTheme.warning)
                }
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

    // MARK: - Face Photo Card
    private func facePhotoCard(_ image: UIImage) -> some View {
        VStack(spacing: AppTheme.spacingMD) {
            HStack {
                Label("Ảnh chân dung", systemImage: "person.crop.rectangle")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)

                Spacer()
            }

            Button {
                showFullImage = true
            } label: {
                Image(uiImage: image)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(maxHeight: 200)
                    .clipShape(RoundedRectangle(cornerRadius: AppTheme.radiusMD))
                    .overlay(
                        RoundedRectangle(cornerRadius: AppTheme.radiusMD)
                            .stroke(AppTheme.border, lineWidth: 1)
                    )
                    .shadow(color: .black.opacity(0.3), radius: 8, y: 4)
            }
            .fullScreenCover(isPresented: $showFullImage) {
                ZStack {
                    Color.black.ignoresSafeArea()

                    Image(uiImage: image)
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                        .ignoresSafeArea()

                    VStack {
                        HStack {
                            Spacer()
                            Button {
                                showFullImage = false
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 30))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                            .padding()
                        }
                        Spacer()
                    }
                }
            }
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
    }

    // MARK: - Personal Info
    private func personalInfoSection(_ info: PersonalInfo) -> some View {
        VStack(spacing: 0) {
            sectionHeader(title: "Thông tin cá nhân", icon: "person.text.rectangle")

            VStack(spacing: 0) {
                infoRow(label: "Họ và tên", value: info.fullName, icon: "person.fill")
                infoDivider
                infoRow(label: "Số CCCD", value: info.idNumber, icon: "number")
                infoDivider
                infoRow(label: "Ngày sinh", value: info.dateOfBirth, icon: "calendar")
                infoDivider
                infoRow(label: "Giới tính", value: info.gender, icon: "figure.stand")
                infoDivider
                infoRow(label: "Quốc tịch", value: info.nationality, icon: "flag.fill")
                infoDivider
                infoRow(label: "Dân tộc", value: info.ethnicity, icon: "person.3.fill")
                infoDivider
                infoRow(label: "Tôn giáo", value: info.religion, icon: "building.columns.fill")
                infoDivider
                infoRow(label: "Quê quán", value: info.placeOfOrigin, icon: "mappin.circle.fill")
                infoDivider
                infoRow(label: "Nơi thường trú", value: info.placeOfResidence, icon: "house.fill")
                infoDivider
                infoRow(label: "ĐDCN", value: info.personalIdentification, icon: "fingerprint")
                infoDivider
                infoRow(label: "Ngày cấp", value: info.dateOfIssue, icon: "calendar.badge.plus")
                infoDivider
                infoRow(label: "Ngày hết hạn", value: info.dateOfExpiry, icon: "calendar.badge.exclamationmark")
                infoDivider
                infoRow(label: "Họ tên cha", value: info.fatherName, icon: "person.fill")
                infoDivider
                infoRow(label: "Họ tên mẹ", value: info.motherName, icon: "person.fill")
                infoDivider
                infoRow(label: "Số CMND cũ", value: info.oldIdNumber, icon: "doc.text.fill")
            }
            .padding(.horizontal, AppTheme.spacingMD)
        }
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.radiusLG))
    }

    // MARK: - MRZ Info
    private var mrzInfoSection: some View {
        VStack(spacing: 0) {
            sectionHeader(title: "Thông tin MRZ", icon: "barcode.viewfinder")

            VStack(spacing: 0) {
                infoRow(label: "Số document", value: cccdData.mrzData.fullDocumentNumber, icon: "number")
                infoDivider
                infoRow(label: "Ngày sinh", value: cccdData.mrzData.dateOfBirth, icon: "calendar")
                infoDivider
                infoRow(label: "Hết hạn", value: cccdData.mrzData.dateOfExpiry, icon: "calendar.badge.exclamationmark")
                infoDivider
                infoRow(label: "Giới tính", value: cccdData.mrzData.gender, icon: "figure.stand")
                infoDivider
                infoRow(label: "Quốc tịch", value: cccdData.mrzData.nationality, icon: "flag.fill")
                infoDivider
                infoRow(label: "Họ tên (MRZ)", value: cccdData.mrzData.fullNameMrz, icon: "person.fill")
            }
            .padding(.horizontal, AppTheme.spacingMD)
        }
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.radiusLG))
    }

    // MARK: - Technical Info
    private var technicalInfoSection: some View {
        VStack(spacing: 0) {
            sectionHeader(title: "Thông tin kỹ thuật", icon: "cpu")

            VStack(spacing: 0) {
                infoRow(label: "DG1", value: cccdData.rawDG1 != nil ? "\(cccdData.rawDG1!.count) bytes" : nil, icon: "doc.fill")
                infoDivider
                infoRow(label: "DG2", value: cccdData.rawDG2 != nil ? "\(cccdData.rawDG2!.count) bytes" : nil, icon: "photo.fill")
                infoDivider
                infoRow(label: "DG13", value: cccdData.rawDG13 != nil ? "\(cccdData.rawDG13!.count) bytes" : nil, icon: "doc.text.fill")
                infoDivider
                infoRow(
                    label: "Passive Auth",
                    value: cccdData.isPassiveAuthSuccess.map { $0 ? "Thành công" : "Thất bại" },
                    icon: "lock.shield.fill"
                )
            }
            .padding(.horizontal, AppTheme.spacingMD)
        }
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
        .clipShape(RoundedRectangle(cornerRadius: AppTheme.radiusLG))
    }

    // MARK: - Shared Components
    private func sectionHeader(title: String, icon: String) -> some View {
        HStack(spacing: AppTheme.spacingSM) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(AppTheme.primary)

            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundColor(AppTheme.textSecondary)
                .textCase(.uppercase)


            Spacer()
        }
        .padding(.horizontal, AppTheme.spacingMD)
        .padding(.vertical, AppTheme.spacingSM)
        .background(AppTheme.surfaceLight.opacity(0.3))
    }

    private func infoRow(label: String, value: String?, icon: String) -> some View {
        HStack(spacing: AppTheme.spacingSM) {
            Image(systemName: icon)
                .font(.system(size: 12))
                .foregroundColor(AppTheme.textTertiary)
                .frame(width: 20)

            Text(label)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(AppTheme.textTertiary)
                .frame(width: 100, alignment: .leading)

            Text(value ?? "—")
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(value != nil ? AppTheme.textPrimary : AppTheme.textTertiary)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, AppTheme.spacingSM)
    }

    private var infoDivider: some View {
        Divider()
            .background(AppTheme.border.opacity(0.3))
    }

    // MARK: - Share
    private func generateShareText() -> String {
        var text = "VNCCCD SDK - Kết quả đọc thẻ\n"
        text += "========================\n\n"

        if let info = cccdData.personalInfo {
            text += "Họ tên: \(info.fullName ?? "—")\n"
            text += "Số CCCD: \(info.idNumber ?? "—")\n"
            text += "Ngày sinh: \(info.dateOfBirth ?? "—")\n"
            text += "Giới tính: \(info.gender ?? "—")\n"
            text += "Quốc tịch: \(info.nationality ?? "—")\n"
            text += "Quê quán: \(info.placeOfOrigin ?? "—")\n"
            text += "Nơi thường trú: \(info.placeOfResidence ?? "—")\n"
        }

        text += "\nMRZ: \(cccdData.mrzData.fullDocumentNumber)\n"

        return text
    }
}

#Preview {
    ResultView(
        cccdData: CCCDData(
            mrzData: MrzData(
                documentNumber: "001099123456",
                dateOfBirth: "900115",
                dateOfExpiry: "301115",
                gender: "M",
                nationality: "VNM",
                fullNameMrz: "NGUYEN VAN A"
            ),
            personalInfo: PersonalInfo(
                fullName: "Nguyễn Văn A",
                idNumber: "001099123456",
                dateOfBirth: "15/01/1990",
                gender: "Nam",
                nationality: "Việt Nam",
                ethnicity: "Kinh",
                religion: "Không",
                placeOfOrigin: "Hà Nội",
                placeOfResidence: "123 Đường ABC, Quận 1, TP.HCM"
            )
        )
    )
}
