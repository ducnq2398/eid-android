package com.vncccd.sdk.models

import android.graphics.Bitmap
import java.io.Serializable

/**
 * Toàn bộ dữ liệu đọc được từ chip CCCD.
 * Bao gồm MRZ data, thông tin cá nhân (DG13), và ảnh chân dung (DG2).
 */
data class CCCDData(
    /** Dữ liệu MRZ (từ scanner hoặc DG1) */
    val mrzData: MrzData,

    /** Thông tin cá nhân chi tiết từ DG13 */
    val personalInfo: PersonalInfo? = null,

    /** Ảnh chân dung từ DG2 */
    @Transient
    val faceImageBase64: String? = null,

    /** Raw bytes DG1 */
    val rawDG1: ByteArray? = null,

    /** Raw bytes DG2 (ảnh gốc chưa decode) */
    val rawDG2: ByteArray? = null,

    /** Raw bytes DG13 */
    val rawDG13: ByteArray? = null,

    /** Kết quả passive authentication (nếu có) */
    val isPassiveAuthSuccess: Boolean? = null
) : Serializable {
    companion object {
        private const val serialVersionUID = 1L
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CCCDData) return false

        return mrzData == other.mrzData &&
                personalInfo == other.personalInfo &&
                rawDG1.contentEqualsNullable(other.rawDG1) &&
                rawDG2.contentEqualsNullable(other.rawDG2) &&
                rawDG13.contentEqualsNullable(other.rawDG13) &&
                isPassiveAuthSuccess == other.isPassiveAuthSuccess
    }

    private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean {
        if (this == null && other == null) return true
        if (this == null || other == null) return false
        return this.contentEquals(other)
    }

    override fun hashCode(): Int {
        var result = mrzData.hashCode()
        result = 31 * result + (personalInfo?.hashCode() ?: 0)
        result = 31 * result + (rawDG1?.contentHashCode() ?: 0)
        result = 31 * result + (rawDG2?.contentHashCode() ?: 0)
        result = 31 * result + (rawDG13?.contentHashCode() ?: 0)
        result = 31 * result + isPassiveAuthSuccess.hashCode()
        return result
    }
}
