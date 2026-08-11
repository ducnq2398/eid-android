package com.vncccd.sdk.mrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra khâu chuẩn hoá ký tự OCR theo từng vị trí trường của TD1.
 */
class MrzOcrCleanupTest {

    @Test
    fun `cleanOcrText khong con pha ho ten chua chu O`() {
        // Bản cũ replace "O" thành "0" trên toàn chuỗi, biến LONG thành L0NG.
        val line3 = MrzParser.cleanOcrText("NGUYEN<<VAN<LONG<<<<<<<<<<<<<<")
        assertTrue(line3.contains("LONG"))
    }

    @Test
    fun `cleanOcrText bo khoang trang ML Kit chen vao`() {
        assertEquals(
            "IDVNM001204012",
            MrzParser.cleanOcrText("IDVNM 001 204 012")
        )
    }

    @Test
    fun `cleanOcrText giu nguyen do dai khi gap ky tu la`() {
        // Ký tự lạ được thay bằng filler chứ không bị xoá - xoá sẽ làm lệch
        // toàn bộ các trường phía sau.
        val raw = "IDVNM0012040124345{{{{}}}}[[[["
        val cleaned = MrzParser.cleanOcrText(raw)

        assertEquals(raw.length, cleaned.length)
        assertTrue(cleaned.startsWith("IDVNM0012040124345"))
        assertTrue(cleaned.substring(18).all { it == '<' })
    }

    @Test
    fun `cleanOcrText doi guillemet thanh cap filler`() {
        assertEquals("AB<<CD", MrzParser.cleanOcrText("AB«CD"))
    }

    @Test
    fun `smartClean ep so o vung so giay to cua line 1`() {
        //  O→0, I→1, S→5, B→8 trong vùng document number
        val cleaned = MrzParser.smartCleanMrzLine("IDVNMOO12O4O12", 1)
        assertTrue(cleaned.startsWith("IDVNM"))
        assertEquals("001204012", cleaned.substring(5, 14))
    }

    @Test
    fun `smartClean ep chu o truong ho ten cua line 3`() {
        // Trường tên chỉ chứa A-Z và '<', nên mọi chữ số đều là lỗi OCR.
        val cleaned = MrzParser.smartCleanMrzLine("NGUYEN<<VAN<L0NG<<<<<<<<<<<<<<", 3)
        assertEquals("NGUYEN<<VAN<LONG<<<<<<<<<<<<<<", cleaned)
    }

    @Test
    fun `smartClean ep chu o truong quoc tich cua line 2`() {
        val cleaned = MrzParser.smartCleanMrzLine("0405155M2905154V1M<<<<<<<<<<<6", 2)
        assertEquals("VIM", cleaned.substring(15, 18))
        // Ngày tháng vẫn nguyên vẹn
        assertEquals("040515", cleaned.substring(0, 6))
    }

    @Test
    fun `smartClean khong dung toi o gioi tinh`() {
        val cleaned = MrzParser.smartCleanMrzLine("0405155M2905154VNM<<<<<<<<<<<6", 2)
        assertEquals('M', cleaned[7])
    }

    @Test
    fun `chuoi OCR nhieu van parse duoc sau khi chuan hoa`() {
        // Frame thực tế: khoảng trắng chèn giữa, O/I đọc nhầm ở vùng số,
        // số 0 đọc nhầm ở vùng tên.
        val noisyLines = listOf(
            "IDVNM OO12O4O124345<<<<<<<<<<<<",
            "O4O5155M29O5154VNM<<<<<<<<<<<6",
            "NGUYEN<<VAN<AN<<<<<<<<<<<<<<<<"
        )

        val cleaned = noisyLines.mapIndexed { index, line ->
            MrzParser.smartCleanMrzLine(MrzParser.cleanOcrText(line), index + 1)
                .take(30)
        }

        val data = MrzParser.parse(cleaned)
        assertEquals("001204012345", data?.fullDocumentNumber)
        assertEquals("040515", data?.dateOfBirth)
        assertTrue(MrzParser.validate(cleaned).isStrong)
    }
}
