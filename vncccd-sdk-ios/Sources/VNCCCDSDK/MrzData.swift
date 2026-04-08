import Foundation

public struct MrzData: Sendable, Codable, Equatable {
    public let documentNumber: String
    public let dateOfBirth: String
    public let dateOfExpiry: String
    public let gender: String
    public let nationality: String
    public let fullNameMrz: String
    public let rawMrz: String
    public let optionalData1: String
    public let optionalData2: String

    public init(
        documentNumber: String,
        dateOfBirth: String,
        dateOfExpiry: String,
        gender: String = "",
        nationality: String = "VNM",
        fullNameMrz: String = "",
        rawMrz: String = "",
        optionalData1: String = "",
        optionalData2: String = ""
    ) {
        self.documentNumber = documentNumber
        self.dateOfBirth = dateOfBirth
        self.dateOfExpiry = dateOfExpiry
        self.gender = gender
        self.nationality = nationality
        self.fullNameMrz = fullNameMrz
        self.rawMrz = rawMrz
        self.optionalData1 = optionalData1
        self.optionalData2 = optionalData2
    }

    public var fullDocumentNumber: String {
        let primary = documentNumber.replacingOccurrences(of: "<", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !primary.isEmpty else { return "" }

        let extensionDigits = String(optionalData1.prefix { $0.isNumber })
        let merged: String
        if !extensionDigits.isEmpty {
            merged = primary + extensionDigits
        } else {
            let fallback = optionalData1.replacingOccurrences(of: "<", with: "").trimmingCharacters(in: .whitespacesAndNewlines)
            merged = fallback.isEmpty ? primary : (primary + fallback)
        }

        if merged.allSatisfy(\.isNumber), merged.count > 12 {
            return String(merged.prefix(12))
        }
        return merged
    }
}
