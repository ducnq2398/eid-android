package com.vncccd.sdk.nfc

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vncccd.sdk.CCCDConfig
import com.vncccd.sdk.CCCDReader
import com.vncccd.sdk.R
import com.vncccd.sdk.models.*
import kotlinx.coroutines.*

/**
 * Activity đọc NFC chip trên thẻ CCCD.
 *
 * Features:
 * - Hướng dẫn đặt thẻ lên NFC reader
 * - Animation pulse effect
 * - Progress indicator theo ReadingStatus
 * - Tự động đọc khi detect NFC tag
 * - Retry khi mất kết nối
 */
class NfcReaderActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "NfcReaderActivity"
    }

    // Views
    private lateinit var ivNfcIcon: ImageView
    private lateinit var tvStatus: TextView
    private lateinit var tvInstruction: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var progressBarReading: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var btnCancel: View

    // NFC
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null

    // Data
    private var config: CCCDConfig = CCCDConfig.defaultConfig()
    private var mrzData: MrzData? = null

    // Card reader
    private val cardReader = NfcCardReader()
    private var readingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc_reader)

        // Get extras
        config = intent.getSerializableExtra(CCCDReader.EXTRA_CONFIG) as? CCCDConfig
            ?: CCCDConfig.defaultConfig()
        mrzData = intent.getSerializableExtra(CCCDReader.EXTRA_MRZ_DATA) as? MrzData

        if (mrzData == null) {
            Toast.makeText(this, "Thiếu dữ liệu MRZ", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        setupNfc()
        startPulseAnimation()
    }

    private fun initViews() {
        ivNfcIcon = findViewById(R.id.ivNfcIcon)
        tvStatus = findViewById(R.id.tvStatus)
        tvInstruction = findViewById(R.id.tvInstruction)
        progressBar = findViewById(R.id.progressBar)
        progressBarReading = findViewById(R.id.progressBarReading)
        tvProgress = findViewById(R.id.tvProgress)
        btnCancel = findViewById(R.id.btnCancel)

        tvInstruction.text = getString(R.string.vncccd_nfc_instruction)
        tvStatus.text = getString(R.string.vncccd_nfc_waiting)

        btnCancel.setOnClickListener {
            readingJob?.cancel()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        // Initially hide progress
        progressBarReading.visibility = View.GONE
        tvProgress.visibility = View.GONE
    }

    private fun setupNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            CCCDReader.getCallback()?.onError(CCCDError.NfcNotSupported())
            finish()
            return
        }

        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE
        } else {
            0
        }
        pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
    }

    private fun startPulseAnimation() {
        val pulseAnim = AnimationUtils.loadAnimation(this, R.anim.pulse_animation)
        ivNfcIcon.startAnimation(pulseAnim)
    }

    private fun stopPulseAnimation() {
        ivNfcIcon.clearAnimation()
    }

    override fun onResume() {
        super.onResume()
        val techList = arrayOf(arrayOf(IsoDep::class.java.name))
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
        )
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, filters, techList)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    @Suppress("DEPRECATION")
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        if (intent == null) return

        val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        }

        if (tag == null) return

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            Log.w(TAG, "IsoDep not supported by this tag")
            return
        }

        // Start reading
        readCard(isoDep)
    }

    private fun readCard(isoDep: IsoDep) {
        val mrz = mrzData ?: return

        // Cancel any existing reading
        readingJob?.cancel()

        // Show reading UI
        stopPulseAnimation()
        progressBarReading.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE

        readingJob = coroutineScope.launch {
            try {
                val cccdData = cardReader.readCard(
                    isoDep = isoDep,
                    mrzData = mrz,
                    config = config,
                    onProgress = { status ->
                        launch(Dispatchers.Main) {
                            updateProgress(status)
                        }
                    }
                )

                // Success
                withContext(Dispatchers.Main) {
                    onReadSuccess(cccdData)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Card reading failed", e)
                withContext(Dispatchers.Main) {
                    onReadError(e)
                }
            }
        }
    }

    private fun updateProgress(status: ReadingStatus) {
        tvStatus.text = status.description
        tvProgress.text = status.description

        val progress = when (status) {
            ReadingStatus.CONNECTING -> 10
            ReadingStatus.AUTHENTICATING -> 25
            ReadingStatus.READING_DG1 -> 40
            ReadingStatus.READING_DG2 -> 60
            ReadingStatus.READING_DG13 -> 80
            ReadingStatus.VERIFYING -> 90
            ReadingStatus.COMPLETED -> 100
            ReadingStatus.ERROR -> 0
        }
        progressBarReading.progress = progress

        // Notify callback
        CCCDReader.getCallback()?.onNfcProgress(status)
    }

    private fun onReadSuccess(cccdData: CCCDData) {
        tvStatus.text = getString(R.string.vncccd_nfc_success)
        tvProgress.text = getString(R.string.vncccd_nfc_success)
        ivNfcIcon.setImageResource(R.drawable.ic_check_circle)
        progressBarReading.progress = 100

        // Vibrate
        if (config.enableVibration) {
            val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            vibrator?.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        }

        // Notify callback
        CCCDReader.getCallback()?.onSuccess(cccdData)

        // Return result
        val resultIntent = Intent().apply {
            putExtra(CCCDReader.EXTRA_RESULT_CCCD, cccdData)
        }
        setResult(Activity.RESULT_OK, resultIntent)

        // Delay finish
        ivNfcIcon.postDelayed({ finish() }, 1000)
    }

    private fun onReadError(error: Exception) {
        val cccdError = when {
            error.message?.contains("authentication", ignoreCase = true) == true ->
                CCCDError.AuthenticationFailed(error)

            error.message?.contains("tag was lost", ignoreCase = true) == true ||
                    error.message?.contains("transceive", ignoreCase = true) == true ->
                CCCDError.ConnectionLost(error)

            else -> CCCDError.Unknown(error)
        }

        tvStatus.text = cccdError.message
        tvProgress.text = getString(R.string.vncccd_nfc_error_retry)

        // Reset to waiting state
        ivNfcIcon.postDelayed({
            resetToWaiting()
        }, 2000)

        // Notify callback
        CCCDReader.getCallback()?.onError(cccdError)
    }

    private fun resetToWaiting() {
        tvStatus.text = getString(R.string.vncccd_nfc_waiting)
        tvInstruction.text = getString(R.string.vncccd_nfc_instruction)
        progressBarReading.visibility = View.GONE
        tvProgress.visibility = View.GONE
        progressBarReading.progress = 0
        ivNfcIcon.setImageResource(R.drawable.ic_nfc_scan)
        startPulseAnimation()
    }

    override fun onDestroy() {
        super.onDestroy()
        readingJob?.cancel()
        coroutineScope.cancel()
        cardReader.close()
    }
}
