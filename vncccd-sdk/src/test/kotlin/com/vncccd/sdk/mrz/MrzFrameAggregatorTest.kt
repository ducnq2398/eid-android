package com.vncccd.sdk.mrz

import com.vncccd.sdk.mrz.MrzValidationTest.Companion.VN_SPECIMEN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Kiểm tra cơ chế gộp nhiều frame - phần quyết định tốc độ quét cảm nhận được.
 */
class MrzFrameAggregatorTest {

    /** Thay một ký tự tại vị trí cho trước, mô phỏng OCR đọc nhầm. */
    private fun List<String>.corrupt(line: Int, position: Int, char: Char): List<String> =
        mapIndexed { index, value ->
            if (index == line) value.replaceRange(position, position + 1, char.toString()) else value
        }

    /**
     * Bộ MRZ parse được nhưng sai check digit số giấy tờ, nên chưa đủ tin cậy
     * để đi đường tắt - dùng để kiểm tra các đường chấp nhận chậm hơn.
     */
    private val weak = VN_SPECIMEN.corrupt(0, 14, '9')

    @Test
    fun `fixture weak parse duoc nhung khong du manh`() {
        assertNotNull(MrzParser.parse(weak))
        assertFalse(MrzParser.validate(weak).isStrong)
    }

    @Test
    fun `frame hoan hao duoc chap nhan ngay lap tuc`() {
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 3)

        val result = aggregator.submit(VN_SPECIMEN)

        assertNotNull("mọi check digit đều khớp thì không cần chờ thêm frame", result)
        assertEquals("001204012345", result!!.fullDocumentNumber)
        assertEquals(1, aggregator.framesSeen)
    }

    @Test
    fun `bau chon da so sua duoc loi OCR nhap nhay`() {
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 3)

        // Mỗi frame sai một ký tự khác nhau ở dòng ngày tháng. Không frame nào
        // tự parse được, nhưng đa số tại từng vị trí vẫn khôi phục ra bản đúng.
        assertNull(aggregator.submit(VN_SPECIMEN.corrupt(1, 2, '9')))
        assertNull(aggregator.submit(VN_SPECIMEN.corrupt(1, 3, '7')))

        val result = aggregator.submit(VN_SPECIMEN.corrupt(1, 4, '2'))

        assertNotNull("đa số ký tự vẫn khôi phục được MRZ đúng", result)
        assertEquals("040515", result!!.dateOfBirth)
        assertEquals("001204012345", result.fullDocumentNumber)
    }

    @Test
    fun `frame hoan hao xen giua cac frame yeu van duoc uu tien`() {
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 3)

        assertNull(aggregator.submit(weak))
        assertNull(aggregator.submit(weak))

        assertNotNull(aggregator.submit(VN_SPECIMEN))
    }

    @Test
    fun `van giu quy tac cu N frame giong het nhau`() {
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 3)

        assertNull(aggregator.submit(weak))
        assertNull(aggregator.submit(weak))
        // Đến frame thứ 3 giống hệt nhau thì chấp nhận theo mức sàn cũ.
        assertNotNull(aggregator.submit(weak))
    }

    @Test
    fun `bo qua candidate khong du 3 dong`() {
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 2)
        assertNull(aggregator.submit(listOf(VN_SPECIMEN[0], VN_SPECIMEN[1])))
        assertEquals(0, aggregator.framesSeen)
    }

    @Test
    fun `reset xoa sach lich su bau chon`() {
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 3)

        aggregator.submit(weak)
        aggregator.submit(weak)
        aggregator.reset()

        assertEquals(0, aggregator.framesSeen)
        // Sau reset phải đếm lại từ đầu, không được ăn theo 2 frame trước.
        assertNull(aggregator.submit(weak))
        assertNull(aggregator.submit(weak))
    }

    @Test
    fun `hoa phieu uu tien frame moi nhat`() {
        // Ngưỡng cao để không frame nào được chấp nhận, chỉ quan sát bầu chọn.
        val aggregator = MrzFrameAggregator(requiredConsecutiveFrames = 10)

        aggregator.submit(weak.corrupt(2, 0, 'X'))
        aggregator.submit(weak)

        // 1 phiếu 'X' (frame cũ) so với 1 phiếu 'N' (frame mới) → chọn frame mới,
        // vì frame mới phản ánh tư thế cầm thẻ hiện tại.
        val voted = aggregator.majorityVote()
        assertNotNull(voted)
        assertEquals('N', voted!![2][0])
    }
}
