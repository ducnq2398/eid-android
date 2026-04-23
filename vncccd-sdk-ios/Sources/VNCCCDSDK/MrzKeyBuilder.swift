import Foundation

enum MrzKeyBuilder {

    // MARK: - Public

    static func build(from mrzData: MrzData) -> String {
        // Ưu tiên raw MRZ (đúng chuẩn nhất)
        if let rawKey = buildFromRawMrz(mrzData.rawMrz) {
            return rawKey
        }

        // Fallback từ parsed data
        let doc = normalize(mrzData.documentNumber, length: 9)
        let dob = normalize(mrzData.dateOfBirth, length: 6)
        let doe = normalize(mrzData.dateOfExpiry, length: 6)

        let docCd = MrzParser.computeCheckDigit(doc)
        let dobCd = MrzParser.computeCheckDigit(dob)
        let doeCd = MrzParser.computeCheckDigit(doe)

        return "\(doc)\(docCd)\(dob)\(dobCd)\(doe)\(doeCd)"
    }

    // MARK: - Raw MRZ (BEST PATH)

    private static func buildFromRawMrz(_ rawMrz: String) -> String? {
        guard let (l1, l2, _) = normalizedTd1Lines(from: rawMrz) else { return nil }

        let doc = substr(l1, 5, 9)
        let dob = substr(l2, 0, 6)
        let doe = substr(l2, 8, 6)

        let docCd = safeCheckDigit(from: charAt(l1, 14), fallback: doc)
        let dobCd = safeCheckDigit(from: charAt(l2, 6), fallback: dob)
        let doeCd = safeCheckDigit(from: charAt(l2, 14), fallback: doe)

        return "\(doc)\(docCd)\(dob)\(dobCd)\(doe)\(doeCd)"
    }

    // MARK: - Helpers

    /// Ưu tiên digit từ MRZ, nếu lỗi thì compute lại
    private static func safeCheckDigit(from char: Character, fallback input: String) -> Int {
        if let digit = char.wholeNumberValue {
            return digit
        }
        return MrzParser.computeCheckDigit(input)
    }

    /// Normalize input (uppercase + remove space + pad TD1)
    private static func normalize(_ value: String, length: Int) -> String {
        let cleaned = value
            .uppercased()
            .replacingOccurrences(of: " ", with: "")

        return padOrTrim(cleaned, to: length)
    }

    // MARK: - MRZ Parsing

    private static func normalizedTd1Lines(from rawMrz: String) -> (String, String, String)? {
        let cleaned = rawMrz
            .uppercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "\r", with: "")

        let lines = cleaned
            .components(separatedBy: "\n")
            .filter { !$0.isEmpty }

        // Case 1: chuẩn 3 dòng
        if lines.count == 3 {
            return (
                padOrTrim(lines[0], to: 30),
                padOrTrim(lines[1], to: 30),
                padOrTrim(lines[2], to: 30)
            )
        }

        // Case 2: flat string 90 chars
        if cleaned.count == 90 {
            return (
                substr(cleaned, 0, 30),
                substr(cleaned, 30, 30),
                substr(cleaned, 60, 30)
            )
        }

        return nil
    }

    // MARK: - String Utils

    private static func padOrTrim(_ s: String, to length: Int) -> String {
        if s.count == length { return s }
        if s.count > length { return String(s.prefix(length)) }
        return s + String(repeating: "<", count: length - s.count)
    }

    private static func substr(_ string: String, _ start: Int, _ length: Int) -> String {
        guard !string.isEmpty else { return "" }

        let safeStart = min(max(start, 0), string.count)
        let startIndex = string.index(string.startIndex, offsetBy: safeStart)

        let maxLength = string.distance(from: startIndex, to: string.endIndex)
        let safeLength = min(length, maxLength)

        let endIndex = string.index(startIndex, offsetBy: safeLength)
        return String(string[startIndex..<endIndex])
    }

    private static func charAt(_ string: String, _ index: Int) -> Character {
        guard index >= 0, index < string.count else { return "<" }
        return string[string.index(string.startIndex, offsetBy: index)]
    }
}
