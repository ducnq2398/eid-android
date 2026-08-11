package com.vncccd.sdk.mrz

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vncccd.sdk.models.MrzData
import java.util.concurrent.ExecutorService
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Thông tin chất lượng khung hình, dùng để hướng dẫn người dùng và bật đèn tự động.
 */
data class MrzQualityHint(
    /** Số dòng text ML Kit đọc được trong vùng quét */
    val rowsDetected: Int,

    /** Độ sáng trung bình 0-255, hoặc -1 nếu không đo được */
    val meanLuminance: Int,

    /** Đã tìm thấy bộ 3 dòng trông giống MRZ chưa */
    val mrzCandidateFound: Boolean
) {
    /** Khung hình tối tới mức OCR khó đọc. */
    val tooDark: Boolean
        get() = meanLuminance in 0 until DARK_LUMINANCE_THRESHOLD

    companion object {
        const val DARK_LUMINANCE_THRESHOLD = 60
    }
}

/**
 * ImageAnalysis.Analyzer sử dụng ML Kit Text Recognition
 * để detect và parse MRZ từ camera frames.
 *
 * Pipeline:
 * 1. Cắt vùng MRZ khỏi frame ([MrzRoi]) rồi trả buffer về CameraX ngay.
 * 2. ML Kit OCR trên vùng đã cắt.
 * 3. Ghép các mảnh text thành dòng theo toạ độ ([MrzLineExtractor]).
 * 4. Chuẩn hoá ký tự theo vị trí trường và chấm điểm check digit ([MrzParser]).
 * 5. Gộp nhiều frame để triệt nhiễu ([MrzFrameAggregator]).
 *
 * Lưu ý: các callback được gọi trên worker thread, không phải main thread.
 * Caller phải tự chuyển về UI thread nếu cần cập nhật giao diện.
 */
class MrzTextAnalyzer(
    private val requiredConsecutiveFrames: Int = 3,
    private val onMrzDetected: (MrzData) -> Unit,
    private val onProcessing: ((Boolean) -> Unit)? = null,
    /** Vùng quét chuẩn hoá 0..1 trong không gian preview. null = quét cả khung hình. */
    private val roi: RectF? = null,
    private val onQualityHint: ((MrzQualityHint) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "MrzTextAnalyzer"
        private const val MRZ_LINE_LENGTH = 30

        /** Độ dài tối thiểu của line 1 / line 2 sau khi clean. */
        private const val MIN_MRZ_LINE_LENGTH = 25

        /**
         * Line 3 chỉ chứa họ tên và không tham gia dẫn xuất khoá BAC, nên chấp
         * nhận ngắn hơn nhiều - ML Kit hay bỏ bớt đuôi '<<<<' dài.
         */
        private const val MIN_NAME_LINE_LENGTH = 8

        /** Giới hạn số dòng đưa vào bước ghép, chặn trường hợp frame quá nhiễu. */
        private const val MAX_ROWS_CONSIDERED = 8

        /** ROI nhỏ hơn ngưỡng này thì không đáng cắt, dùng nguyên frame. */
        private const val MIN_ROI_PX = 32

        /** Điểm thưởng khi 3 dòng nằm đúng thứ tự vật lý trên ảnh. */
        private const val ORDER_BONUS = 10
    }

    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Executor riêng cho callback OCR, giữ toàn bộ khâu parse khỏi main thread.
     *
     * Dùng [ThreadPoolExecutor.DiscardPolicy] thay vì policy mặc định: sau khi
     * [close] shutdown executor, ML Kit vẫn có thể cố gửi callback của tác vụ
     * đang dở. Policy mặc định sẽ ném RejectedExecutionException từ trong lòng
     * ML Kit; ở đây callback muộn đó chỉ đơn giản bị bỏ qua.
     */
    private val callbackExecutor: ExecutorService = ThreadPoolExecutor(
        1, 1, 0L, TimeUnit.MILLISECONDS,
        LinkedBlockingQueue(),
        ThreadPoolExecutor.DiscardPolicy()
    )

    private val aggregator = MrzFrameAggregator(requiredConsecutiveFrames)

    private val isProcessing = AtomicBoolean(false)
    private val isCompleted = AtomicBoolean(false)
    private val isClosed = AtomicBoolean(false)

    /** Buffer NV21 tái sử dụng. Chỉ chạm tới từ analyze thread. */
    private var yuvBuffer: ByteArray? = null

    override fun analyze(imageProxy: ImageProxy) {
        // STRATEGY_KEEP_ONLY_LATEST đã chặn backpressure ở tầng CameraX, nhưng
        // OCR chạy bất đồng bộ nên vẫn cần cờ riêng để không chồng 2 lần nhận dạng.
        if (isCompleted.get() || isClosed.get() || !isProcessing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val prepared = try {
            prepareInput(imageProxy)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to prepare frame", e)
            null
        }

        if (prepared == null) {
            isProcessing.set(false)
            imageProxy.close()
            return
        }

        // Đã copy xong vùng cần thiết thì trả buffer cho CameraX ngay, không giữ
        // suốt thời gian OCR - camera có thể tiếp tục sản xuất frame mới.
        if (prepared.ownsPixels) imageProxy.close()
        onProcessing?.invoke(true)

        try {
            textRecognizer.process(prepared.image)
                .addOnSuccessListener(callbackExecutor) { visionText ->
                    handleRecognizedText(visionText, prepared.meanLuminance)
                }
                .addOnFailureListener(callbackExecutor) { e ->
                    Log.w(TAG, "Text recognition failed", e)
                }
                .addOnCompleteListener(callbackExecutor) {
                    if (!prepared.ownsPixels) imageProxy.close()
                    onProcessing?.invoke(false)
                    isProcessing.set(false)
                }
        } catch (e: Exception) {
            // Chạy đua với close(): recognizer có thể đã bị đóng ngay sau kiểm tra
            // isClosed ở đầu hàm. Không được để kẹt cờ isProcessing.
            Log.w(TAG, "Unable to submit frame for recognition", e)
            if (!prepared.ownsPixels) imageProxy.close()
            onProcessing?.invoke(false)
            isProcessing.set(false)
        }
    }

    /**
     * Ảnh đã sẵn sàng cho ML Kit.
     *
     * @property ownsPixels true khi pixel đã được copy ra buffer riêng, tức là
     *   [ImageProxy] có thể đóng ngay lập tức.
     */
    private class PreparedFrame(
        val image: InputImage,
        val ownsPixels: Boolean,
        val meanLuminance: Int
    )

    @SuppressLint("UnsafeOptInUsageError")
    private fun prepareInput(imageProxy: ImageProxy): PreparedFrame? {
        val rotation = imageProxy.imageInfo.rotationDegrees

        if (roi != null && imageProxy.format == ImageFormat.YUV_420_888) {
            val rect = MrzRoi.toImageRect(
                normalized = roi,
                viewport = imageProxy.cropRect,
                imageWidth = imageProxy.width,
                imageHeight = imageProxy.height,
                rotationDegrees = rotation
            )
            if (rect.width() >= MIN_ROI_PX && rect.height() >= MIN_ROI_PX) {
                val buffer = MrzRoi.cropLuminanceToNv21(imageProxy, rect, yuvBuffer)
                yuvBuffer = buffer
                return PreparedFrame(
                    image = InputImage.fromByteArray(
                        buffer,
                        rect.width(),
                        rect.height(),
                        rotation,
                        InputImage.IMAGE_FORMAT_NV21
                    ),
                    ownsPixels = true,
                    meanLuminance = MrzRoi.meanLuminance(buffer, rect.width(), rect.height())
                )
            }
        }

        val mediaImage = imageProxy.image ?: return null
        return PreparedFrame(
            image = InputImage.fromMediaImage(mediaImage, rotation),
            ownsPixels = false,
            meanLuminance = -1
        )
    }

    private fun handleRecognizedText(visionText: Text, meanLuminance: Int) {
        if (isCompleted.get()) return

        val rows = MrzLineExtractor.rowsFrom(visionText)
        val candidate = findMrzLines(rows)

        onQualityHint?.invoke(
            MrzQualityHint(
                rowsDetected = rows.size,
                meanLuminance = meanLuminance,
                mrzCandidateFound = candidate != null
            )
        )

        if (candidate == null) return

        val mrzData = aggregator.submit(candidate) ?: return
        if (isCompleted.compareAndSet(false, true)) {
            Log.d(TAG, "MRZ validated after ${aggregator.framesSeen} frames: ${mrzData.fullDocumentNumber}")
            onMrzDetected(mrzData)
        }
    }

    /**
     * Chọn bộ 3 dòng MRZ tốt nhất trong các dòng text đọc được.
     *
     * Khác với cách cũ (lấy bộ đầu tiên khớp về mặt hình thức), ở đây mọi tổ hợp
     * khả dĩ đều được chấm điểm bằng check digit rồi mới chọn bộ điểm cao nhất.
     * Một dòng bị OCR nuốt hoặc chèn thêm dòng rác vào giữa không còn làm hỏng
     * kết quả nữa.
     */
    private fun findMrzLines(rows: List<String>): List<String>? {
        // Giữ các dòng dài nhất nhưng bảo toàn thứ tự trên ảnh.
        val usable = rows
            .withIndex()
            .filter { it.value.length >= MIN_NAME_LINE_LENGTH }
            .sortedByDescending { it.value.length }
            .take(MAX_ROWS_CONSIDERED)
            .sortedBy { it.index }
            .map { it.value }

        if (usable.size < 3) return null

        val line1Candidates = usable.map { cleanAndValidateLine(it, 1) }
        val line2Candidates = usable.map { cleanAndValidateLine(it, 2) }
        val line3Candidates = usable.map { cleanAndValidateLine(it, 3) }

        var best: List<String>? = null
        var bestScore = -1

        for (i in usable.indices) {
            val line1 = line1Candidates[i] ?: continue
            if (!isVietnamCCCD(line1)) continue

            for (j in usable.indices) {
                if (j == i) continue
                val line2 = line2Candidates[j] ?: continue

                for (k in usable.indices) {
                    if (k == i || k == j) continue
                    val line3 = line3Candidates[k] ?: continue

                    val candidate = listOf(line1, line2, line3)
                    val validation = MrzParser.validate(candidate)
                    val score = validation.score + if (i < j && j < k) ORDER_BONUS else 0

                    if (score > bestScore) {
                        bestScore = score
                        best = candidate
                    }
                    // Mọi tín hiệu đều khớp thì không cần dò tiếp.
                    if (validation.isStrong && i < j && j < k) return candidate
                }
            }
        }

        return best
    }

    /**
     * Clean và validate một dòng MRZ theo vai trò của nó (line 1 / 2 / 3).
     */
    private fun cleanAndValidateLine(rawLine: String, lineNumber: Int): String? {
        val minLength = if (lineNumber == 3) MIN_NAME_LINE_LENGTH else MIN_MRZ_LINE_LENGTH

        var cleaned = MrzParser.cleanOcrText(rawLine)
        if (cleaned.length < minLength) return null

        cleaned = MrzParser.smartCleanMrzLine(cleaned, lineNumber)
        cleaned = when {
            cleaned.length > MRZ_LINE_LENGTH -> cleaned.substring(0, MRZ_LINE_LENGTH)
            cleaned.length < MRZ_LINE_LENGTH -> cleaned.padEnd(MRZ_LINE_LENGTH, '<')
            else -> cleaned
        }

        return when (lineNumber) {
            // Document type phải là I/A/C theo ICAO 9303.
            1 -> if (cleaned[0] == 'I' || cleaned[0] == 'A' || cleaned[0] == 'C') cleaned else null

            // 6 ký tự đầu là ngày sinh, phải toàn số.
            2 -> if (cleaned.substring(0, 6).all { it.isDigit() }) cleaned else null

            3 -> cleaned

            else -> null
        }
    }

    /**
     * Issuing state của CCCD Việt Nam là 'VNM'. Cho phép sai 1 ký tự vì đây là
     * vùng chữ dễ bị OCR nhầm ('VNM' → 'WNM', 'VMM'...) mà nếu loại thẳng thì
     * cả frame bị bỏ phí.
     */
    private fun isVietnamCCCD(line1: String): Boolean {
        if (line1.length < 5) return false
        val state = line1.substring(2, 5)
        var matches = 0
        for (i in 0 until 3) if (state[i] == "VNM"[i]) matches++
        return matches >= 2
    }

    /**
     * Reset analyzer state.
     */
    fun reset() {
        isCompleted.set(false)
        isProcessing.set(false)
        aggregator.reset()
    }

    /**
     * Release resources.
     */
    fun close() {
        if (!isClosed.compareAndSet(false, true)) return
        // Chờ tác vụ OCR đang chạy kết thúc trước khi đóng recognizer.
        callbackExecutor.execute {
            try {
                textRecognizer.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing recognizer", e)
            }
        }
        callbackExecutor.shutdown()
    }
}
