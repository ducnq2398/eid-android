package com.vncccd.sdk.mrz

import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Quản lý CameraX cho MRZ scanning.
 * Cung cấp camera preview và image analysis pipeline.
 */
class MrzCameraManager(
    private val lifecycleOwner: LifecycleOwner,
    private val previewView: PreviewView,
    private val analyzer: MrzTextAnalyzer,
    /**
     * Độ phân giải mong muốn cho luồng phân tích.
     *
     * Mặc định của CameraX chỉ là 640×480. Ở độ phân giải đó, một dòng MRZ 30
     * ký tự trải trên bề ngang thẻ chỉ còn khoảng 5-7 pixel mỗi ký tự - dưới
     * ngưỡng OCR đọc tin cậy, nên tỉ lệ đọc sai rất cao. 1280×720 đưa mỗi ký
     * tự lên khoảng 12-15 pixel mà vẫn nhẹ hơn nhiều so với full sensor.
     */
    private val targetAnalysisSize: Size = Size(1280, 720)
) {
    companion object {
        private const val TAG = "MrzCameraManager"
        private const val FOCUS_AUTO_CANCEL_SECONDS = 4L
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var isTorchEnabled = false

    /**
     * Khởi tạo và start camera.
     */
    fun startCamera(onError: ((Exception) -> Unit)? = null, onReady: (() -> Unit)? = null) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(previewView.context)

        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                val resolutionSelector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            targetAnalysisSize,
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                        )
                    )
                    .build()

                // Preview use case
                val preview = Preview.Builder().build()
                preview.setSurfaceProvider(previewView.surfaceProvider)

                // Image analysis use case
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setResolutionSelector(resolutionSelector)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }

                // Camera selector - back camera
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                // Bind kèm ViewPort để ImageProxy.cropRect trùng đúng vùng preview
                // đang hiển thị. Không có nó thì khung quét vẽ trên màn hình và
                // vùng ảnh analyzer nhận được sẽ lệch nhau khi PreviewView crop
                // theo scale type.
                val useCaseGroupBuilder = UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                previewView.viewPort?.let { useCaseGroupBuilder.setViewPort(it) }

                // Unbind all use cases before rebinding
                provider.unbindAll()

                camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    useCaseGroupBuilder.build()
                )

                Log.d(TAG, "Camera started successfully")
                onReady?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                onError?.invoke(e)
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    /** Thiết bị có đèn flash không. */
    fun hasFlashUnit(): Boolean = camera?.cameraInfo?.hasFlashUnit() == true

    /** Trạng thái đèn hiện tại. */
    fun isTorchOn(): Boolean = isTorchEnabled

    /**
     * Bật/tắt đèn.
     *
     * @return trạng thái đèn sau khi đổi.
     */
    fun setTorch(enabled: Boolean): Boolean {
        val control = camera?.cameraControl ?: return isTorchEnabled
        if (!hasFlashUnit()) return false
        if (isTorchEnabled == enabled) return isTorchEnabled

        control.enableTorch(enabled)
        isTorchEnabled = enabled
        return isTorchEnabled
    }

    /**
     * Toggle torch/flash.
     */
    fun toggleTorch(): Boolean = setTorch(!isTorchEnabled)

    /**
     * Lấy nét vào một điểm trên preview (toạ độ view).
     */
    fun focusAt(x: Float, y: Float) {
        val control = camera?.cameraControl ?: return
        try {
            val point = previewView.meteringPointFactory.createPoint(x, y)
            val action = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            ).setAutoCancelDuration(FOCUS_AUTO_CANCEL_SECONDS, TimeUnit.SECONDS).build()
            control.startFocusAndMetering(action)
        } catch (e: Exception) {
            Log.w(TAG, "Focus request failed", e)
        }
    }

    /**
     * Đưa điểm lấy nét và đo sáng vào giữa khung quét.
     *
     * Mặc định CameraX đo sáng ở tâm khung hình, trong khi dải MRZ nằm lệch
     * xuống dưới. Ép metering vào đúng vùng cần đọc giúp nét và phơi sáng đúng
     * chỗ, đặc biệt khi phần còn lại của thẻ sáng hơn hẳn.
     */
    fun focusOnScanArea(normalizedCenterX: Float, normalizedCenterY: Float) {
        if (previewView.width == 0 || previewView.height == 0) return
        focusAt(previewView.width * normalizedCenterX, previewView.height * normalizedCenterY)
    }

    /**
     * Stop camera và release resources.
     */
    fun stop() {
        try {
            cameraProvider?.unbindAll()
            camera = null
            analyzer.close()
            cameraExecutor.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }
}
