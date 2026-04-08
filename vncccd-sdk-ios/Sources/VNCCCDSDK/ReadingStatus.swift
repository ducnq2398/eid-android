import Foundation

public enum ReadingStatus: String, Sendable, Codable, CaseIterable {
    case connecting
    case authenticating
    case readingDG1
    case readingDG2
    case readingDG13
    case verifying
    case completed
    case error

    public var description: String {
        switch self {
        case .connecting: return "Dang ket noi voi the..."
        case .authenticating: return "Dang xac thuc..."
        case .readingDG1: return "Dang doc thong tin MRZ..."
        case .readingDG2: return "Dang doc anh chan dung..."
        case .readingDG13: return "Dang doc thong tin ca nhan..."
        case .verifying: return "Dang xac minh du lieu..."
        case .completed: return "Hoan thanh!"
        case .error: return "Co loi xay ra"
        }
    }
}
