package com.vncccd.sdk.models

import java.io.Serializable

/**
 * Thông tin cá nhân đọc từ DG13 của chip CCCD.
 * DG13 chứa thông tin đặc thù Việt Nam mà DG1 (MRZ) không thể chứa hết.
 */
data class PersonalInfo(
    /** Họ và tên đầy đủ (tiếng Việt có dấu) */
    val fullName: String? = null,

    /** Số CCCD đầy đủ (12 chữ số) */
    val idNumber: String? = null,

    /** Ngày sinh (dd/MM/yyyy) */
    val dateOfBirth: String? = null,

    /** Giới tính (Nam/Nữ) */
    val gender: String? = null,

    /** Quốc tịch */
    val nationality: String? = null,

    /** Dân tộc */
    val ethnicity: String? = null,

    /** Tôn giáo */
    val religion: String? = null,

    /** Quê quán */
    val placeOfOrigin: String? = null,

    /** Nơi thường trú */
    val placeOfResidence: String? = null,

    /** Đặc điểm nhận dạng */
    val personalIdentification: String? = null,

    /** Ngày cấp (dd/MM/yyyy) */
    val dateOfIssue: String? = null,

    /** Ngày hết hạn (dd/MM/yyyy) */
    val dateOfExpiry: String? = null,

    /** Họ tên cha */
    val fatherName: String? = null,

    /** Họ tên mẹ */
    val motherName: String? = null,

    /** Họ tên vợ/chồng */
    val spouseName: String? = null,

    /** Số CCCD cũ / CMND */
    val oldIdNumber: String? = null
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }
}
