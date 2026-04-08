import Foundation

public struct CCCDConfig: Sendable, Codable, Equatable {
    public var readFaceImage: Bool
    public var readPersonalInfo: Bool
    public var nfcTimeoutMs: Int
    public var mrzTimeoutMs: Int
    public var mrzConsecutiveFrames: Int
    public var enableSoundEffects: Bool
    public var enableVibration: Bool
    public var locale: String

    public init(
        readFaceImage: Bool = true,
        readPersonalInfo: Bool = true,
        nfcTimeoutMs: Int = 30_000,
        mrzTimeoutMs: Int = 0,
        mrzConsecutiveFrames: Int = 3,
        enableSoundEffects: Bool = true,
        enableVibration: Bool = true,
        locale: String = "vi"
    ) {
        self.readFaceImage = readFaceImage
        self.readPersonalInfo = readPersonalInfo
        self.nfcTimeoutMs = nfcTimeoutMs
        self.mrzTimeoutMs = mrzTimeoutMs
        self.mrzConsecutiveFrames = mrzConsecutiveFrames
        self.enableSoundEffects = enableSoundEffects
        self.enableVibration = enableVibration
        self.locale = locale
    }

    public static var defaultConfig: CCCDConfig {
        CCCDConfig()
    }
}
