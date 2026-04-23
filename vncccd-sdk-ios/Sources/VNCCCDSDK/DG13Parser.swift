import Foundation

public enum DG13Parser {
    public static func parse(rawBytes: Data) -> PersonalInfo? {
        guard !rawBytes.isEmpty else { return nil }

        if let info = parseVietnamSegmented(rawBytes: rawBytes), hasData(info) {
            return info
        }
        if let info = parseTextBased(rawBytes: rawBytes), hasData(info) {
            return info
        }
        if let info = parseFallback(rawBytes: rawBytes), hasData(info) {
            return info
        }
        return nil
    }

    private static func parseTextBased(rawBytes: Data) -> PersonalInfo? {
        let text = decodeBestEffort(data: rawBytes)
        guard !text.isEmpty else { return nil }

        let lines = text
            .components(separatedBy: .newlines)
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }

        var values: [String: String] = [:]
        for line in lines {
            guard let separatorIndex = line.firstIndex(where: { $0 == ":" || $0 == "-" }) else { continue }
            let key = normalizeKey(String(line[..<separatorIndex]))
            let value = String(line[line.index(after: separatorIndex)...]).trimmingCharacters(in: .whitespacesAndNewlines)
            if !key.isEmpty, !value.isEmpty {
                values[key] = value
            }
        }
        guard !values.isEmpty else { return nil }

        func pick(_ keys: [String]) -> String? {
            for key in keys {
                if let value = values[key], !value.isEmpty {
                    return value
                }
            }
            return nil
        }

        return PersonalInfo(
            fullName: normalizeName(pick(["ho_ten", "ho_va_ten", "full_name"])),
            idNumber: normalizeId(pick(["so_cccd", "so_dinh_danh", "id_number"])),
            dateOfBirth: normalizeDate(pick(["ngay_sinh", "dob", "date_of_birth"])),
            gender: normalizeGender(pick(["gioi_tinh", "gender", "sex"])),
            nationality: normalizeShortText(pick(["quoc_tich", "nationality"]), maxLen: 32),
            ethnicity: normalizeShortText(pick(["dan_toc", "ethnicity"]), maxLen: 64),
            religion: normalizeShortText(pick(["ton_giao", "religion"]), maxLen: 64),
            placeOfOrigin: normalizeShortText(pick(["que_quan", "place_of_origin"]), maxLen: 200),
            placeOfResidence: normalizeShortText(pick(["noi_thuong_tru", "thuong_tru", "dia_chi", "place_of_residence"]), maxLen: 200),
            personalIdentification: normalizeShortText(pick(["dac_diem_nhan_dang", "personal_identification"]), maxLen: 200),
            dateOfIssue: normalizeDate(pick(["ngay_cap", "date_of_issue"])),
            dateOfExpiry: normalizeDate(pick(["ngay_het_han", "gia_tri_den", "date_of_expiry"])),
            fatherName: normalizeName(pick(["ho_ten_cha", "father_name"])),
            motherName: normalizeName(pick(["ho_ten_me", "mother_name"])),
            spouseName: normalizeName(pick(["ho_ten_vo_chong", "spouse_name"])),
            oldIdNumber: normalizeId(pick(["so_cmnd_cu", "old_id_number"]))
        )
    }

    private static func parseFallback(rawBytes: Data) -> PersonalInfo? {
        let text = decodeBestEffort(data: rawBytes)
        guard !text.isEmpty else { return nil }

        let idRegex = try? NSRegularExpression(pattern: "\\d{12}")
        let dateRegex = try? NSRegularExpression(pattern: "(\\d{2}[/.-]\\d{2}[/.-]\\d{4}|\\d{8})")

        let idValue = firstMatch(regex: idRegex, in: text)
        let dates = allMatches(regex: dateRegex, in: text)

        return PersonalInfo(
            idNumber: normalizeId(idValue),
            dateOfBirth: normalizeDate(dates.count > 0 ? dates[0] : nil),
            dateOfIssue: normalizeDate(dates.count > 1 ? dates[1] : nil),
            dateOfExpiry: normalizeDate(dates.count > 2 ? dates[2] : nil)
        )
    }

    private static func parseVietnamSegmented(rawBytes: Data) -> PersonalInfo? {
        let bytes = [UInt8](rawBytes)
        guard bytes.count >= 8 else { return nil }

        var separators: [Int] = []
        var expectedIndex: UInt8 = 1
        var position = 0
        while position <= bytes.count - 5 {
            let b0 = bytes[position]
            let b2 = bytes[position + 2]
            let b3 = bytes[position + 3]
            let b4 = bytes[position + 4]
            if b0 == 0x30, b2 == 0x02, b3 == 0x01, b4 == expectedIndex {
                separators.append(position)
                expectedIndex = expectedIndex &+ 1
                if expectedIndex > 20 { break }
            }
            position += 1
        }

        guard !separators.isEmpty else { return nil }
        separators.append(bytes.count)

        var idNumber: String?
        var fullName: String?
        var dateOfBirth: String?
        var gender: String?
        var nationality: String?
        var ethnicity: String?
        var religion: String?
        var placeOfOrigin: String?
        var placeOfResidence: String?
        var personalIdentification: String?
        var dateOfIssue: String?
        var dateOfExpiry: String?
        var fatherName: String?
        var motherName: String?
        var oldIdNumber: String?

        for index in 0..<(separators.count - 1) {
            let start = separators[index]
            let end = separators[index + 1]
            if end - start < 6 { continue }
            let subset = Array(bytes[start..<end])
            let fieldIndex = Int(subset[4])

            if fieldIndex == 14 { continue } // Card info is usually empty.

            if fieldIndex == 13 {
                let names = extractAsn1TextValues(from: subset)
                fatherName = normalizeName(names.first)
                motherName = normalizeName(names.count > 1 ? names[1] : nil)
                continue
            }

            let value = extractSegmentTextValue(from: subset)
            switch fieldIndex {
            case 1: idNumber = normalizeId(value)
            case 2: fullName = normalizeName(value)
            case 3: dateOfBirth = normalizeDate(value)
            case 4: gender = normalizeGender(value)
            case 5: nationality = normalizeShortText(value, maxLen: 32)
            case 6: ethnicity = normalizeShortText(value, maxLen: 64)
            case 7: religion = normalizeShortText(value, maxLen: 64)
            case 8: placeOfOrigin = normalizeShortText(value, maxLen: 200)
            case 9: placeOfResidence = normalizeShortText(value, maxLen: 200)
            case 10: personalIdentification = normalizeShortText(value, maxLen: 200)
            case 11: dateOfIssue = normalizeDate(value)
            case 12: dateOfExpiry = normalizeDate(value)
            case 15: oldIdNumber = normalizeId(value)
            default: break
            }
        }

        return PersonalInfo(
            fullName: fullName,
            idNumber: idNumber,
            dateOfBirth: dateOfBirth,
            gender: gender,
            nationality: nationality,
            ethnicity: ethnicity,
            religion: religion,
            placeOfOrigin: placeOfOrigin,
            placeOfResidence: placeOfResidence,
            personalIdentification: personalIdentification,
            dateOfIssue: dateOfIssue,
            dateOfExpiry: dateOfExpiry,
            fatherName: fatherName,
            motherName: motherName,
            oldIdNumber: oldIdNumber
        )
    }

    private static func decodeBestEffort(data: Data) -> String {
        let decoded = [
            String(data: data, encoding: .utf8),
            String(data: data, encoding: .windowsCP1252),
            String(data: data, encoding: .utf16LittleEndian),
            String(data: data, encoding: .utf16BigEndian),
            String(data: data, encoding: .isoLatin1)
        ].compactMap { $0 }

        return decoded
            .map(cleanupText(_:))
            .max(by: { scoreText($0) < scoreText($1) }) ?? ""
    }

    private static func normalizeKey(_ value: String) -> String {
        let lowered = value.lowercased()
        let folded = lowered.folding(options: [.diacriticInsensitive, .widthInsensitive, .caseInsensitive], locale: Locale(identifier: "vi_VN"))
            .replacingOccurrences(of: "đ", with: "d")
        let components = folded.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        return components.joined(separator: "_")
    }

    private static func normalizeId(_ value: String?) -> String? {
        guard let value else { return nil }
        let digits = value.filter(\.isNumber)
        return digits.count >= 9 ? digits : nil
    }

    private static func normalizeDate(_ value: String?) -> String? {
        guard let raw = value?.trimmingCharacters(in: .whitespacesAndNewlines), !raw.isEmpty else { return nil }

        if raw.range(of: #"^\d{2}[/.-]\d{2}[/.-]\d{4}$"#, options: .regularExpression) != nil {
            return raw.replacingOccurrences(of: ".", with: "/").replacingOccurrences(of: "-", with: "/")
        }

        if raw.range(of: #"^\d{8}$"#, options: .regularExpression) != nil {
            let yyyy = Int(String(raw.prefix(4))) ?? 0
            if (1900...2200).contains(yyyy) {
                return "\(raw.suffix(2))/\(raw.dropFirst(4).prefix(2))/\(raw.prefix(4))"
            }
            return "\(raw.prefix(2))/\(raw.dropFirst(2).prefix(2))/\(raw.suffix(4))"
        }

        return raw
    }

    private static func normalizeGender(_ value: String?) -> String? {
        guard let value = value?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        let upper = value.uppercased()
        if upper == "M" || upper.contains("NAM") || upper == "MALE" { return "Nam" }
        if upper == "F" || upper.contains("NU") || upper == "FEMALE" { return "Nu" }
        return value
    }

    private static func normalizeName(_ value: String?) -> String? {
        guard let value = normalizeShortText(value, maxLen: 120) else { return nil }
        return value.contains(where: \.isLetter) ? value : nil
    }

    private static func normalizeShortText(_ value: String?, maxLen: Int) -> String? {
        let cleaned = cleanupText(value ?? "")
        guard !cleaned.isEmpty, cleaned.count <= maxLen else { return nil }
        return cleaned
    }

    private static func cleanupText(_ value: String) -> String {
        value
            .replacingOccurrences(of: "\u{0000}", with: " ")
            .components(separatedBy: .controlCharacters)
            .joined(separator: " ")
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func scoreText(_ value: String) -> Int {
        guard !value.isEmpty else { return Int.min / 2 }
        let base = value.filter { $0.isLetter || $0.isNumber || " /.-|:;,".contains($0) }.count
        return base
    }

    private static func extractSegmentTextValue(from segment: [UInt8]) -> String? {
        let values = extractAsn1TextValues(from: segment)
        if let first = values.first {
            return first
        }

        guard segment.count > 5 else { return nil }
        let fallback = String(decoding: segment.suffix(from: 5), as: UTF8.self)
        return cleanupText(fallback).isEmpty ? nil : cleanupText(fallback)
    }

    private static func extractAsn1TextValues(from bytes: [UInt8]) -> [String] {
        guard !bytes.isEmpty else { return [] }

        var results: [String] = []
        var index = 0
        while index < bytes.count {
            let tag = bytes[index]
            if (tag == 0x0C || tag == 0x13), index + 1 < bytes.count,
               let (length, lengthOffset) = readAsn1Length(from: bytes, at: index + 1) {
                let valueStart = index + 1 + lengthOffset
                let valueEnd = valueStart + length
                if valueStart <= bytes.count, valueEnd <= bytes.count {
                    let text = cleanupText(String(decoding: bytes[valueStart..<valueEnd], as: UTF8.self))
                    if !text.isEmpty {
                        results.append(text)
                    }
                    index = valueEnd
                    continue
                }
            }

            // Best-effort skip for other TLV nodes.
            if index + 1 < bytes.count,
               let (length, lengthOffset) = readAsn1Length(from: bytes, at: index + 1) {
                let next = index + 1 + lengthOffset + length
                if next > index, next <= bytes.count {
                    index = next
                    continue
                }
            }

            index += 1
        }
        return results
    }

    private static func readAsn1Length(from bytes: [UInt8], at index: Int) -> (Int, Int)? {
        guard index < bytes.count else { return nil }

        let first = Int(bytes[index])
        if (first & 0x80) == 0 {
            return (first, 1)
        }

        let count = first & 0x7F
        guard count > 0, count <= 4, index + count < bytes.count else { return nil }

        var length = 0
        for position in 0..<count {
            length = (length << 8) | Int(bytes[index + 1 + position])
        }
        return (length, 1 + count)
    }

    private static func firstMatch(regex: NSRegularExpression?, in text: String) -> String? {
        guard let regex else { return nil }
        let range = NSRange(location: 0, length: text.utf16.count)
        guard let match = regex.firstMatch(in: text, options: [], range: range),
              let swiftRange = Range(match.range, in: text) else {
            return nil
        }
        return String(text[swiftRange])
    }

    private static func allMatches(regex: NSRegularExpression?, in text: String) -> [String] {
        guard let regex else { return [] }
        let range = NSRange(location: 0, length: text.utf16.count)
        return regex.matches(in: text, options: [], range: range).compactMap { match in
            guard let swiftRange = Range(match.range, in: text) else { return nil }
            return String(text[swiftRange])
        }
    }

    private static func hasData(_ info: PersonalInfo) -> Bool {
        !(info.fullName?.isEmpty ?? true) || !(info.idNumber?.isEmpty ?? true)
    }
}
