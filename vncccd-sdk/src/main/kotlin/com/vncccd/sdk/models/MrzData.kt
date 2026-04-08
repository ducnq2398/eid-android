package com.vncccd.sdk.models

import java.io.Serializable

/**
 * Dữ liệu MRZ đã được parse từ thẻ CCCD.
 * Format: TD1 (3 dòng × 30 ký tự)
 *
 * Vietnam CCCD MRZ structure:
 * Line 1: I<VNM[DocNumber(9)][CheckDigit(1)][OptionalData(15)]
 * Line 2: [DOB(6)][CheckDigit(1)][Sex(1)][DOE(6)][CheckDigit(1)][Nationality(3)][OptionalData(11)]
 * Line 3: [Surname]<<[GivenNames]
 */
data class MrzData(
    /** Số CCCD (trích xuất từ MRZ, có thể bị cắt 9 ký tự) */
    val documentNumber: String,

    /** Ngày sinh theo format YYMMDD */
    val dateOfBirth: String,

    /** Ngày hết hạn theo format YYMMDD */
    val dateOfExpiry: String,

    /** Giới tính: M (Nam) hoặc F (Nữ) */
    val gender: String = "",

    /** Quốc tịch (VNM) */
    val nationality: String = "VNM",

    /** Họ tên trích từ MRZ Line 3 */
    val fullNameMrz: String = "",

    /** 3 dòng MRZ gốc ghép lại */
    val rawMrz: String = "",

    /** Optional data chứa phần bổ sung doc number (nếu > 9 ký tự) */
    val optionalData1: String = "",

    /** Optional data line 2 */
    val optionalData2: String = ""
) : Serializable {

    /**
     * Lấy document number đầy đủ.
     * Vietnam CCCD có 12-digit ID, trong MRZ TD1:
     * - 9 ký tự đầu ở vị trí document number
     * - 3 ký tự còn lại ở optional data (line 1)
     */
    val fullDocumentNumber: String
        get() {
            val primary = documentNumber.replace("<", "").trim()
            if (primary.isEmpty()) return ""

            // Với CCCD VN, phần nối của số giấy tờ thường là chuỗi số ở đầu optionalData1.
            val extensionDigits = optionalData1.takeWhile { it.isDigit() }
            val merged = if (extensionDigits.isNotEmpty()) {
                primary + extensionDigits
            } else {
                val fallback = optionalData1.replace("<", "").trim()
                if (fallback.isNotEmpty()) primary + fallback else primary
            }

            // Chuẩn CCCD là 12 số; nếu OCR dư ký tự thì ưu tiên 12 ký tự đầu.
            return if (merged.all { it.isDigit() } && merged.length > 12) {
                merged.take(12)
            } else {
                merged
            }
        }

    companion object {
        private const val serialVersionUID = 1L
    }
}
