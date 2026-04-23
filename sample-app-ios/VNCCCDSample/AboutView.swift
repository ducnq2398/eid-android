import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            ZStack {
                AppTheme.background
                    .ignoresSafeArea()

                ScrollView(showsIndicators: false) {
                    VStack(spacing: AppTheme.spacingLG) {
                        // Icon + Title
                        VStack(spacing: AppTheme.spacingMD) {
                            ZStack {
                                Circle()
                                    .fill(AppTheme.primaryGradient)
                                    .frame(width: 80, height: 80)

                                Image(systemName: "creditcard.and.123")
                                    .font(.system(size: 32, weight: .semibold))
                                    .foregroundColor(.white)
                            }

                            Text("VNCCCD SDK")
                                .font(.system(size: 24, weight: .bold, design: .rounded))
                                .foregroundColor(AppTheme.textPrimary)

                            Text("Vietnamese Citizen Identity Card Reader")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(AppTheme.textSecondary)
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, AppTheme.spacingXL)

                        // Features
                        featuresList

                        // Requirements
                        requirementsCard

                        // Version Info
                        versionCard

                        Spacer(minLength: AppTheme.spacingLG)
                    }
                    .padding(.horizontal, AppTheme.spacingMD)
                }
            }
            .navigationTitle("Giới thiệu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Đóng") {
                        dismiss()
                    }
                    .foregroundColor(AppTheme.primary)
                }
            }
        }
        .preferredColorScheme(.dark)
    }

    private var featuresList: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingMD) {
            sectionTitle("Tính năng")

            featureItem(icon: "camera.viewfinder", title: "Quét MRZ", description: "Nhận diện MRZ bằng camera (AVFoundation + Vision)")
            featureItem(icon: "wave.3.right", title: "Đọc NFC", description: "Đọc chip CCCD qua NFC (NFCPassportReader)")
            featureItem(icon: "person.crop.rectangle", title: "Ảnh chân dung", description: "Lấy ảnh chân dung từ DG2")
            featureItem(icon: "doc.text.fill", title: "Thông tin cá nhân", description: "Parse DG13 để lấy thông tin đầy đủ")
            featureItem(icon: "lock.shield.fill", title: "Xác thực", description: "BAC/PACE + Passive Authentication")
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
    }

    private func featureItem(icon: String, title: String, description: String) -> some View {
        HStack(alignment: .top, spacing: AppTheme.spacingMD) {
            ZStack {
                Circle()
                    .fill(AppTheme.primary.opacity(0.15))
                    .frame(width: 36, height: 36)

                Image(systemName: icon)
                    .font(.system(size: 14))
                    .foregroundColor(AppTheme.primary)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundColor(AppTheme.textPrimary)

                Text(description)
                    .font(.system(size: 13, weight: .regular))
                    .foregroundColor(AppTheme.textSecondary)
            }

            Spacer()
        }
    }

    private var requirementsCard: some View {
        VStack(alignment: .leading, spacing: AppTheme.spacingMD) {
            sectionTitle("Yêu cầu Host App")

            VStack(alignment: .leading, spacing: AppTheme.spacingSM) {
                reqItem("Info.plist: NSCameraUsageDescription")
                reqItem("Info.plist: NFCReaderUsageDescription")
                reqItem("Entitlements: Near Field Communication Tag Reading")
                reqItem("iOS Deployment Target: 15.0+")
                reqItem("Thiết bị có NFC (iPhone 7+)")
            }
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
    }

    private func reqItem(_ text: String) -> some View {
        HStack(spacing: AppTheme.spacingSM) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 12))
                .foregroundColor(AppTheme.success)

            Text(text)
                .font(.system(size: 13, weight: .medium, design: .monospaced))
                .foregroundColor(AppTheme.textSecondary)
        }
    }

    private var versionCard: some View {
        VStack(spacing: AppTheme.spacingSM) {
            HStack {
                Text("SDK Version")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(AppTheme.textTertiary)
                Spacer()
                Text("1.0.0")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)
            }

            Divider().background(AppTheme.border.opacity(0.3))

            HStack {
                Text("Platform")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(AppTheme.textTertiary)
                Spacer()
                Text("iOS 15+")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)
            }

            Divider().background(AppTheme.border.opacity(0.3))

            HStack {
                Text("Swift")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(AppTheme.textTertiary)
                Spacer()
                Text("5.9+")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(AppTheme.textSecondary)
            }
        }
        .padding(AppTheme.spacingMD)
        .background(
            RoundedRectangle(cornerRadius: AppTheme.radiusLG)
                .fill(AppTheme.surface.opacity(0.5))
        )
    }

    private func sectionTitle(_ title: String) -> some View {
        HStack(spacing: AppTheme.spacingSM) {
            Rectangle()
                .fill(AppTheme.primaryGradient)
                .frame(width: 3, height: 16)
                .clipShape(Capsule())

            Text(title)
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(AppTheme.textPrimary)
        }
    }
}

#Preview {
    AboutView()
}
