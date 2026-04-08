package com.vncccd.sdk.mrz

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.vncccd.sdk.R

/**
 * Custom overlay view cho MRZ scanner.
 * Vẽ một khung scan với viền bo tròn và hiệu ứng scan line.
 */
class MrzOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /** Paint cho vùng tối bao quanh */
    private val dimPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    /** Paint cho viền khung scan */
    private val borderPaint = Paint().apply {
        color = Color.parseColor("#FF6B35")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    /** Paint cho corner brackets */
    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#FF6B35")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    /** Paint cho scan line */
    private val scanLinePaint = Paint().apply {
        color = Color.parseColor("#80FF6B35")
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    /** Paint cho text hướng dẫn */
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    /** Paint cho trạng thái thành công */
    private val successPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
    }

    /** Vùng scan */
    private var scanRect = RectF()

    /** Chiều dài corner bracket */
    private var cornerLength = 40f

    /** Vị trí scan line (0.0 - 1.0) */
    private var scanLineProgress = 0f

    /** Trạng thái: đang scan hay đã thành công */
    private var isSuccess = false

    /** Text hướng dẫn */
    private var instructionText = ""

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateScanRect()
    }

    private fun calculateScanRect() {
        val w = width.toFloat()
        val h = height.toFloat()
        val padding = w * 0.08f

        // MRZ area is at the bottom of the card, ID card ratio ~= 1.585:1
        val scanWidth = w - padding * 2
        val scanHeight = scanWidth / 3.5f // Narrower for MRZ area

        val left = padding
        val top = h * 0.4f // Position in middle-lower of screen
        val right = left + scanWidth
        val bottom = top + scanHeight

        scanRect = RectF(left, top, right, bottom)
        cornerLength = scanWidth * 0.06f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Draw dimmed background except scan area
        drawDimmedBackground(canvas, w, h)

        // Draw scan frame
        if (isSuccess) {
            drawSuccessFrame(canvas)
        } else {
            drawScanFrame(canvas)
            drawScanLine(canvas)
        }

        // Draw instruction text
        drawInstructionText(canvas)
    }

    private fun drawDimmedBackground(canvas: Canvas, w: Float, h: Float) {
        // Top
        canvas.drawRect(0f, 0f, w, scanRect.top, dimPaint)
        // Bottom
        canvas.drawRect(0f, scanRect.bottom, w, h, dimPaint)
        // Left
        canvas.drawRect(0f, scanRect.top, scanRect.left, scanRect.bottom, dimPaint)
        // Right
        canvas.drawRect(scanRect.right, scanRect.top, w, scanRect.bottom, dimPaint)
    }

    private fun drawScanFrame(canvas: Canvas) {
        // Draw border
        canvas.drawRoundRect(scanRect, 12f, 12f, borderPaint)

        // Draw corner brackets
        drawCornerBrackets(canvas, cornerPaint)
    }

    private fun drawSuccessFrame(canvas: Canvas) {
        canvas.drawRoundRect(scanRect, 12f, 12f, successPaint)
        drawCornerBrackets(canvas, successPaint)
    }

    private fun drawCornerBrackets(canvas: Canvas, paint: Paint) {
        val r = scanRect

        // Top-left corner
        canvas.drawLine(r.left, r.top + cornerLength, r.left, r.top, paint)
        canvas.drawLine(r.left, r.top, r.left + cornerLength, r.top, paint)

        // Top-right corner
        canvas.drawLine(r.right - cornerLength, r.top, r.right, r.top, paint)
        canvas.drawLine(r.right, r.top, r.right, r.top + cornerLength, paint)

        // Bottom-left corner
        canvas.drawLine(r.left, r.bottom - cornerLength, r.left, r.bottom, paint)
        canvas.drawLine(r.left, r.bottom, r.left + cornerLength, r.bottom, paint)

        // Bottom-right corner
        canvas.drawLine(r.right - cornerLength, r.bottom, r.right, r.bottom, paint)
        canvas.drawLine(r.right, r.bottom - cornerLength, r.right, r.bottom, paint)
    }

    private fun drawScanLine(canvas: Canvas) {
        val y = scanRect.top + scanRect.height() * scanLineProgress
        canvas.drawLine(scanRect.left + 10, y, scanRect.right - 10, y, scanLinePaint)
    }

    private fun drawInstructionText(canvas: Canvas) {
        val textY = scanRect.bottom + 80f
        canvas.drawText(instructionText, width / 2f, textY, textPaint)
    }

    /**
     * Cập nhật vị trí scan line (animated externally).
     */
    fun setScanLineProgress(progress: Float) {
        scanLineProgress = progress
        invalidate()
    }

    /**
     * Set trạng thái thành công.
     */
    fun setSuccess(success: Boolean) {
        isSuccess = success
        if (success) {
            instructionText = "Đã nhận diện MRZ thành công!"
            cornerPaint.color = Color.parseColor("#4CAF50")
        } else {
            instructionText = ""
            cornerPaint.color = Color.parseColor("#FF6B35")
        }
        invalidate()
    }

    /**
     * Set text hướng dẫn.
     */
    fun setInstruction(text: String) {
        instructionText = text
        invalidate()
    }

    /**
     * Lấy vùng scan rect.
     */
    fun getScanRect(): RectF = RectF(scanRect)
}
