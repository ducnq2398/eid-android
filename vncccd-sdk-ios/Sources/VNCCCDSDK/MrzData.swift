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
        if let rawBased = extractCccdFromRawMrz(rawMrz), rawBased.count == 12 {
            return rawBased
        }

        let primaryDigits = normalizeDigitLike(documentNumber)
        let overflowDigits = normalizeDigitLike(optionalData1)

        // Some VN CCCD MRZ variants store full 12-digit number in optionalData1.
        if overflowDigits.count >= 12 {
            return String(overflowDigits.prefix(12))
        }

        if primaryDigits.count >= 12 {
            return String(primaryDigits.prefix(12))
        }

        if primaryDigits.count == 9, overflowDigits.count >= 3 {
            return primaryDigits + String(overflowDigits.prefix(3))
        }

        let merged = primaryDigits + overflowDigits

        if merged.count >= 12 {
            return String(merged.prefix(12))
        }
        return merged
    }

    private func extractCccdFromRawMrz(_ raw: String) -> String? {
        let lines = raw
            .uppercased()
            .replacingOccurrences(of: "\r", with: "")
            .components(separatedBy: "\n")
            .filter { !$0.isEmpty }

        guard lines.count >= 1 else { return nil }
        let line1 = lines[0]
        guard line1.count >= 18 else { return nil }

        let optionalDigits = normalizeDigitLike(slice(line1, start: 15, length: 15))
        if optionalDigits.count >= 12 {
            return String(optionalDigits.prefix(12))
        }

        let doc9 = slice(line1, start: 5, length: 9)
        let overflow3 = slice(line1, start: 15, length: 3)
        let doc9Digits = normalizeDigitLike(doc9)
        let overflow3Digits = normalizeDigitLike(overflow3)

        if doc9Digits.count == 9, overflow3Digits.count >= 3 {
            return doc9Digits + String(overflow3Digits.prefix(3))
        }
        return normalizeDigitLike(doc9 + overflow3)
    }

    private func normalizeDigitLike(_ input: String) -> String {
        String(input.uppercased().compactMap { char -> Character? in
            if char.isNumber { return char }
            switch char {
            case "O", "Q", "D": return "0"
            case "I", "L": return "1"
            case "Z": return "2"
            case "S": return "5"
            case "G": return "6"
            case "T": return "7"
            case "B": return "8"
            default: return nil
            }
        })
    }

    private func slice(_ value: String, start: Int, length: Int) -> String {
        guard start < value.count else { return "" }

        let startIndex = value.index(value.startIndex, offsetBy: start)
        let endIndex = value.index(
            startIndex,
            offsetBy: min(length, value.distance(from: startIndex, to: value.endIndex)),
            limitedBy: value.endIndex
        ) ?? value.endIndex

        return String(value[startIndex..<endIndex])
    }
}
