package com.vncccd.sdk.mrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra việc ghép các mảnh text của ML Kit thành đúng dòng vật lý.
 */
class MrzLineExtractorTest {

    private fun fragment(left: Int, top: Int, right: Int, bottom: Int, text: String) =
        TextFragment(left, top, right, bottom, text)

    @Test
    fun `ghep cac manh cung mot dong theo toa do x`() {
        // ML Kit hay cắt một dòng MRZ thành nhiều block, nhất là ở cụm '<<<<'.
        // Thứ tự trả về không phải thứ tự đọc, nên phải sắp lại theo x.
        val fragments = listOf(
            fragment(200, 10, 300, 30, "0124345"),
            fragment(10, 10, 200, 30, "IDVNM00120"),
            fragment(300, 12, 400, 32, "<<<<<<<<<<<<")
        )

        val rows = MrzLineExtractor.rowsFrom(fragments)

        assertEquals(1, rows.size)
        assertEquals("IDVNM001200124345<<<<<<<<<<<<", rows[0])
    }

    @Test
    fun `tach dung 3 dong MRZ theo toa do y`() {
        val fragments = listOf(
            fragment(10, 70, 400, 90, "NGUYEN<<VAN<AN<<<<<<<<<<<<<<<<"),
            fragment(10, 10, 400, 30, "IDVNM0012040124345<<<<<<<<<<<<"),
            fragment(10, 40, 400, 60, "0405155M2905154VNM<<<<<<<<<<<6")
        )

        val rows = MrzLineExtractor.rowsFrom(fragments)

        assertEquals(3, rows.size)
        assertTrue(rows[0].startsWith("IDVNM"))
        assertTrue(rows[1].startsWith("0405155M"))
        assertTrue(rows[2].startsWith("NGUYEN"))
    }

    @Test
    fun `lech y nho trong cung mot dong khong bi tach ra`() {
        // Bounding box của các block trên cùng một dòng hiếm khi thẳng hàng tuyệt đối.
        val fragments = listOf(
            fragment(10, 10, 200, 30, "IDVNM00120"),
            fragment(200, 14, 400, 34, "40124345<<")
        )

        assertEquals(1, MrzLineExtractor.rowsFrom(fragments).size)
    }

    @Test
    fun `bo qua manh rong va tra ve rong khi khong co gi`() {
        assertEquals(emptyList<String>(), MrzLineExtractor.rowsFrom(emptyList()))
    }

    @Test
    fun `khoang trang trong tung manh bi cat bo khi ghep`() {
        val fragments = listOf(
            fragment(10, 10, 200, 30, "IDVNM 00120 "),
            fragment(200, 10, 400, 30, " 40124345")
        )

        assertEquals("IDVNM 0012040124345", MrzLineExtractor.rowsFrom(fragments)[0])
    }
}
