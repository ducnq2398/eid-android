import Foundation

#if canImport(CoreNFC) && canImport(NFCPassportReader)
import CoreNFC
@_implementationOnly import NFCPassportReader
import UIKit

public final class CoreNfcCardReaderService: NSObject, NfcCardReaderService, @unchecked Sendable {
    private let reader: PassportReader

    public override init() {
        self.reader = PassportReader()
        super.init()
    }

    public func readCard(
        mrzData: MrzData,
        config: CCCDConfig,
        onProgress: @escaping @Sendable (ReadingStatus) -> Void
    ) async throws -> CCCDData {
        debugLog("Start NFC read")
        debugLog("Input MRZ number=\(mrzData.fullDocumentNumber), dob=\(mrzData.dateOfBirth), doe=\(mrzData.dateOfExpiry)")

        guard NFCNDEFReaderSession.readingAvailable else {
            debugLog("NFC is not available on this device")
            throw CCCDError.nfcNotSupported
        }

        

        let mrzKey = MrzKeyBuilder.build(from: mrzData)
        debugLog("Built MRZ key=\(masked(mrzKey))")

        debugLog("Progress -> \(ReadingStatus.connecting.description)")
        onProgress(.connecting)
        debugLog("Progress -> \(ReadingStatus.authenticating.description)")
        onProgress(.authenticating)

        let tags: [DataGroupId] = [.COM, .SOD, .DG1, .DG2, .DG11, .DG12, .DG13, .DG14, .DG15]
        debugLog("Requesting tags: \(tags.map { String(describing: $0) }.joined(separator: ", "))")

        let passportModel: Any
        do {
            passportModel = try await reader.readPassport(mrzKey: mrzKey, tags: tags)
            debugLog("NFC readPassport succeeded")
            debugLog("Available groups: \(ReflectionExtractor.availableGroupHints(passportModel).joined(separator: ", "))")
        } catch {
            let mapped = mapNfcError(error)
            debugLog("NFC readPassport failed: raw=\(error.localizedDescription), mapped=\(mapped.message)")
            throw mapped
        }

        debugLog("Progress -> \(ReadingStatus.readingDG1.description)")
        onProgress(.readingDG1)

        let rawDG1 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG1")
        debugLog("DG1 bytes=\(rawDG1?.count ?? 0)")

        var rawDG2: Data?
        var faceBase64: String?
        if config.readFaceImage {
            debugLog("Progress -> \(ReadingStatus.readingDG2.description)")
            onProgress(.readingDG2)
            rawDG2 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG2")
            faceBase64 = ReflectionExtractor.extractFaceImageBase64(passportModel)
            debugLog("DG2 bytes=\(rawDG2?.count ?? 0), faceBase64=\(faceBase64 != nil ? "yes" : "no")")
        }

        let rawDG13 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG13")
        let rawDG11 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG11")
        var personalInfo: PersonalInfo?
        if config.readPersonalInfo {
            debugLog("Progress -> \(ReadingStatus.readingDG13.description)")
            onProgress(.readingDG13)
            if let dg13 = rawDG13 {
                personalInfo = DG13Parser.parse(rawBytes: dg13)
                if personalInfo != nil {
                    debugLog("Personal info source=DG13")
                }
            }
            if personalInfo == nil, let dg11 = rawDG11 {
                personalInfo = DG13Parser.parse(rawBytes: dg11)
                if personalInfo != nil {
                    debugLog("DG13 unavailable/empty, personal info source=DG11")
                }
            }
            debugLog("DG13 bytes=\(rawDG13?.count ?? 0), DG11 bytes=\(rawDG11?.count ?? 0), personalInfo=\(personalInfo != nil ? "yes" : "no")")
        }

        debugLog("Progress -> \(ReadingStatus.verifying.description)")
        onProgress(.verifying)
        debugLog("Progress -> \(ReadingStatus.completed.description)")
        onProgress(.completed)

        let result = CCCDData(
            mrzData: mrzData,
            personalInfo: personalInfo,
            faceImageBase64: faceBase64,
            rawDG1: rawDG1,
            rawDG2: rawDG2,
            rawDG13: rawDG13,
            isPassiveAuthSuccess: ReflectionExtractor.extractPassiveAuthResult(passportModel)
        )
        debugLog("Finish NFC read: passiveAuth=\(result.isPassiveAuthSuccess.map(String.init(describing:)) ?? "nil")")
        return result
    }

    private func mapNfcError(_ error: Error) -> CCCDError {
        let text = String(describing: error).lowercased()
        if text.contains("cancel") {
            return .cancelled
        }
        if text.contains("nfc") && (text.contains("not available") || text.contains("unsupported")) {
            return .nfcNotSupported
        }
        if text.contains("authentication") || text.contains("bac") || text.contains("pace") || text.contains("mrz") {
            return .authenticationFailed(details: error.localizedDescription)
        }
        if text.contains("tag") || text.contains("connection") || text.contains("lost") || text.contains("transceive") {
            return .connectionLost(details: error.localizedDescription)
        }
        if text.contains("timeout") {
            return .timeout
        }
        return .unknown(details: error.localizedDescription)
    }

    private func debugLog(_ message: String) {
        #if DEBUG
        print("[VNCCCDSDK][NFC] \(message)")
        #endif
    }

    private func masked(_ value: String) -> String {
        guard value.count > 6 else { return value }
        let prefix = value.prefix(4)
        let suffix = value.suffix(2)
        return "\(prefix)****\(suffix)"
    }
}

private enum ReflectionExtractor {
    private static let maxLookupDepth = 24

    static func extractFaceImageBase64(_ model: Any) -> String? {
        if let image = lookup(model, candidates: ["passportImage", "faceImage", "portraitImage"]) as? UIImage,
           let data = image.jpegData(compressionQuality: 0.95) {
            return data.base64EncodedString()
        }

        if let data = toData(lookup(model, candidates: ["passportImageData", "faceImageData", "portraitImageData"])) {
            return data.base64EncodedString()
        }

        if let raw = extractDataGroupRaw(model, groupCode: "DG2") {
            return raw.base64EncodedString()
        }

        return nil
    }

    static func extractPassiveAuthResult(_ model: Any) -> Bool? {
        if let value = lookup(model, candidates: ["passiveAuthenticationPassed", "isPassiveAuthenticationPassed", "passiveAuthenticationSucceeded"]) as? Bool {
            return value
        }
        if let details = lookup(model, candidates: ["errors", "verificationErrors"]) {
            return mirrorChildren(details).isEmpty
        }
        return nil
    }

    static func extractPersonalInfo(_ model: Any) -> PersonalInfo? {
        let fullName = string(model, ["fullName", "name", "holderName"])
        let idNumber = string(model, ["documentNumber", "passportNumber", "personalNumber"])
        let dateOfBirth = normalizeDate(string(model, ["dateOfBirth", "birthDate", "dob"]))
        let dateOfExpiry = normalizeDate(string(model, ["dateOfExpiry", "expiryDate", "expirationDate"]))
        let gender = string(model, ["gender", "sex"])
        let nationality = string(model, ["nationality", "countryCode"])
        let placeOfBirth = string(model, ["placeOfBirth", "placeBirth"])
        let personalNumber = string(model, ["personalNumber", "optionalData"])

        let info = PersonalInfo(
            fullName: fullName,
            idNumber: idNumber,
            dateOfBirth: dateOfBirth,
            gender: gender,
            nationality: nationality,
            placeOfOrigin: placeOfBirth,
            personalIdentification: personalNumber,
            dateOfExpiry: dateOfExpiry
        )

        return hasPersonalData(info) ? info : nil
    }

    static func extractDataGroupRaw(_ model: Any, groupCode: String) -> Data? {
        if let direct = toData(lookup(
            model,
            candidates: [
                "\(groupCode.lowercased())Data",
                "\(groupCode)Data",
                groupCode.lowercased(),
                groupCode,
                "raw\(groupCode)"
            ]
        )) {
            return direct
        }

        if let groups = lookup(model, candidates: ["dataGroupsRead", "dataGroups", "dataGroupMap"]) {
            let tokens = groupTokens(for: groupCode)

            for child in mirrorChildren(groups) {
                guard let label = child.label else { continue }
                if matchesGroupToken(label, tokens: tokens), let data = toData(child.value) {
                    return data
                }
                if matchesGroupToken(label, tokens: tokens), let payload = extractPayload(from: child.value) {
                    return payload
                }

                let keyDescription = String(describing: child.value)
                if matchesGroupToken(keyDescription, tokens: tokens) {
                    if let data = toData(lookup(child.value, candidates: ["data", "body", "rawData", "value"])) {
                        return data
                    }
                    if let payload = extractPayload(from: child.value) {
                        return payload
                    }
                }
            }

            if let dict = groups as? [AnyHashable: Any] {
                for (key, value) in dict {
                    if matchesGroupToken(String(describing: key), tokens: tokens) {
                        if let data = toData(value) {
                            return data
                        }
                        if let data = toData(lookup(value, candidates: ["data", "body", "rawData", "value"])) {
                            return data
                        }
                        if let payload = extractPayload(from: value) {
                            return payload
                        }
                    }
                }
            }
        }

        return nil
    }

    static func availableGroupHints(_ model: Any) -> [String] {
        var hints = Set<String>()

        if let groups = lookup(model, candidates: ["dataGroupsRead", "dataGroups", "dataGroupMap"]) {
            if let dict = groups as? [AnyHashable: Any] {
                for key in dict.keys {
                    hints.insert(String(describing: key))
                }
            }

            for child in mirrorChildren(groups) {
                if let label = child.label, !label.isEmpty {
                    hints.insert(label)
                }
                let valueDescription = String(describing: child.value)
                if valueDescription.contains("DG") || valueDescription.contains("0x") || valueDescription.contains("DataGroup") {
                    hints.insert(valueDescription)
                }
            }
        }

        return hints.sorted()
    }

    static func toData(_ value: Any?) -> Data? {
        guard let value else { return nil }

        // Unwrap Optional<...> values reflected from library models.
        let mirror = Mirror(reflecting: value)
        if mirror.displayStyle == .optional {
            guard let wrapped = mirror.children.first?.value else { return nil }
            return toData(wrapped)
        }

        if let data = value as? Data {
            return data
        }
        if let data = value as? NSData {
            return data as Data
        }
        if let bytes = value as? [UInt8] {
            return Data(bytes)
        }
        if let bytes = value as? ArraySlice<UInt8> {
            return Data(bytes)
        }
        if let numbers = value as? [NSNumber] {
            return Data(numbers.map { UInt8(truncating: $0) })
        }
        return nil
    }

    static func extractPayload(from object: Any) -> Data? {
        var visited = Set<ObjectIdentifier>()
        return extractPayload(from: object, depth: 0, visited: &visited)
    }

    private static func extractPayload(
        from object: Any,
        depth: Int,
        visited: inout Set<ObjectIdentifier>
    ) -> Data? {
        guard depth <= maxLookupDepth else { return nil }
        if let data = toData(object), !data.isEmpty {
            return data
        }

        let mirror = Mirror(reflecting: object)
        if mirror.displayStyle == .class {
            let objectId = ObjectIdentifier(object as AnyObject)
            if visited.contains(objectId) {
                return nil
            }
            visited.insert(objectId)
        }

        // First pass: prefer obvious payload labels.
        for child in mirror.children {
            let label = child.label?.lowercased() ?? ""
            if (label.contains("data") || label.contains("body") || label.contains("raw") || label.contains("value")),
               let data = toData(child.value),
               !data.isEmpty {
                return data
            }
        }

        // Second pass: recurse for nested payload.
        for child in mirror.children {
            if let data = extractPayload(from: child.value, depth: depth + 1, visited: &visited) {
                return data
            }
        }

        return nil
    }

    static func lookup(_ object: Any, candidates: [String]) -> Any? {
        var visited = Set<ObjectIdentifier>()
        return lookup(object, candidates: candidates, depth: 0, visited: &visited)
    }

    private static func lookup(
        _ object: Any,
        candidates: [String],
        depth: Int,
        visited: inout Set<ObjectIdentifier>
    ) -> Any? {
        guard depth <= maxLookupDepth else { return nil }

        let mirror = Mirror(reflecting: object)

        // Prevent infinite recursion when reflected object graph has cycles.
        if mirror.displayStyle == .class {
            let objectId = ObjectIdentifier(object as AnyObject)
            if visited.contains(objectId) {
                return nil
            }
            visited.insert(objectId)
        }

        for child in mirror.children {
            guard let label = child.label else { continue }
            if candidates.contains(label) {
                return child.value
            }
        }

        for child in mirror.children {
            if let nested = lookup(
                child.value,
                candidates: candidates,
                depth: depth + 1,
                visited: &visited
            ) {
                return nested
            }
        }

        return nil
    }

    static func mirrorChildren(_ object: Any) -> [Mirror.Child] {
        Array(Mirror(reflecting: object).children)
    }

    private static func string(_ model: Any, _ keys: [String]) -> String? {
        for key in keys {
            if let value = lookup(model, candidates: [key]) {
                let str = String(describing: value).trimmingCharacters(in: .whitespacesAndNewlines)
                if !str.isEmpty, str != "nil" {
                    return str
                }
            }
        }
        return nil
    }

    private static func normalizeDate(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let digits = raw.filter(\.isNumber)
        if digits.count == 8 {
            let yyyy = Int(String(digits.prefix(4))) ?? 0
            if (1900...2200).contains(yyyy) {
                let dd = digits.suffix(2)
                let mm = digits.dropFirst(4).prefix(2)
                return "\(dd)/\(mm)/\(digits.prefix(4))"
            }
            let yy = Int(String(digits.prefix(2))) ?? 0
            let year = yy > 50 ? "19\(digits.prefix(2))" : "20\(digits.prefix(2))"
            return "\(digits.dropFirst(4).prefix(2))/\(digits.dropFirst(2).prefix(2))/\(year)"
        }
        return raw
    }

    private static func hasPersonalData(_ info: PersonalInfo) -> Bool {
        !(info.fullName?.isEmpty ?? true) || !(info.idNumber?.isEmpty ?? true)
    }

    private static func groupTokens(for groupCode: String) -> [String] {
        let normalized = groupCode.uppercased()
        var tokens = [normalized]
        switch normalized {
        case "COM":
            tokens += ["0X60", "96", "0X011E", "286"]
        case "SOD":
            tokens += ["0X77", "119", "0X011D", "285"]
        case "DG11":
            tokens += ["0X6B", "107", "0X010B", "267"]
        case "DG12":
            tokens += ["0X6C", "108", "0X010C", "268"]
        case "DG13":
            tokens += ["0X6D", "109", "0X010D", "269"]
        case "DG14":
            tokens += ["0X6E", "110", "0X010E", "270"]
        case "DG15":
            tokens += ["0X6F", "111", "0X010F", "271"]
        default:
            break
        }
        return tokens
    }

    private static func matchesGroupToken(_ value: String, tokens: [String]) -> Bool {
        let upper = value.uppercased()
        return tokens.contains { upper.contains($0) }
    }
}

#else
public final class CoreNfcCardReaderService: NfcCardReaderService, @unchecked Sendable {
    public init() {}

    public func readCard(
        mrzData: MrzData,
        config: CCCDConfig,
        onProgress: @escaping @Sendable (ReadingStatus) -> Void
    ) async throws -> CCCDData {
        _ = (mrzData, config, onProgress)
        throw CCCDError.nfcNotSupported
    }
}
#endif
