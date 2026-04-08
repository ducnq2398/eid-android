import Foundation

#if canImport(CoreNFC) && canImport(NFCPassportReader)
import CoreNFC
import NFCPassportReader
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
        guard NFCNDEFReaderSession.readingAvailable else {
            throw CCCDError.nfcNotSupported
        }

        let mrzKey = MrzKeyBuilder.build(from: mrzData)

        onProgress(.connecting)
        onProgress(.authenticating)

        let tags: [DataGroupId] = [.COM, .SOD, .DG1, .DG2, .DG11, .DG12, .DG14, .DG15]

        let passportModel: Any
        do {
            passportModel = try await reader.readPassport(mrzKey: mrzKey, tags: tags)
        } catch {
            throw mapNfcError(error)
        }

        onProgress(.readingDG1)

        let rawDG1 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG1")

        var rawDG2: Data?
        var faceBase64: String?
        if config.readFaceImage {
            onProgress(.readingDG2)
            rawDG2 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG2")
            faceBase64 = ReflectionExtractor.extractFaceImageBase64(passportModel)
        }

        var rawDG13 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG13")
        var personalInfo: PersonalInfo?
        if config.readPersonalInfo {
            onProgress(.readingDG13)
            if let dg13 = rawDG13 {
                personalInfo = DG13Parser.parse(rawBytes: dg13)
            }
            if personalInfo == nil {
                personalInfo = ReflectionExtractor.extractPersonalInfo(passportModel)
            }
            if rawDG13 == nil {
                rawDG13 = ReflectionExtractor.extractDataGroupRaw(passportModel, groupCode: "DG11")
            }
        }

        onProgress(.verifying)
        onProgress(.completed)

        return CCCDData(
            mrzData: mrzData,
            personalInfo: personalInfo,
            faceImageBase64: faceBase64,
            rawDG1: rawDG1,
            rawDG2: rawDG2,
            rawDG13: rawDG13,
            isPassiveAuthSuccess: ReflectionExtractor.extractPassiveAuthResult(passportModel)
        )
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
}

private enum MrzKeyBuilder {
    static func build(from mrzData: MrzData) -> String {
        let doc = sanitizedDocument(mrzData)
        let dob = String(mrzData.dateOfBirth.filter(\.isNumber).prefix(6))
        let doe = String(mrzData.dateOfExpiry.filter(\.isNumber).prefix(6))

        let docCd = MrzParser.computeCheckDigit(input: doc)
        let dobCd = MrzParser.computeCheckDigit(input: dob)
        let doeCd = MrzParser.computeCheckDigit(input: doe)

        return "\(doc)\(docCd)\(dob)\(dobCd)\(doe)\(doeCd)"
    }

    private static func sanitizedDocument(_ mrzData: MrzData) -> String {
        let full = mrzData.fullDocumentNumber
            .uppercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "<", with: "")

        if !full.isEmpty {
            return full
        }

        let raw = mrzData.documentNumber
            .uppercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "<", with: "")

        return raw
    }
}

private enum ReflectionExtractor {
    static func extractFaceImageBase64(_ model: Any) -> String? {
        if let image = lookup(model, candidates: ["passportImage", "faceImage", "portraitImage"]) as? UIImage,
           let data = image.jpegData(compressionQuality: 0.95) {
            return data.base64EncodedString()
        }

        if let data = lookup(model, candidates: ["passportImageData", "faceImageData", "portraitImageData"]) as? Data {
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
        if let direct = lookup(model, candidates: ["\(groupCode.lowercased())Data", "raw\(groupCode)", groupCode]) as? Data {
            return direct
        }

        if let groups = lookup(model, candidates: ["dataGroupsRead", "dataGroups", "dataGroupMap"]) {
            for child in mirrorChildren(groups) {
                guard let label = child.label else { continue }
                if label.uppercased().contains(groupCode.uppercased()), let data = child.value as? Data {
                    return data
                }

                let keyDescription = String(describing: child.value)
                if keyDescription.uppercased().contains(groupCode.uppercased()) {
                    if let data = lookup(child.value, candidates: ["data", "body", "rawData", "value"]) as? Data {
                        return data
                    }
                }
            }

            if let dict = groups as? [AnyHashable: Any] {
                for (key, value) in dict {
                    if String(describing: key).uppercased().contains(groupCode.uppercased()) {
                        if let data = value as? Data {
                            return data
                        }
                        if let data = lookup(value, candidates: ["data", "body", "rawData", "value"]) as? Data {
                            return data
                        }
                    }
                }
            }
        }

        return nil
    }

    static func lookup(_ object: Any, candidates: [String]) -> Any? {
        let mirror = Mirror(reflecting: object)

        for child in mirror.children {
            guard let label = child.label else { continue }
            if candidates.contains(label) {
                return child.value
            }
        }

        for child in mirror.children {
            if let nested = lookup(child.value, candidates: candidates) {
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
