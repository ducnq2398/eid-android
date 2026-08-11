package com.vncccd.sdk.mrz

import com.vncccd.sdk.models.MrzData

/**
 * Gộp kết quả OCR của nhiều frame để triệt tiêu nhiễu.
 *
 * Cách cũ là yêu cầu N frame *giống hệt nhau từng ký tự* mới chấp nhận. Cách
 * đó vừa chậm vừa mong manh: chỉ cần một ký tự nhấp nháy giữa hai frame là
 * bộ đếm về 0 và người dùng phải giữ thẻ lại từ đầu.
 *
 * Ở đây dùng ba đường chấp nhận, xếp theo độ nhanh:
 *
 * 1. **Fast path** - một frame duy nhất mà *mọi* check digit đều khớp
 *    ([MrzValidation.isStrong]). Xác suất OCR sai mà vẫn qua được tất cả
 *    check digit độc lập là cực thấp, không cần đợi thêm frame nào.
 * 2. **Consensus** - bầu chọn đa số theo từng vị trí ký tự trên cửa sổ N
 *    frame gần nhất. Sửa được lỗi nhấp nháy lẻ tẻ mà cách so khớp tuyệt đối
 *    phải bỏ đi cả frame.
 * 3. **Floor** - vẫn giữ nguyên quy tắc cũ (N frame liên tiếp giống nhau)
 *    làm mức sàn, để không có thẻ nào đang quét được lại thành quét không được.
 */
internal class MrzFrameAggregator(
    private val requiredConsecutiveFrames: Int,
    private val windowSize: Int = 8
) {

    private val window = ArrayDeque<List<String>>()

    private var lastRawCandidate: List<String>? = null
    private var rawStreak = 0

    private var lastVoted: List<String>? = null
    private var votedStreak = 0

    /** Số frame đã nhận kể từ lần reset gần nhất. */
    var framesSeen: Int = 0
        private set

    /**
     * Nạp một ứng viên MRZ (3 dòng, mỗi dòng đã pad về 30 ký tự).
     *
     * @return [MrzData] nếu đã đủ tin cậy để chấp nhận, null nếu cần thêm frame.
     */
    fun submit(candidate: List<String>): MrzData? {
        if (candidate.size != 3) return null
        framesSeen++

        window.addLast(candidate)
        while (window.size > windowSize) window.removeFirst()

        // 1. Fast path - frame này tự nó đã đủ mạnh.
        acceptIfStrong(candidate)?.let { return it }

        // 2. Consensus - bầu chọn đa số trên cửa sổ.
        val voted = majorityVote()
        if (voted != null) {
            acceptIfStrong(voted)?.let { return it }

            if (voted == lastVoted) votedStreak++ else votedStreak = 1
            lastVoted = voted

            if (votedStreak >= requiredConsecutiveFrames) {
                MrzParser.parse(voted)?.let { return it }
            }
        }

        // 3. Floor - quy tắc cũ: N frame liên tiếp giống hệt nhau.
        if (candidate == lastRawCandidate) rawStreak++ else rawStreak = 1
        lastRawCandidate = candidate

        if (rawStreak >= requiredConsecutiveFrames) {
            MrzParser.parse(candidate)?.let { return it }
            // Parse hỏng thì chuỗi này vô nghĩa, bỏ đi để không kẹt mãi.
            rawStreak = 0
            lastRawCandidate = null
        }

        return null
    }

    fun reset() {
        window.clear()
        lastRawCandidate = null
        rawStreak = 0
        lastVoted = null
        votedStreak = 0
        framesSeen = 0
    }

    private fun acceptIfStrong(lines: List<String>): MrzData? {
        val data = MrzParser.parse(lines) ?: return null
        return if (MrzParser.validate(lines).isStrong) data else null
    }

    /**
     * Bầu chọn ký tự chiếm đa số tại từng vị trí trên toàn bộ cửa sổ.
     *
     * Hoà phiếu thì ưu tiên ký tự của frame mới nhất - frame mới nhất phản ánh
     * tư thế cầm thẻ hiện tại, còn các frame cũ có thể đã lệch khung.
     */
    internal fun majorityVote(): List<String>? {
        if (window.size < 2) return null
        val newest = window.last()

        return (0 until 3).map { lineIndex ->
            val sb = StringBuilder(MrzParser.TD1_LINE_LENGTH)
            for (pos in 0 until MrzParser.TD1_LINE_LENGTH) {
                val counts = HashMap<Char, Int>(8)
                for (frame in window) {
                    val line = frame.getOrNull(lineIndex) ?: continue
                    if (pos < line.length) counts[line[pos]] = (counts[line[pos]] ?: 0) + 1
                }
                if (counts.isEmpty()) return null

                val topCount = counts.values.max()
                val preferred = newest.getOrNull(lineIndex)?.getOrNull(pos)
                val winner = if (preferred != null && counts[preferred] == topCount) {
                    preferred
                } else {
                    // Không có frame mới nhất trong nhóm dẫn đầu: chọn ký tự nhỏ
                    // nhất theo mã để kết quả ổn định giữa các lần chạy.
                    counts.filterValues { it == topCount }.keys.min()
                }
                sb.append(winner)
            }
            sb.toString()
        }
    }
}
