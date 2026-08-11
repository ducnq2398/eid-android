package com.vncccd.sdk.mrz

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.util.Size
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.doOnLayout
import com.vncccd.sdk.CCCDConfig
import com.vncccd.sdk.CCCDReader
import com.vncccd.sdk.R
import com.vncccd.sdk.models.CCCDError
import com.vncccd.sdk.models.MrzData

/**
 * Activity quét MRZ trên mặt sau thẻ CCCD.
 *
 * Features:
 * - Camera preview fullscreen
 * - MRZ overlay với hướng dẫn
 * - Auto-detect MRZ với multi-frame validation
 * - Hiệu ứng animation khi scanning và thành công
 * - Flash/torch toggle, tự bật khi thiếu sáng
 * - Chạm để lấy nét
 */
class MrzScannerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MrzScannerActivity"
        private const val CAMERA_PERMISSION_CODE = 1001

        /** Số frame tối liên tiếp trước khi tự bật đèn. */
        private const val DARK_FRAMES_BEFORE_TORCH = 6
    }

    // Views
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: MrzOverlayView
    private lateinit var btnClose: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var tvInstruction: TextView

    // Camera
    private lateinit var cameraManager: MrzCameraManager
    private lateinit var textAnalyzer: MrzTextAnalyzer

    // Config
    private var config: CCCDConfig = CCCDConfig.defaultConfig()

    // Animation
    private var scanLineAnimator: ValueAnimator? = null
    private var hasCompleted = false
    private var hasStarted = false

    /** Người dùng đã tự bấm nút đèn thì thôi không tự động can thiệp nữa. */
    private var torchControlledByUser = false
    private var darkFrameStreak = 0

    private val timeoutRunnable = Runnable { onScanTimeout() }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mrz_scanner)

        // Get config
        config = intent.getSerializableExtra(CCCDReader.EXTRA_CONFIG) as? CCCDConfig
            ?: CCCDConfig.defaultConfig()

        initViews()
        setupListeners()

        if (hasCameraPermission()) {
            startScanning()
        } else {
            requestCameraPermission()
        }
    }

    private fun initViews() {
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        btnClose = findViewById(R.id.btnClose)
        btnFlash = findViewById(R.id.btnFlash)
        tvInstruction = findViewById(R.id.tvInstruction)

        tvInstruction.text = getString(R.string.vncccd_mrz_instruction)
        btnFlash.visibility = View.GONE
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        btnFlash.setOnClickListener {
            if (!::cameraManager.isInitialized) return@setOnClickListener
            torchControlledByUser = true
            updateFlashIcon(cameraManager.toggleTorch())
        }

        // Chạm vào preview để lấy nét lại - cứu được các trường hợp auto focus
        // bám nhầm vào nền phía sau thẻ.
        previewView.setOnTouchListener { view, event ->
            if (event.actionMasked == MotionEvent.ACTION_UP) {
                view.performClick()
                if (::cameraManager.isInitialized) {
                    cameraManager.focusAt(event.x, event.y)
                }
            }
            true
        }
    }

    private fun startScanning() {
        if (hasStarted) return
        hasStarted = true

        // ROI chỉ tính được sau khi overlay đã được đo đạc.
        overlayView.doOnLayout { setupCamera() }
    }

    private fun setupCamera() {
        if (hasCompleted || isFinishing) return

        val roi = if (config.mrzCropToScanArea) overlayView.getNormalizedScanRect() else null
        Log.d(TAG, "Scan ROI (normalized): $roi")

        textAnalyzer = MrzTextAnalyzer(
            requiredConsecutiveFrames = config.mrzConsecutiveFrames,
            onMrzDetected = { mrzData ->
                runOnUiThread { onMrzDetected(mrzData) }
            },
            roi = roi,
            onQualityHint = { hint ->
                runOnUiThread { onQualityHint(hint) }
            }
        )

        cameraManager = MrzCameraManager(
            lifecycleOwner = this,
            previewView = previewView,
            analyzer = textAnalyzer,
            targetAnalysisSize = Size(config.mrzAnalysisWidth, config.mrzAnalysisHeight)
        )

        cameraManager.startCamera(
            onError = { error ->
                Log.e(TAG, "Camera error", error)
                Toast.makeText(
                    this,
                    getString(R.string.vncccd_camera_error),
                    Toast.LENGTH_SHORT
                ).show()
                CCCDReader.dispatchMrzError(CCCDError.CameraNotAvailable())
            },
            onReady = {
                // Ép đo sáng và lấy nét vào giữa khung quét thay vì tâm khung hình.
                overlayView.getNormalizedScanRect(0f)?.let { rect ->
                    cameraManager.focusOnScanArea(rect.centerX(), rect.centerY())
                }
                btnFlash.visibility =
                    if (cameraManager.hasFlashUnit()) View.VISIBLE else View.GONE
            }
        )

        startScanLineAnimation()

        if (config.mrzTimeoutMs > 0) {
            overlayView.postDelayed(timeoutRunnable, config.mrzTimeoutMs)
        }
    }

    /**
     * Phản hồi chất lượng khung hình theo thời gian thực.
     *
     * Người dùng cần biết máy có "thấy" thẻ hay không, và khi thiếu sáng thì
     * bật đèn giúp luôn thay vì để họ tự loay hoay.
     */
    private fun onQualityHint(hint: MrzQualityHint) {
        if (hasCompleted) return

        overlayView.setDetecting(hint.mrzCandidateFound)

        if (!config.mrzAutoTorch || torchControlledByUser || !::cameraManager.isInitialized) return
        if (!cameraManager.hasFlashUnit()) return

        if (hint.tooDark && !hint.mrzCandidateFound) {
            darkFrameStreak++
            if (darkFrameStreak >= DARK_FRAMES_BEFORE_TORCH && !cameraManager.isTorchOn()) {
                updateFlashIcon(cameraManager.setTorch(true))
            }
        } else {
            darkFrameStreak = 0
        }
    }

    private fun updateFlashIcon(enabled: Boolean) {
        btnFlash.setImageResource(
            if (enabled) R.drawable.ic_flash_on else R.drawable.ic_flash_off
        )
    }

    /**
     * Called when MRZ is successfully detected and validated.
     */
    private fun onMrzDetected(mrzData: MrzData) {
        if (hasCompleted) return
        hasCompleted = true
        Log.d(TAG, "MRZ detected: ${mrzData.fullDocumentNumber}")

        overlayView.removeCallbacks(timeoutRunnable)

        // Stop scanning animation
        stopScanLineAnimation()

        // Show success state
        overlayView.setSuccess(true)
        tvInstruction.text = getString(R.string.vncccd_mrz_success)

        // Tắt đèn ngay, không để sáng suốt animation kết thúc.
        if (::cameraManager.isInitialized) {
            cameraManager.setTorch(false)
        }

        // Vibrate
        if (config.enableVibration) {
            vibrate()
        }

        // Notify callback
        CCCDReader.dispatchMrzScanned(mrzData)

        // Return result
        val resultIntent = Intent().apply {
            putExtra(CCCDReader.EXTRA_RESULT_MRZ, mrzData)
        }
        setResult(Activity.RESULT_OK, resultIntent)

        // Delay finish to show success state
        overlayView.postDelayed({
            finish()
        }, 800)
    }

    private fun onScanTimeout() {
        if (hasCompleted || isFinishing) return
        hasCompleted = true

        stopScanLineAnimation()
        Toast.makeText(this, getString(R.string.vncccd_mrz_failed), Toast.LENGTH_LONG).show()
        CCCDReader.dispatchMrzError(CCCDError.Timeout())
        setResult(Activity.RESULT_CANCELED)
        finish()
    }

    private fun startScanLineAnimation() {
        scanLineAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000L
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            addUpdateListener { animator ->
                overlayView.setScanLineProgress(animator.animatedValue as Float)
            }
            start()
        }
    }

    private fun stopScanLineAnimation() {
        scanLineAnimator?.cancel()
        scanLineAnimator = null
    }

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    // Camera permission handling
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.vncccd_camera_permission_denied),
                    Toast.LENGTH_LONG
                ).show()
                CCCDReader.dispatchMrzError(CCCDError.Cancelled())
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayView.removeCallbacks(timeoutRunnable)
        stopScanLineAnimation()
        if (::cameraManager.isInitialized) {
            cameraManager.stop()
        }
    }
}
