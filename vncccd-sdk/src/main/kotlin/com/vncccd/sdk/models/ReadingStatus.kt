package com.vncccd.sdk.models

/**
 * Trạng thái đọc NFC chip.
 * Được sử dụng trong callback để báo tiến trình đọc cho host app.
 */
enum class ReadingStatus(val description: String) {
    CONNECTING("Đang kết nối với thẻ..."),
    AUTHENTICATING("Đang xác thực..."),
    READING_DG1("Đang đọc thông tin MRZ..."),
    READING_DG2("Đang đọc ảnh chân dung..."),
    READING_DG13("Đang đọc thông tin cá nhân..."),
    VERIFYING("Đang xác minh dữ liệu..."),
    COMPLETED("Hoàn thành!"),
    ERROR("Có lỗi xảy ra")
}

/**
 * Các loại lỗi có thể xảy ra trong SDK.
 */
sealed class CCCDError(val message: String, val cause: Throwable? = null) {
    /** NFC không được hỗ trợ trên thiết bị */
    class NfcNotSupported : CCCDError("Thiết bị không hỗ trợ NFC")

    /** NFC chưa được bật */
    class NfcNotEnabled : CCCDError("Vui lòng bật NFC trên thiết bị")

    /** Camera không khả dụng */
    class CameraNotAvailable : CCCDError("Không thể truy cập camera")

    /** Không tìm thấy MRZ trên thẻ */
    class MrzNotFound : CCCDError("Không tìm thấy mã MRZ trên thẻ")

    /** MRZ không hợp lệ (check digit sai) */
    class MrzInvalid(details: String) : CCCDError("Mã MRZ không hợp lệ: $details")

    /** Xác thực BAC thất bại */
    class AuthenticationFailed(cause: Throwable? = null) :
        CCCDError("Xác thực thẻ thất bại. Vui lòng kiểm tra lại thông tin MRZ.", cause)

    /** Mất kết nối NFC trong quá trình đọc */
    class ConnectionLost(cause: Throwable? = null) :
        CCCDError("Mất kết nối với thẻ. Vui lòng giữ thẻ ổn định.", cause)

    /** Hết thời gian chờ */
    class Timeout : CCCDError("Hết thời gian chờ. Vui lòng thử lại.")

    /** Không đọc được data group */
    class DataGroupReadFailed(dgName: String, cause: Throwable? = null) :
        CCCDError("Không thể đọc $dgName từ thẻ", cause)

    /** Người dùng huỷ */
    class Cancelled : CCCDError("Đã huỷ thao tác")

    /** Lỗi không xác định */
    class Unknown(cause: Throwable? = null) :
        CCCDError("Lỗi không xác định: ${cause?.message ?: "unknown"}", cause)
}
