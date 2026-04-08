package com.vncccd.sdk.mrz

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vncccd.sdk.models.MrzData

/**
 * ImageAnalysis.Analyzer sử dụng ML Kit Text Recognition
 * để detect và parse MRZ từ camera frames.
 *
 * Multi-frame validation: chỉ chấp nhận kết quả khi N frames liên tiếp
 * cho kết quả giống nhau (tránh false positives từ OCR).
 */
class MrzTextAnalyzer(
    private val requiredConsecutiveFrames: Int = 3,
    private val onMrzDetected: (MrzData) -> Unit,
    private val onProcessing: ((Boolean) -> Unit)? = null
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "MrzTextAnalyzer"
        private const val MRZ_LINE_LENGTH = 30
        private const val MIN_MRZ_LINE_LENGTH = 25 // Allow some tolerance for OCR
    }

    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private var lastMrzResult: String? = null
    private var consecutiveCount = 0
    private var isProcessing = false
    private var isCompleted = false

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (isCompleted || isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        isProcessing = true
        onProcessing?.invoke(true)

        val inputImage = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees
        )

        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                processMrzText(visionText.text)
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Text recognition failed", e)
            }
            .addOnCompleteListener {
                isProcessing = false
                onProcessing?.invoke(false)
                imageProxy.close()
            }
    }

    /**
     * Process recognized text and try to find MRZ lines.
     */
    private fun processMrzText(fullText: String) {
        if (isCompleted) return

        val lines = fullText.split("\n")
            .map { it.trim() }
            .filter { it.length >= MIN_MRZ_LINE_LENGTH }

        // Try to find 3 consecutive MRZ lines
        val mrzLines = findMrzLines(lines)
        if (mrzLines != null) {
            val mrzString = mrzLines.joinToString("\n")

            // Multi-frame validation
            if (mrzString == lastMrzResult) {
                consecutiveCount++
                Log.d(TAG, "MRZ match count: $consecutiveCount / $requiredConsecutiveFrames")

                if (consecutiveCount >= requiredConsecutiveFrames) {
                    // Parse and validate
                    val mrzData = MrzParser.parse(mrzLines)
                    if (mrzData != null) {
                        isCompleted = true
                        Log.d(TAG, "MRZ validated: ${mrzData.fullDocumentNumber}")
                        onMrzDetected(mrzData)
                    } else {
                        // Parse failed, reset
                        Log.w(TAG, "MRZ parse failed, resetting")
                        consecutiveCount = 0
                        lastMrzResult = null
                    }
                }
            } else {
                // New MRZ detected, reset counter
                lastMrzResult = mrzString
                consecutiveCount = 1
                Log.d(TAG, "New MRZ candidate detected")
            }
        }
    }

    /**
     * Tìm 3 dòng MRZ liên tiếp trong danh sách text lines.
     */
    private fun findMrzLines(lines: List<String>): List<String>? {
        for (i in 0 until lines.size - 2) {
            val line1 = cleanAndValidateLine(lines[i], 1)
            val line2 = cleanAndValidateLine(lines[i + 1], 2)
            val line3 = cleanAndValidateLine(lines[i + 2], 3)

            if (line1 != null && line2 != null && line3 != null) {
                // Validate it's a Vietnam CCCD
                if (isVietnamCCCD(line1)) {
                    return listOf(line1, line2, line3)
                }
            }
        }

        // Try non-consecutive approach: find Line 1, then best matches for Line 2, Line 3
        for (line in lines) {
            val cleaned1 = cleanAndValidateLine(line, 1) ?: continue
            if (!isVietnamCCCD(cleaned1)) continue

            val remaining = lines.filter { it != line }
            for (l2 in remaining) {
                val cleaned2 = cleanAndValidateLine(l2, 2) ?: continue
                val remaining2 = remaining.filter { it != l2 }
                for (l3 in remaining2) {
                    val cleaned3 = cleanAndValidateLine(l3, 3) ?: continue
                    return listOf(cleaned1, cleaned2, cleaned3)
                }
            }
        }

        return null
    }

    /**
     * Clean và validate một dòng MRZ.
     */
    private fun cleanAndValidateLine(rawLine: String, lineNumber: Int): String? {
        var cleaned = MrzParser.cleanOcrText(rawLine)
        cleaned = MrzParser.smartCleanMrzLine(cleaned, lineNumber)

        // Pad or trim to 30 chars
        cleaned = when {
            cleaned.length > MRZ_LINE_LENGTH -> cleaned.substring(0, MRZ_LINE_LENGTH)
            cleaned.length < MIN_MRZ_LINE_LENGTH -> return null
            cleaned.length < MRZ_LINE_LENGTH -> cleaned.padEnd(MRZ_LINE_LENGTH, '<')
            else -> cleaned
        }

        // Basic validation per line
        return when (lineNumber) {
            1 -> if (cleaned.length >= MRZ_LINE_LENGTH &&
                (cleaned.startsWith("I") || cleaned.startsWith("A") || cleaned.startsWith("C"))
            ) cleaned else null

            2 -> if (cleaned.length >= MRZ_LINE_LENGTH &&
                cleaned.substring(0, 6).all { it.isDigit() || it == '<' }
            ) cleaned else null

            3 -> if (cleaned.length >= MIN_MRZ_LINE_LENGTH) cleaned else null

            else -> null
        }
    }

    private fun isVietnamCCCD(line1: String): Boolean {
        return line1.length >= 5 &&
                (line1.substring(0, 5) == "I<VNM" ||
                        line1.substring(0, 5) == "IDVNM" ||
                        line1.substring(0, 5) == "I0VNM" ||
                        line1.substring(0, 5) == "ICVNM" ||
                        line1.substring(2, 5) == "VNM")
    }

    /**
     * Reset analyzer state.
     */
    fun reset() {
        isCompleted = false
        isProcessing = false
        lastMrzResult = null
        consecutiveCount = 0
    }

    /**
     * Release resources.
     */
    fun close() {
        textRecognizer.close()
    }
}
