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
import android.view.animation.LinearInterpolator
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vncccd.sdk.CCCDConfig
import com.vncccd.sdk.CCCDReader
import com.vncccd.sdk.R
import com.vncccd.sdk.models.MrzData

/**
 * Activity quét MRZ trên mặt sau thẻ CCCD.
 *
 * Features:
 * - Camera preview fullscreen
 * - MRZ overlay với hướng dẫn
 * - Auto-detect MRZ với multi-frame validation
 * - Hiệu ứng animation khi scanning và thành công
 * - Flash/torch toggle
 */
class MrzScannerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MrzScannerActivity"
        private const val CAMERA_PERMISSION_CODE = 1001
    }

    // Views
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: MrzOverlayView
    private lateinit var btnClose: ImageButton
    private lateinit var tvInstruction: TextView
    // Camera
    private lateinit var cameraManager: MrzCameraManager
    private lateinit var textAnalyzer: MrzTextAnalyzer

    // Config
    private var config: CCCDConfig = CCCDConfig.defaultConfig()

    // Animation
    private var scanLineAnimator: ValueAnimator? = null

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
        tvInstruction = findViewById(R.id.tvInstruction)

        tvInstruction.text = getString(R.string.vncccd_mrz_instruction)
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }


    }

    private fun startScanning() {
        textAnalyzer = MrzTextAnalyzer(
            requiredConsecutiveFrames = config.mrzConsecutiveFrames,
            onMrzDetected = { mrzData ->
                runOnUiThread { onMrzDetected(mrzData) }
            },
        )

        cameraManager = MrzCameraManager(this, previewView, textAnalyzer)
        cameraManager.startCamera { error ->
            Log.e(TAG, "Camera error", error)
            Toast.makeText(this, getString(R.string.vncccd_camera_error), Toast.LENGTH_SHORT).show()
        }

        startScanLineAnimation()
    }

    /**
     * Called when MRZ is successfully detected and validated.
     */
    private fun onMrzDetected(mrzData: MrzData) {
        Log.d(TAG, "MRZ detected: ${mrzData.fullDocumentNumber}")

        // Stop scanning animation
        stopScanLineAnimation()

        // Show success state
        overlayView.setSuccess(true)
        tvInstruction.text = getString(R.string.vncccd_mrz_success)

        // Vibrate
        if (config.enableVibration) {
            vibrate()
        }

        // Notify callback
        CCCDReader.getCallback()?.onMrzScanned(mrzData)

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
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScanLineAnimation()
        if (::cameraManager.isInitialized) {
            cameraManager.stop()
        }
    }
}
