package com.vncccd.sdk

import com.vncccd.sdk.models.CCCDData
import com.vncccd.sdk.models.CCCDError
import com.vncccd.sdk.models.MrzData
import com.vncccd.sdk.models.ReadingStatus

/**
 * Callback interface cho toàn bộ flow đọc CCCD.
 *
 * Host app implement interface này để nhận kết quả:
 * ```kotlin
 * val callback = object : CCCDCallback {
 *     override fun onMrzScanned(mrzData: MrzData) {
 *         // MRZ đã được quét thành công
 *     }
 *     override fun onNfcProgress(status: ReadingStatus) {
 *         // Cập nhật tiến trình đọc NFC
 *     }
 *     override fun onSuccess(cccdData: CCCDData) {
 *         // Đọc chip thành công, hiển thị dữ liệu
 *     }
 *     override fun onError(error: CCCDError) {
 *         // Xử lý lỗi
 *     }
 * }
 * ```
 */
interface CCCDCallback {
    /**
     * Được gọi khi MRZ đã được quét thành công qua camera.
     * @param mrzData Dữ liệu MRZ đã parse (doc number, DOB, DOE)
     */
    fun onMrzScanned(mrzData: MrzData)

    /**
     * Được gọi để báo tiến trình đọc NFC chip.
     * @param status Trạng thái hiện tại
     */
    fun onNfcProgress(status: ReadingStatus)

    /**
     * Được gọi khi đọc chip thành công.
     * @param cccdData Toàn bộ dữ liệu CCCD đọc được
     */
    fun onSuccess(cccdData: CCCDData)

    /**
     * Được gọi khi có lỗi xảy ra ở bất kỳ bước nào.
     * @param error Chi tiết lỗi
     */
    fun onError(error: CCCDError)
}

/**
 * Adapter class cho CCCDCallback - chỉ override methods cần thiết.
 */
open class CCCDCallbackAdapter : CCCDCallback {
    override fun onMrzScanned(mrzData: MrzData) {}
    override fun onNfcProgress(status: ReadingStatus) {}
    override fun onSuccess(cccdData: CCCDData) {}
    override fun onError(error: CCCDError) {}
}
