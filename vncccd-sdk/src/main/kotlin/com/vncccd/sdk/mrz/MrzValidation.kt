package com.vncccd.sdk.mrz

/**
 * Kết quả kiểm tra tính toàn vẹn của một bộ 3 dòng MRZ.
 *
 * Mỗi cờ tương ứng một tín hiệu độc lập. Càng nhiều tín hiệu đúng thì
 * xác suất OCR đọc sai mà vẫn lọt qua càng thấp, nên analyzer dùng
 * [isStrong] để quyết định có chấp nhận ngay chỉ với một frame hay không.
 */
data class MrzValidation(
    /** Check digit của số giấy tờ (line 1, vị trí 14) khớp */
    val documentNumberValid: Boolean,

    /** Check digit ngày sinh (line 2, vị trí 6) khớp */
    val dateOfBirthValid: Boolean,

    /** Check digit ngày hết hạn (line 2, vị trí 14) khớp */
    val dateOfExpiryValid: Boolean,

    /** Composite check digit (line 2, vị trí 29) khớp */
    val compositeValid: Boolean,

    /** Cả hai ngày đều có tháng 01-12 và ngày 01-31 */
    val datesPlausible: Boolean,

    /**
     * Cross-check riêng cho CCCD Việt Nam: mã tỉnh hợp lệ, 2 số năm sinh
     * trong số CCCD khớp với YY của ngày sinh, và mã thế kỷ/giới tính
     * khớp với ô sex trong MRZ.
     */
    val vietnamConsistent: Boolean
) {
    /** Số tín hiệu đã pass, dùng để so sánh giữa các frame. */
    val score: Int
        get() = listOf(
            documentNumberValid,
            dateOfBirthValid,
            dateOfExpiryValid,
            compositeValid,
            datesPlausible,
            vietnamConsistent
        ).count { it }

    /** Toàn bộ check digit theo ICAO 9303 đều khớp. */
    val allCheckDigitsValid: Boolean
        get() = documentNumberValid && dateOfBirthValid && dateOfExpiryValid && compositeValid

    /**
     * Đủ tin cậy để chấp nhận ngay từ một frame duy nhất.
     *
     * Ngày sinh / ngày hết hạn đã bắt buộc phải đúng check digit mới parse
     * được, nên ngưỡng bổ sung là check digit số giấy tờ cộng thêm ít nhất
     * một tín hiệu độc lập (composite hoặc cross-check CCCD).
     */
    val isStrong: Boolean
        get() = documentNumberValid &&
                dateOfBirthValid &&
                dateOfExpiryValid &&
                datesPlausible &&
                (compositeValid || vietnamConsistent)

    companion object {
        val NONE = MrzValidation(
            documentNumberValid = false,
            dateOfBirthValid = false,
            dateOfExpiryValid = false,
            compositeValid = false,
            datesPlausible = false,
            vietnamConsistent = false
        )
    }
}
