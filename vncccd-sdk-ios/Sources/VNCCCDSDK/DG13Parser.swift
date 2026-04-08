import Foundation

public enum DG13Parser {
    public static func parse(rawBytes: Data) -> PersonalInfo? {
        guard !rawBytes.isEmpty else { return nil }

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
