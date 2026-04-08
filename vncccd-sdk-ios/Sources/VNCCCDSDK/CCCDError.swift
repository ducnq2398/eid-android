import Foundation

public enum CCCDError: Error, Sendable, Equatable {
    case nfcNotSupported
    case nfcNotEnabled
    case cameraNotAvailable
    case mrzNotFound
    case mrzInvalid(details: String)
    case authenticationFailed(details: String? = nil)
    case connectionLost(details: String? = nil)
    case timeout
    case dataGroupReadFailed(dgName: String, details: String? = nil)
    case cancelled
    case unknown(details: String? = nil)

    public var message: String {
        switch self {
        case .nfcNotSupported:
            return "Thiết bị không hỗ trợ NFC"
        case .nfcNotEnabled:
            return "Vui lòng bật NFC trên thiết bị"
        case .cameraNotAvailable:
            return "Không thể truy cập camera"
        case .mrzNotFound:
            return "Không tìm thấy mã MRZ trên thẻ"
        case .mrzInvalid(let details):
            return "Mã MRZ không hợp lệ: \(details)"
        case .authenticationFailed(let details):
            return details ?? "Xác thực thẻ thất bại. Vui lòng kiểm tra lại thông tin MRZ."
        case .connectionLost(let details):
            return details ?? "Mất kết nối với thẻ. Vui lòng giữ thẻ ổn định."
        case .timeout:
            return "Hết thời gian chờ. Vui lòng thử lại."
        case .dataGroupReadFailed(let dgName, let details):
            return details ?? "Không thể đọc \(dgName) từ thẻ"
        case .cancelled:
            return "Đã hủy thao tác"
        case .unknown(let details):
            return "Lỗi không xác định: \(details ?? "unknown")"
        }
    }
}
