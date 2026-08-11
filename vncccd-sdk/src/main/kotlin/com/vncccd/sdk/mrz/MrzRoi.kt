package com.vncccd.sdk.mrz

import android.graphics.Rect
import android.graphics.RectF
import androidx.camera.core.ImageProxy
import kotlin.math.roundToInt

/**
 * Cắt vùng quan tâm (ROI) của khung hình trước khi đưa vào ML Kit.
 *
 * Đây là điểm tối ưu lớn nhất của pipeline. Frame gốc 1280×720 chứa toàn bộ
 * mặt sau thẻ: chữ tiếng Việt có dấu, số seri, hoa văn nền... ML Kit phải
 * nhận dạng hết rồi ta mới lọc ra 3 dòng MRZ. Cắt sẵn còn dải MRZ giúp:
 *
 * - Giảm số pixel ML Kit phải xử lý khoảng 5-8 lần → tăng số frame quét được.
 * - Loại sạch text nhiễu → bước ghép dòng không còn phải dò trong hàng chục
 *   dòng rác, và không còn cơ hội ghép nhầm.
 * - Trả buffer về cho CameraX ngay sau khi copy, thay vì giữ suốt quá trình OCR.
 */
internal object MrzRoi {

    /**
     * Ánh xạ ROI (toạ độ chuẩn hoá 0..1 trong không gian preview đã xoay đúng
     * chiều) sang toạ độ pixel của ảnh phân tích gốc (chưa xoay).
     *
     * @param viewport vùng ảnh thực sự hiển thị trên preview - lấy từ
     *   `ImageProxy.cropRect` khi bind use case kèm `ViewPort`. Nhờ nó mà ROI
     *   khớp đúng với khung vẽ trên màn hình dù PreviewView đang crop theo
     *   scale type FILL_CENTER.
     */
    fun toImageRect(
        normalized: RectF,
        viewport: Rect,
        imageWidth: Int,
        imageHeight: Int,
        rotationDegrees: Int
    ): Rect {
        // 1. Đưa viewport về không gian đã xoay - chính là không gian người dùng nhìn thấy.
        val uprightViewport = rotateToUpright(viewport, imageWidth, imageHeight, rotationDegrees)

        // 2. Đặt ROI vào bên trong viewport theo tỉ lệ.
        val vw = uprightViewport.width()
        val vh = uprightViewport.height()
        val upright = Rect(
            uprightViewport.left + (normalized.left * vw).roundToInt(),
            uprightViewport.top + (normalized.top * vh).roundToInt(),
            uprightViewport.left + (normalized.right * vw).roundToInt(),
            uprightViewport.top + (normalized.bottom * vh).roundToInt()
        )

        // 3. Xoay ngược về không gian ảnh gốc.
        val (uprightW, uprightH) = uprightSize(imageWidth, imageHeight, rotationDegrees)
        upright.intersect(Rect(0, 0, uprightW, uprightH))
        val imageRect = rotateFromUpright(upright, imageWidth, imageHeight, rotationDegrees)

        // 4. NV21 yêu cầu kích thước chẵn.
        return clampAndAlign(imageRect, imageWidth, imageHeight)
    }

    /** Kích thước ảnh sau khi xoay `rotationDegrees`. */
    fun uprightSize(imageWidth: Int, imageHeight: Int, rotationDegrees: Int): Pair<Int, Int> =
        if (rotationDegrees % 180 == 0) imageWidth to imageHeight else imageHeight to imageWidth

    /**
     * Xoay rect từ không gian ảnh gốc sang không gian đã dựng đứng.
     * Phép xoay theo chiều kim đồng hồ `rotationDegrees` độ.
     */
    fun rotateToUpright(r: Rect, w: Int, h: Int, rotationDegrees: Int): Rect =
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Rect(h - r.bottom, r.left, h - r.top, r.right)
            180 -> Rect(w - r.right, h - r.bottom, w - r.left, h - r.top)
            270 -> Rect(r.top, w - r.right, r.bottom, w - r.left)
            else -> Rect(r)
        }

    /** Phép nghịch đảo của [rotateToUpright]. */
    fun rotateFromUpright(r: Rect, w: Int, h: Int, rotationDegrees: Int): Rect =
        when (((rotationDegrees % 360) + 360) % 360) {
            90 -> Rect(r.top, h - r.right, r.bottom, h - r.left)
            180 -> Rect(w - r.right, h - r.bottom, w - r.left, h - r.top)
            270 -> Rect(w - r.bottom, r.left, w - r.top, r.right)
            else -> Rect(r)
        }

    private fun clampAndAlign(rect: Rect, imageWidth: Int, imageHeight: Int): Rect {
        val left = rect.left.coerceIn(0, imageWidth - 2) and 1.inv()
        val top = rect.top.coerceIn(0, imageHeight - 2) and 1.inv()
        var right = rect.right.coerceIn(left + 2, imageWidth)
        var bottom = rect.bottom.coerceIn(top + 2, imageHeight)
        if ((right - left) % 2 != 0) right--
        if ((bottom - top) % 2 != 0) bottom--
        return Rect(left, top, right, bottom)
    }

    /**
     * Copy vùng [rect] của mặt phẳng Y sang buffer NV21.
     *
     * ML Kit nhận dạng chữ chỉ dùng độ sáng, nên phần chroma được điền giá trị
     * trung tính 128 thay vì phải đọc và trộn lại hai mặt phẳng U/V. Cách này
     * bỏ hẳn một vòng chuyển đổi màu và không phải cấp phát Bitmap mỗi frame.
     *
     * @param reusable buffer của frame trước; chỉ dùng lại khi kích thước khớp
     *   chính xác, vì ML Kit đọc trọn mảng được truyền vào.
     * @return buffer dài đúng `w * h * 3 / 2` chứa dữ liệu NV21.
     */
    fun cropLuminanceToNv21(image: ImageProxy, rect: Rect, reusable: ByteArray?): ByteArray {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride

        val w = rect.width()
        val h = rect.height()
        val ySize = w * h
        val total = ySize + ySize / 2

        val out = if (reusable != null && reusable.size == total) reusable else ByteArray(total)

        if (pixelStride == 1) {
            for (y in 0 until h) {
                buffer.position((rect.top + y) * rowStride + rect.left)
                buffer.get(out, y * w, w)
            }
        } else {
            // Hiếm gặp: mặt phẳng Y có pixel stride > 1, phải nhặt từng byte.
            val rowLength = (w - 1) * pixelStride + 1
            val row = ByteArray(rowLength)
            for (y in 0 until h) {
                buffer.position((rect.top + y) * rowStride + rect.left * pixelStride)
                buffer.get(row, 0, rowLength)
                var offset = y * w
                for (x in 0 until w) {
                    out[offset++] = row[x * pixelStride]
                }
            }
        }

        // Chroma trung tính → ảnh xám hợp lệ theo chuẩn NV21.
        java.util.Arrays.fill(out, ySize, total, 128.toByte())
        return out
    }

    /**
     * Độ sáng trung bình của vùng Y vừa cắt (0-255).
     * Lấy mẫu thưa vì chỉ cần biết khung hình có quá tối hay không.
     */
    fun meanLuminance(nv21: ByteArray, width: Int, height: Int): Int {
        val ySize = width * height
        if (ySize <= 0 || nv21.size < ySize) return -1

        var sum = 0L
        var count = 0
        var i = 0
        val step = maxOf(1, ySize / 2048)
        while (i < ySize) {
            sum += nv21[i].toInt() and 0xFF
            count++
            i += step
        }
        return if (count == 0) -1 else (sum / count).toInt()
    }
}
