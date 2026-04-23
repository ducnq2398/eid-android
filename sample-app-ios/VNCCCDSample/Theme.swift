import SwiftUI

// MARK: - Design Tokens
enum AppTheme {
    // Colors
    static let primary = Color(hex: "2563EB")       // Vivid blue
    static let primaryDark = Color(hex: "1D4ED8")
    static let secondary = Color(hex: "7C3AED")     // Purple accent
    static let success = Color(hex: "10B981")
    static let warning = Color(hex: "F59E0B")
    static let error = Color(hex: "EF4444")
    static let background = Color(hex: "0F172A")     // Dark navy
    static let surface = Color(hex: "1E293B")
    static let surfaceLight = Color(hex: "334155")
    static let textPrimary = Color.white
    static let textSecondary = Color(hex: "94A3B8")
    static let textTertiary = Color(hex: "64748B")
    static let border = Color(hex: "334155")
    static let cardGradientStart = Color(hex: "1E3A5F")
    static let cardGradientEnd = Color(hex: "0F172A")
    static let nfcGreen = Color(hex: "34D399")
    static let nfcOrange = Color(hex: "FBBF24")
    static let nfcRed = Color(hex: "F87171")

    // Gradients
    static let primaryGradient = LinearGradient(
        colors: [primary, secondary],
        startPoint: .leading,
        endPoint: .trailing
    )

    static let cardGradient = LinearGradient(
        colors: [cardGradientStart, cardGradientEnd],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let surfaceGradient = LinearGradient(
        colors: [surface.opacity(0.8), surface.opacity(0.4)],
        startPoint: .top,
        endPoint: .bottom
    )

    // Spacing
    static let spacingXS: CGFloat = 4
    static let spacingSM: CGFloat = 8
    static let spacingMD: CGFloat = 16
    static let spacingLG: CGFloat = 24
    static let spacingXL: CGFloat = 32
    static let spacing2XL: CGFloat = 48

    // Radius
    static let radiusSM: CGFloat = 8
    static let radiusMD: CGFloat = 12
    static let radiusLG: CGFloat = 16
    static let radiusXL: CGFloat = 24
    static let radiusFull: CGFloat = 100
}

// MARK: - Hex Color Extension
extension Color {
    init(hex: String) {
        let scanner = Scanner(string: hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted))
        var hex: UInt64 = 0
        scanner.scanHexInt64(&hex)
        let r = Double((hex >> 16) & 0xFF) / 255.0
        let g = Double((hex >> 8) & 0xFF) / 255.0
        let b = Double(hex & 0xFF) / 255.0
        self.init(red: r, green: g, blue: b)
    }
}
