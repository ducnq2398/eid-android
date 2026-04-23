import Foundation

public enum MrzParser {

    // MARK: - Constants

    private static let lineLength = 30
    private static let numLines = 3
    private static let filler: Character = "<"
    private static let weights = [7, 3, 1]

    // MARK: - Public API

    public static func parse(mrzLines: [String]) -> MrzData? {
        guard mrzLines.count == numLines else { return nil }

        let l1 = normalize(mrzLines[0])
        let l2 = normalize(mrzLines[1])
        let l3 = normalize(mrzLines[2])

        guard l1.count == lineLength,
              l2.count == lineLength,
              l3.count == lineLength else {
            return nil
        }

        // MARK: Document type

        let documentType = slice(l1, 0, 2)
        guard documentType.first == "I" || documentType.first == "A" || documentType.first == "C" else {
            return nil
        }

        // MARK: Line 1

        let docNumberRaw = slice(l1, 5, 9)
        let docNumberCD = digit(l1, 14)
        let optional1 = slice(l1, 15, 15)

        guard check(docNumberRaw, docNumberCD) else { return nil }

        // MARK: Line 2

        let dob = slice(l2, 0, 6)
        let dobCD = digit(l2, 6)

        let sex = slice(l2, 7, 1)

        let expiry = slice(l2, 8, 6)
        let expiryCD = digit(l2, 14)

        let nationality = clean(slice(l2, 15, 3))
        let optional2 = slice(l2, 18, 11)

        let compositeCD = digit(l2, 29)

        guard check(dob, dobCD),
              check(expiry, expiryCD) else {
            return nil
        }

        // MARK: Composite check (ICAO)

        let compositeInput =
            docNumberRaw +
            slice(l1, 14, 1) +
            optional1 +
            dob +
            slice(l2, 6, 1) +
            expiry +
            slice(l2, 14, 1) +
            optional2

        guard computeCheckDigit(compositeInput) == compositeCD else {
            return nil
        }

        // MARK: Name

        let fullName = parseName(l3)

        return MrzData(
            documentNumber: clean(docNumberRaw),
            dateOfBirth: dob,
            dateOfExpiry: expiry,
            gender: normalizeGender(sex),
            nationality: nationality,
            fullNameMrz: fullName,
            rawMrz: "\(l1)\n\(l2)\n\(l3)",
            optionalData1: optional1,
            optionalData2: optional2
        )
    }

    public static func parseRaw(rawMrz: String) -> MrzData? {
        let cleaned = rawMrz
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "\r", with: "")

        let lines = cleaned
            .components(separatedBy: "\n")
            .filter { !$0.isEmpty }

        if lines.count == numLines {
            return parse(mrzLines: lines)
        }

        if cleaned.count == lineLength * numLines {
            return parse(mrzLines: [
                slice(cleaned, 0, 30),
                slice(cleaned, 30, 30),
                slice(cleaned, 60, 30)
            ])
        }

        return nil
    }

    // MARK: - OCR Helpers

    public static func cleanOcrText(_ text: String) -> String {
        text.uppercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "«", with: "<<")
            .replacingOccurrences(of: "{", with: "<")
            .replacingOccurrences(of: "[", with: "<")
            .replacingOccurrences(of: "(", with: "<")
    }

    public static func smartCleanMrzLine(_ line: String, lineNumber: Int) -> String {
        var chars = Array(line.uppercased())

        func fix(_ i: Int) {
            guard i < chars.count else { return }
            chars[i] = fixToDigit(chars[i])
        }

        switch lineNumber {
        case 1:
            (5...14).forEach { fix($0) }
        case 2:
            (0...6).forEach { fix($0) }
            (8...14).forEach { fix($0) }
            fix(29)
        default:
            break
        }

        return String(chars)
    }

    // MARK: - Core Logic

    public static func computeCheckDigit(_ input: String) -> Int {
        input.enumerated().reduce(0) { sum, pair in
            sum + value(pair.element) * weights[pair.offset % 3]
        } % 10
    }

    // MARK: - Helpers

    private static func check(_ value: String, _ digit: Int) -> Bool {
        computeCheckDigit(value) == digit
    }

    private static func parseName(_ line: String) -> String {
        let parts = line.components(separatedBy: "<<")

        let surname = clean(parts.first ?? "")

        let given = parts
            .dropFirst()
            .joined(separator: " ")
            .replacingOccurrences(of: "<", with: " ")
            .trimmingCharacters(in: .whitespaces)

        return "\(surname) \(given)".trimmingCharacters(in: .whitespaces)
    }

    private static func normalize(_ line: String) -> String {
        let uppercased = line.uppercased()
        if line.count > lineLength {
            return slice(uppercased, 0, lineLength)
        }
        if line.count < lineLength {
            return uppercased + String(repeating: String(filler), count: lineLength - line.count)
        }
        return uppercased
    }

    private static func clean(_ value: String) -> String {
        value.replacingOccurrences(of: String(filler), with: "")
    }

    private static func slice(_ str: String, _ start: Int, _ length: Int) -> String {
        guard start < str.count else { return "" }

        let s = str.index(str.startIndex, offsetBy: start)
        let e = str.index(s, offsetBy: min(length, str.distance(from: s, to: str.endIndex)), limitedBy: str.endIndex) ?? str.endIndex

        return String(str[s..<e])
    }

    private static func digit(_ str: String, _ index: Int) -> Int {
        guard index < str.count else { return 0 }
        return value(str[str.index(str.startIndex, offsetBy: index)])
    }

    private static func value(_ char: Character) -> Int {
        if char == filler { return 0 }
        if let d = char.wholeNumberValue { return d }

        let scalar = char.unicodeScalars.first!.value
        if scalar >= 65 && scalar <= 90 { return Int(scalar - 55) }

        return 0
    }

    private static func fixToDigit(_ char: Character) -> Character {
        switch char {
        case "O": return "0"
        case "I", "L": return "1"
        case "Z": return "2"
        case "S": return "5"
        case "B": return "8"
        default: return char
        }
    }

    private static func normalizeGender(_ value: String) -> String {
        if value == "M" || value == "F" || value == "X" {
            return value
        }
        return "X"
    }
}
