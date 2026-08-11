package com.vncccd.sdk

import java.io.Serializable

/**
 * Cấu hình SDK đọc CCCD.
 *
 * Sử dụng Builder pattern:
 * ```kotlin
 * val config = CCCDConfig.Builder()
 *     .setReadFaceImage(true)
 *     .setReadPersonalInfo(true)
 *     .setTimeout(30_000L)
 *     .build()
 * ```
 */
data class CCCDConfig(
    /** Có đọc ảnh chân dung (DG2) không. Nếu false sẽ bỏ qua → nhanh hơn. */
    val readFaceImage: Boolean = true,

    /** Có đọc thông tin cá nhân DG13 không */
    val readPersonalInfo: Boolean = true,

    /** Timeout cho NFC reading (milliseconds) */
    val nfcTimeoutMs: Long = 30_000L,

    /** Timeout cho MRZ scanning (milliseconds). 0 = không timeout */
    val mrzTimeoutMs: Long = 0L,

    /** Số frame liên tiếp MRZ phải giống nhau để chấp nhận */
    val mrzConsecutiveFrames: Int = 3,

    /**
     * Chỉ đưa vùng trong khung quét vào OCR thay vì cả khung hình.
     * Nhanh hơn nhiều và loại được text nhiễu trên thẻ.
     */
    val mrzCropToScanArea: Boolean = true,

    /**
     * Độ phân giải luồng phân tích MRZ. Thấp hơn 1280×720 thì ký tự MRZ
     * không đủ pixel để OCR đọc tin cậy.
     */
    val mrzAnalysisWidth: Int = 1280,
    val mrzAnalysisHeight: Int = 720,

    /** Tự bật đèn khi khung hình quá tối */
    val mrzAutoTorch: Boolean = true,

    /** Bật/tắt hiệu ứng âm thanh khi scan thành công */
    val enableSoundEffects: Boolean = true,

    /** Bật/tắt vibrate khi scan thành công */
    val enableVibration: Boolean = true,

    /** Ngôn ngữ hiển thị (vi / en) */
    val locale: String = "vi"
) : Serializable {

    class Builder {
        private var readFaceImage = true
        private var readPersonalInfo = true
        private var nfcTimeoutMs = 30_000L
        private var mrzTimeoutMs = 0L
        private var mrzConsecutiveFrames = 3
        private var mrzCropToScanArea = true
        private var mrzAnalysisWidth = 1280
        private var mrzAnalysisHeight = 720
        private var mrzAutoTorch = true
        private var enableSoundEffects = true
        private var enableVibration = true
        private var locale = "vi"

        fun setReadFaceImage(value: Boolean) = apply { readFaceImage = value }
        fun setReadPersonalInfo(value: Boolean) = apply { readPersonalInfo = value }
        fun setNfcTimeout(timeoutMs: Long) = apply { nfcTimeoutMs = timeoutMs }
        fun setMrzTimeout(timeoutMs: Long) = apply { mrzTimeoutMs = timeoutMs }
        fun setMrzConsecutiveFrames(frames: Int) = apply { mrzConsecutiveFrames = frames }
        fun setMrzCropToScanArea(value: Boolean) = apply { mrzCropToScanArea = value }
        fun setMrzAnalysisResolution(width: Int, height: Int) = apply {
            mrzAnalysisWidth = width
            mrzAnalysisHeight = height
        }

        fun setMrzAutoTorch(value: Boolean) = apply { mrzAutoTorch = value }
        fun setEnableSoundEffects(value: Boolean) = apply { enableSoundEffects = value }
        fun setEnableVibration(value: Boolean) = apply { enableVibration = value }
        fun setLocale(locale: String) = apply { this.locale = locale }

        fun build() = CCCDConfig(
            readFaceImage = readFaceImage,
            readPersonalInfo = readPersonalInfo,
            nfcTimeoutMs = nfcTimeoutMs,
            mrzTimeoutMs = mrzTimeoutMs,
            mrzConsecutiveFrames = mrzConsecutiveFrames,
            mrzCropToScanArea = mrzCropToScanArea,
            mrzAnalysisWidth = mrzAnalysisWidth,
            mrzAnalysisHeight = mrzAnalysisHeight,
            mrzAutoTorch = mrzAutoTorch,
            enableSoundEffects = enableSoundEffects,
            enableVibration = enableVibration,
            locale = locale
        )
    }

    companion object {
        private const val serialVersionUID = 1L

        /** Cấu hình mặc định */
        @JvmStatic
        fun defaultConfig(): CCCDConfig = CCCDConfig()
    }
}
