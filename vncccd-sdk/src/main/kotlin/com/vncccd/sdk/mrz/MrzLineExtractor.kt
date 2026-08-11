package com.vncccd.sdk.mrz

import com.google.mlkit.vision.text.Text

/**
 * Một đoạn text kèm bounding box, đơn vị pixel trong ảnh đã xoay đúng chiều.
 */
internal data class TextFragment(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val text: String
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/**
 * Ghép các đoạn text của ML Kit thành từng dòng MRZ theo *vị trí hình học*.
 *
 * Dùng `visionText.text` rồi split theo '\n' là không đủ tin cậy: ML Kit hay
 * cắt một dòng MRZ thành nhiều block rời (nhất là ở chỗ có cụm '<<<<' dài),
 * và thứ tự các block trong chuỗi trả về là thứ tự block chứ không phải thứ
 * tự đọc. Kết quả là một dòng MRZ 30 ký tự bị vỡ thành 2-3 mảnh ngắn và bị
 * loại ngay ở bước lọc độ dài.
 *
 * Gom theo toạ độ y rồi sắp xếp theo x khôi phục lại đúng dòng vật lý.
 */
internal object MrzLineExtractor {

    /** Hai đoạn được coi là cùng dòng nếu tâm y lệch nhau dưới ngần này lần chiều cao. */
    private const val ROW_TOLERANCE_RATIO = 0.6f

    /** Sàn dung sai, phòng trường hợp bounding box dẹt bất thường. */
    private const val MIN_ROW_TOLERANCE_PX = 4

    fun rowsFrom(visionText: Text): List<String> {
        val fragments = ArrayList<TextFragment>()
        for (block in visionText.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                if (line.text.isBlank()) continue
                fragments.add(
                    TextFragment(box.left, box.top, box.right, box.bottom, line.text)
                )
            }
        }
        // Không có bounding box (hiếm) thì quay về cách split theo newline.
        if (fragments.isEmpty()) {
            return visionText.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
        return rowsFrom(fragments)
    }

    /**
     * Gom fragment thành dòng. Tách riêng khỏi kiểu ML Kit để test được trên JVM.
     */
    fun rowsFrom(fragments: List<TextFragment>): List<String> {
        if (fragments.isEmpty()) return emptyList()

        val sorted = fragments.sortedBy { it.centerY }
        val rows = ArrayList<MutableList<TextFragment>>()

        for (fragment in sorted) {
            val row = rows.lastOrNull()
            // So với tâm y trung bình của cả dòng, không so với một fragment đơn
            // lẻ - tránh việc một mảnh cao bất thường kéo lệch cả ngưỡng.
            val rowCenterY = row?.let { r -> r.sumOf { it.centerY } / r.size }
            val rowHeight = row?.let { r -> r.sumOf { it.height } / r.size } ?: 0
            val tolerance = maxOf(
                (maxOf(rowHeight, fragment.height) * ROW_TOLERANCE_RATIO).toInt(),
                MIN_ROW_TOLERANCE_PX
            )

            if (row != null && rowCenterY != null &&
                kotlin.math.abs(fragment.centerY - rowCenterY) <= tolerance
            ) {
                row.add(fragment)
            } else {
                rows.add(mutableListOf(fragment))
            }
        }

        return rows.map { row ->
            row.sortedBy { it.left }.joinToString("") { it.text.trim() }
        }
    }
}
