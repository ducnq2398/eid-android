package com.vncccd.sample

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vncccd.sdk.CCCDCallback
import com.vncccd.sdk.CCCDConfig
import com.vncccd.sdk.CCCDReader
import com.vncccd.sdk.models.CCCDData
import com.vncccd.sdk.models.CCCDError
import com.vncccd.sdk.models.MrzData
import com.vncccd.sdk.models.ReadingStatus
import com.vncccd.sdk.utils.NfcUtils

/**
 * Sample app demonstrating VN CCCD SDK usage.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SampleMainActivity"
    }

    private lateinit var btnFullScan: Button
    private lateinit var btnMrzOnly: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvNfcStatus: TextView

    private val cccdCallback = object : CCCDCallback {
        override fun onMrzScanned(mrzData: MrzData) {
            Log.d(TAG, "MRZ scanned: ${mrzData.fullDocumentNumber}")
            runOnUiThread {
                tvStatus.text = "MRZ: ${mrzData.fullDocumentNumber}\nĐang chờ đọc NFC..."
            }
        }

        override fun onNfcProgress(status: ReadingStatus) {
            Log.d(TAG, "NFC progress: ${status.description}")
            runOnUiThread {
                tvStatus.text = status.description
            }
        }

        override fun onSuccess(cccdData: CCCDData) {
            Log.d(TAG, "Success! Name: ${cccdData}")
            runOnUiThread {
                tvStatus.text = "Thành công!"
                navigateToResult(cccdData)
            }
        }

        override fun onError(error: CCCDError) {
            Log.e(TAG, "Error: ${error.message}", error.cause)
            runOnUiThread {
                tvStatus.text = "Lỗi: ${error.message}"
                Toast.makeText(this@MainActivity, error.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        checkNfcStatus()
    }

    private fun initViews() {
        btnFullScan = findViewById(R.id.btnFullScan)
        btnMrzOnly = findViewById(R.id.btnMrzOnly)
        tvStatus = findViewById(R.id.tvStatus)
        tvNfcStatus = findViewById(R.id.tvNfcStatus)

        btnFullScan.setOnClickListener {
            startFullScan()
        }

        btnMrzOnly.setOnClickListener {
            startMrzOnly()
        }
    }

    private fun checkNfcStatus() {
        when {
            !NfcUtils.isNfcSupported(this) -> {
                tvNfcStatus.text = "⚠️ Thiết bị không hỗ trợ NFC"
                tvNfcStatus.setTextColor(getColor(android.R.color.holo_red_light))
                btnFullScan.isEnabled = false
            }
            !NfcUtils.isNfcEnabled(this) -> {
                tvNfcStatus.text = "⚠️ NFC chưa được bật. Vui lòng bật NFC trong Cài đặt."
                tvNfcStatus.setTextColor(getColor(android.R.color.holo_orange_light))
            }
            else -> {
                tvNfcStatus.text = "✅ NFC đã sẵn sàng"
                tvNfcStatus.setTextColor(getColor(android.R.color.holo_green_light))
            }
        }
    }

    private fun startFullScan() {
        val config = CCCDConfig.Builder()
            .setReadFaceImage(true)
            .setReadPersonalInfo(true)
            .setNfcTimeout(30_000L)
            .setMrzConsecutiveFrames(3)
            .setEnableVibration(true)
            .build()

        tvStatus.text = "Đang khởi tạo..."
        CCCDReader.startFullFlow(this, config, cccdCallback)
    }

    private fun startMrzOnly() {
        val config = CCCDConfig.Builder()
            .setMrzConsecutiveFrames(3)
            .build()

        tvStatus.text = "Đang mở camera..."
        CCCDReader.startMrzScan(this, config, object : CCCDCallback {
            override fun onMrzScanned(mrzData: MrzData) {
                runOnUiThread {
                    tvStatus.text = buildString {
                        appendLine("✅ MRZ Scan Result:")
                        appendLine("Số CCCD: ${mrzData.fullDocumentNumber}")
                        appendLine("Ngày sinh: ${mrzData.dateOfBirth}")
                        appendLine("Hết hạn: ${mrzData.dateOfExpiry}")
                        appendLine("Giới tính: ${mrzData.gender}")
                        appendLine("Quốc tịch: ${mrzData.nationality}")
                        appendLine("Họ tên: ${mrzData.fullNameMrz}")
                    }
                }
            }

            override fun onNfcProgress(status: ReadingStatus) {}
            override fun onSuccess(cccdData: CCCDData) {}

            override fun onError(error: CCCDError) {
                runOnUiThread {
                    tvStatus.text = "Lỗi: ${error.message}"
                }
            }
        })
    }

    private fun navigateToResult(cccdData: CCCDData) {
        val intent = Intent(this, ResultActivity::class.java).apply {
            putExtra(ResultActivity.EXTRA_CCCD_DATA, cccdData)
        }
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        CCCDReader.handleActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        checkNfcStatus()
    }
}
