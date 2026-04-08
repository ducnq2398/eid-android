import Foundation

public struct CCCDData: Sendable, Codable, Equatable {
    public let mrzData: MrzData
    public let personalInfo: PersonalInfo?
    public let faceImageBase64: String?
    public let rawDG1: Data?
    public let rawDG2: Data?
    public let rawDG13: Data?
    public let isPassiveAuthSuccess: Bool?

    public init(
        mrzData: MrzData,
        personalInfo: PersonalInfo? = nil,
        faceImageBase64: String? = nil,
        rawDG1: Data? = nil,
        rawDG2: Data? = nil,
        rawDG13: Data? = nil,
        isPassiveAuthSuccess: Bool? = nil
    ) {
        self.mrzData = mrzData
        self.personalInfo = personalInfo
        self.faceImageBase64 = faceImageBase64
        self.rawDG1 = rawDG1
        self.rawDG2 = rawDG2
        self.rawDG13 = rawDG13
        self.isPassiveAuthSuccess = isPassiveAuthSuccess
    }
}
