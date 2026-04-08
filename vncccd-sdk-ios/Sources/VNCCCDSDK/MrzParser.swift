import Foundation

public enum MrzParser {
    private static let td1LineLength = 30
    private static let td1NumLines = 3
    private static let filler: Character = "<"
    private static let weights = [7, 3, 1]

    public static func parse(mrzLines: [String]) -> MrzData? {
        guard mrzLines.count == td1NumLines else { return nil }

        let line1 = padOrTrim(mrzLines[0])
        let line2 = padOrTrim(mrzLines[1])
        let line3 = padOrTrim(mrzLines[2])

        guard line1.count == td1LineLength, line2.count == td1LineLength, line3.count == td1LineLength else {
            return nil
        }

        let documentType = substr(line1, 0, 2)
        guard documentType.hasPrefix("I") || documentType.hasPrefix("A") || documentType.hasPrefix("C") else {
            return nil
        }

        let documentNumber = substr(line1, 5, 9).replacingOccurrences(of: String(filler), with: "")
        let optionalData1 = substr(line1, 15, 15)

        let dateOfBirth = substr(line2, 0, 6)
        let dobCheckDigit = charToValue(charAt(line2, 6))
        let sex = substr(line2, 7, 1)
        let dateOfExpiry = substr(line2, 8, 6)
        let doeCheckDigit = charToValue(charAt(line2, 14))
        let nationality = substr(line2, 15, 3).replacingOccurrences(of: String(filler), with: "")
        let optionalData2 = substr(line2, 18, 11)

        guard computeCheckDigit(input: dateOfBirth) == dobCheckDigit else { return nil }
        guard computeCheckDigit(input: dateOfExpiry) == doeCheckDigit else { return nil }

        let nameParts = line3.components(separatedBy: "<<")
        let surname = nameParts.first?.replacingOccurrences(of: String(filler), with: " ").trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let givenNames: String
        if nameParts.count > 1 {
            givenNames = nameParts.dropFirst().joined(separator: " ").replacingOccurrences(of: String(filler), with: " ").trimmingCharacters(in: .whitespacesAndNewlines)
        } else {
            givenNames = ""
        }
        let fullName = "\(surname) \(givenNames)".trimmingCharacters(in: .whitespacesAndNewlines)

        return MrzData(
            documentNumber: documentNumber,
            dateOfBirth: dateOfBirth,
            dateOfExpiry: dateOfExpiry,
            gender: sex,
            nationality: nationality,
            fullNameMrz: fullName,
            rawMrz: "\(line1)\n\(line2)\n\(line3)",
            optionalData1: optionalData1,
            optionalData2: optionalData2
        )
    }

    public static func parseRaw(rawMrz: String) -> MrzData? {
        let cleaned = rawMrz.replacingOccurrences(of: " ", with: "").replacingOccurrences(of: "\r", with: "")
        let lines = cleaned.components(separatedBy: "\n").filter { !$0.isEmpty }
        if lines.count == td1NumLines {
            return parse(mrzLines: lines)
        }

        if cleaned.count == td1LineLength * td1NumLines {
            return parse(mrzLines: [
                substr(cleaned, 0, 30),
                substr(cleaned, 30, 30),
                substr(cleaned, 60, 30)
            ])
        }
        return nil
    }

    public static func computeCheckDigit(input: String) -> Int {
        var sum = 0
        for (index, char) in input.enumerated() {
            sum += charToValue(char) * weights[index % 3]
        }
        return sum % 10
    }

    public static func isMrzLine1(text: String) -> Bool {
        let cleaned = text.replacingOccurrences(of: " ", with: "").uppercased()
        return (cleaned.hasPrefix("I<VNM") || cleaned.hasPrefix("IDVNM") ||
                cleaned.hasPrefix("I0VNM") || cleaned.hasPrefix("ICVNM")) &&
        cleaned.count >= td1LineLength - 5
    }

    public static func cleanOcrText(_ text: String) -> String {
        text.uppercased()
            .replacingOccurrences(of: "«", with: "<<")
            .replacingOccurrences(of: "»", with: ">>")
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "O", with: "0")
            .replacingOccurrences(of: "{", with: "<")
            .replacingOccurrences(of: "}", with: ">")
            .replacingOccurrences(of: "[", with: "<")
            .replacingOccurrences(of: "]", with: ">")
            .replacingOccurrences(of: "(", with: "<")
            .replacingOccurrences(of: ")", with: ">")
    }

    public static func smartCleanMrzLine(_ line: String, lineNumber: Int) -> String {
        var chars = Array(line.uppercased())

        switch lineNumber {
        case 1:
            if chars.count >= 15 {
                for index in 5...13 where index < chars.count {
                    chars[index] = fixToDigit(chars[index])
                }
                chars[14] = fixToDigit(chars[14])
            }
        case 2:
            if chars.count >= 15 {
                for index in 0...6 where index < chars.count {
                    chars[index] = fixToDigit(chars[index])
                }
                for index in 8...14 where index < chars.count {
                    chars[index] = fixToDigit(chars[index])
                }
            }
        default:
            break
        }

        return String(chars)
    }

    private static func padOrTrim(_ value: String) -> String {
        if value.count == td1LineLength { return value }
        if value.count > td1LineLength { return substr(value, 0, td1LineLength) }
        return value + String(repeating: String(filler), count: td1LineLength - value.count)
    }

    private static func charToValue(_ char: Character) -> Int {
        if char == filler { return 0 }
        if let digit = char.wholeNumberValue { return digit }
        let scalar = String(char).unicodeScalars.first?.value ?? 0
        if scalar >= 65 && scalar <= 90 {
            return Int(scalar - 65 + 10)
        }
        if scalar >= 97 && scalar <= 122 {
            return Int(scalar - 97 + 10)
        }
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

    private static func substr(_ string: String, _ start: Int, _ length: Int) -> String {
        let startIndex = string.index(string.startIndex, offsetBy: min(max(start, 0), string.count))
        let endIndex = string.index(startIndex, offsetBy: min(length, string.distance(from: startIndex, to: string.endIndex)), limitedBy: string.endIndex) ?? string.endIndex
        return String(string[startIndex..<endIndex])
    }

    private static func charAt(_ string: String, _ index: Int) -> Character {
        guard index >= 0, index < string.count else { return filler }
        return string[string.index(string.startIndex, offsetBy: index)]
    }
}
