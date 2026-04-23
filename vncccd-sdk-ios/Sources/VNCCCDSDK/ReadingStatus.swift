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
        case .connecting: return "Đang kết nối với thẻ..."
        case .authenticating: return "Đang xác thực..."
        case .readingDG1: return "Đang đọc thông tin MRZ..."
        case .readingDG2: return "Đang đọc ảnh chân dung..."
        case .readingDG13: return "Đang đọc thông tin cá nhân..."
        case .verifying: return "Đang xác minh dữ liệu..."
        case .completed: return "Hoàn thành!"
        case .error: return "Có lỗi xảy ra"
        }
    }
}
