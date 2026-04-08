package com.vncccd.sdk

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import com.vncccd.sdk.models.CCCDError
import com.vncccd.sdk.models.MrzData
import com.vncccd.sdk.mrz.MrzScannerActivity
import com.vncccd.sdk.nfc.NfcReaderActivity

/**
 * Entry point chính của VN CCCD SDK.
 *
 * Sử dụng:
 * ```kotlin
 * // Full flow: MRZ scan → NFC read
 * CCCDReader.startFullFlow(activity, config, callback)
 *
 * // Chỉ scan MRZ
 * CCCDReader.startMrzScan(activity, config, callback)
 *
 * // Chỉ đọc NFC (đã có MRZ data)
 * CCCDReader.startNfcRead(activity, mrzData, config, callback)
 * ```
 */
object CCCDReader {

    private var callback: CCCDCallback? = null
    private var config: CCCDConfig = CCCDConfig.defaultConfig()
    private var currentMrzData: MrzData? = null
    private var mrzResultDelivered = false
    private var nfcResultDelivered = false

    internal const val REQUEST_CODE_MRZ = 10001
    internal const val REQUEST_CODE_NFC = 10002

    internal const val EXTRA_CONFIG = "extra_cccd_config"
    internal const val EXTRA_MRZ_DATA = "extra_mrz_data"
    internal const val EXTRA_RESULT_MRZ = "extra_result_mrz"
    internal const val EXTRA_RESULT_CCCD = "extra_result_cccd"
    internal const val EXTRA_ERROR = "extra_error"

    /**
     * Bắt đầu full flow: Quét MRZ → Đọc NFC chip.
     *
     * @param activity Activity hiện tại
     * @param config Cấu hình SDK
     * @param callback Callback nhận kết quả
     */
    fun startFullFlow(activity: Activity, config: CCCDConfig = CCCDConfig.defaultConfig(), callback: CCCDCallback) {
        this.config = config
        this.callback = callback

        // Check NFC availability first
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
        if (nfcAdapter == null) {
            callback.onError(CCCDError.NfcNotSupported())
            return
        }
        if (!nfcAdapter.isEnabled) {
            callback.onError(CCCDError.NfcNotEnabled())
            return
        }

        // Start MRZ scanner
        startMrzScan(activity, config, object : CCCDCallbackAdapter() {
            override fun onMrzScanned(mrzData: MrzData) {
                callback.onMrzScanned(mrzData)
                currentMrzData = mrzData
                // Automatically proceed to NFC reading
                startNfcRead(activity, mrzData, config, callback)
            }

            override fun onError(error: CCCDError) {
                callback.onError(error)
            }
        })
    }

    /**
     * Chỉ quét MRZ (không đọc NFC).
     *
     * @param activity Activity hiện tại
     * @param config Cấu hình SDK
     * @param callback Callback nhận kết quả MRZ
     */
    fun startMrzScan(activity: Activity, config: CCCDConfig = CCCDConfig.defaultConfig(), callback: CCCDCallback) {
        this.config = config
        this.callback = callback
        mrzResultDelivered = false

        val intent = Intent(activity, MrzScannerActivity::class.java).apply {
            putExtra(EXTRA_CONFIG, config)
        }
        activity.startActivityForResult(intent, REQUEST_CODE_MRZ)
    }

    /**
     * Chỉ đọc NFC chip (đã có MRZ data từ trước).
     *
     * @param activity Activity hiện tại
     * @param mrzData Dữ liệu MRZ đã scan
     * @param config Cấu hình SDK
     * @param callback Callback nhận kết quả
     */
    fun startNfcRead(
        activity: Activity,
        mrzData: MrzData,
        config: CCCDConfig = CCCDConfig.defaultConfig(),
        callback: CCCDCallback
    ) {
        this.config = config
        this.callback = callback
        this.currentMrzData = mrzData
        nfcResultDelivered = false

        val intent = Intent(activity, NfcReaderActivity::class.java).apply {
            putExtra(EXTRA_CONFIG, config)
            putExtra(EXTRA_MRZ_DATA, mrzData)
        }
        activity.startActivityForResult(intent, REQUEST_CODE_NFC)
    }

    /**
     * Phải gọi trong onActivityResult của host Activity.
     */
    @Suppress("DEPRECATION")
    fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            REQUEST_CODE_MRZ -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val mrzData = data.getSerializableExtra(EXTRA_RESULT_MRZ) as? MrzData
                    if (mrzData != null) {
                        dispatchMrzScanned(mrzData)
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    dispatchMrzError(CCCDError.Cancelled())
                }
            }

            REQUEST_CODE_NFC -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val cccdData = data.getSerializableExtra(EXTRA_RESULT_CCCD) as? com.vncccd.sdk.models.CCCDData
                    if (cccdData != null) {
                        dispatchNfcSuccess(cccdData)
                    }
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    dispatchNfcError(CCCDError.Cancelled())
                }
            }
        }
    }

    /**
     * Lấy callback hiện tại (internal use).
     */
    internal fun getCallback(): CCCDCallback? = callback

    /**
     * Lấy config hiện tại (internal use).
     */
    internal fun getConfig(): CCCDConfig = config

    internal fun dispatchMrzScanned(mrzData: MrzData) {
        if (mrzResultDelivered) return
        mrzResultDelivered = true
        callback?.onMrzScanned(mrzData)
    }

    internal fun dispatchMrzError(error: CCCDError) {
        if (mrzResultDelivered) return
        mrzResultDelivered = true
        callback?.onError(error)
    }

    internal fun dispatchNfcSuccess(cccdData: com.vncccd.sdk.models.CCCDData) {
        if (nfcResultDelivered) return
        nfcResultDelivered = true
        callback?.onSuccess(cccdData)
    }

    internal fun dispatchNfcError(error: CCCDError) {
        if (nfcResultDelivered) return
        nfcResultDelivered = true
        callback?.onError(error)
    }

    /**
     * Reset SDK state.
     */
    fun reset() {
        callback = null
        currentMrzData = null
        config = CCCDConfig.defaultConfig()
        mrzResultDelivered = false
        nfcResultDelivered = false
    }
}
