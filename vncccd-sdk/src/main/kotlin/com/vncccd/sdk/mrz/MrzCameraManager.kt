package com.vncccd.sdk.mrz

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Quản lý CameraX cho MRZ scanning.
 * Cung cấp camera preview và image analysis pipeline.
 */
class MrzCameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analyzer: MrzTextAnalyzer
) {
    companion object {
        private const val TAG = "MrzCameraManager"
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isTorchEnabled = false

    /**
     * Khởi tạo và start camera.
     */
    fun startCamera(onError: ((Exception) -> Unit)? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Preview use case
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Image analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                // Camera selector - back camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                // Unbind all use cases before rebinding
                provider.unbindAll()

                // Bind use cases to lifecycle
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )

                Log.d(TAG, "Camera started successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    /**
     * Toggle torch/flash.
     */
    fun toggleTorch(): Boolean {
        val camera = cameraProvider?.let {
            try {
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                it.bindToLifecycle(lifecycleOwner, cameraSelector)
            } catch (e: Exception) {
                null
            }
        }

        camera?.cameraControl?.let {
            isTorchEnabled = !isTorchEnabled
            it.enableTorch(isTorchEnabled)
        }

        return isTorchEnabled
    }

    /**
     * Stop camera và release resources.
     */
    fun stop() {
        try {
            cameraProvider?.unbindAll()
            cameraExecutor.shutdown()
            analyzer.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
}
