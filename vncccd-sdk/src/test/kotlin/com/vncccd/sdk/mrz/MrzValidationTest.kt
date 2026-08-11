package com.vncccd.sdk.mrz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Kiểm tra các tín hiệu xác thực dùng để rút ngắn số frame cần quét.
 */
class MrzValidationTest {

    companion object {
        /**
         * Specimen TD1 chính thức trong ICAO 9303 Part 5.
         * Mọi check digit của bộ này đều đã được công bố, dùng làm mốc đối chiếu
         * cho công thức composite check digit.
         */
        val ICAO_SPECIMEN = listOf(
            "I<UTOD231458907<<<<<<<<<<<<<<<",
            "7408122F1204159UTO<<<<<<<<<<<6",
            "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"
        )

        /**
         * CCCD Việt Nam hợp lệ, dựng thủ công:
         * - Số CCCD 001204012345 (tỉnh 001, mã 2 = nam sinh 2000s, năm sinh 04)
         * - Ngày sinh 15/05/2004, hết hạn 15/05/2029
         */
        val VN_SPECIMEN = listOf(
            "IDVNM0012040124345<<<<<<<<<<<<",
            "0405155M2905154VNM<<<<<<<<<<<6",
            "NGUYEN<<VAN<AN<<<<<<<<<<<<<<<<"
        )
    }

    @Test
    fun `composite check digit khop voi specimen ICAO 9303`() {
        val validation = MrzParser.validate(ICAO_SPECIMEN)

        assertTrue("document check digit", validation.documentNumberValid)
        assertTrue("DOB check digit", validation.dateOfBirthValid)
        assertTrue("DOE check digit", validation.dateOfExpiryValid)
        assertTrue("composite check digit", validation.compositeValid)
        assertTrue(validation.allCheckDigitsValid)
    }

    @Test
    fun `specimen ICAO du manh de chap nhan ngay tu mot frame`() {
        assertTrue(MrzParser.validate(ICAO_SPECIMEN).isStrong)
    }

    @Test
    fun `CCCD Viet Nam hop le pass toan bo tin hieu`() {
        val validation = MrzParser.validate(VN_SPECIMEN)

        assertTrue("document check digit", validation.documentNumberValid)
        assertTrue("DOB check digit", validation.dateOfBirthValid)
        assertTrue("DOE check digit", validation.dateOfExpiryValid)
        assertTrue("composite check digit", validation.compositeValid)
        assertTrue("cross-check CCCD", validation.vietnamConsistent)
        assertTrue(validation.isStrong)
    }

    @Test
    fun `cross-check CCCD bat duoc nam sinh khong khop`() {
        val data = MrzParser.parse(VN_SPECIMEN)
        assertNotNull(data)
        assertTrue(MrzParser.isVietnamConsistent(data!!))

        // Số CCCD nói sinh năm 04, MRZ nói sinh năm 99 → không thể cùng một thẻ.
        val mismatched = data.copy(dateOfBirth = "990515")
        assertFalse(MrzParser.isVietnamConsistent(mismatched))
    }

    @Test
    fun `cross-check CCCD bat duoc gioi tinh khong khop`() {
        val data = MrzParser.parse(VN_SPECIMEN)!!
        // Mã thế kỷ/giới tính '2' là nam, MRZ ghi F → mâu thuẫn.
        assertFalse(MrzParser.isVietnamConsistent(data.copy(gender = "F")))
    }

    @Test
    fun `check digit sai lam giam diem nhung khong crash`() {
        val corrupted = VN_SPECIMEN.toMutableList()
        corrupted[0] = corrupted[0].replaceRange(14, 15, "9") // check digit số giấy tờ sai

        val validation = MrzParser.validate(corrupted)
        assertFalse(validation.documentNumberValid)
        assertFalse(validation.isStrong)
        assertTrue(validation.dateOfBirthValid)
    }

    @Test
    fun `validate tra ve NONE khi khong du 3 dong`() {
        assertEquals(MrzValidation.NONE, MrzParser.validate(listOf("ABC", "DEF")))
    }

    @Test
    fun `ngay khong hop le bi loai du check digit dung`() {
        // Tháng 19 không tồn tại. Dựng check digit khớp để chứng minh rằng
        // riêng check digit là chưa đủ.
        val dob = "041915"
        val checkDigit = MrzParser.computeCheckDigit(dob)
        val line2 = dob + checkDigit + "M2905154VNM<<<<<<<<<<<6"

        assertFalse(MrzParser.isPlausibleYymmdd(dob))
        assertNull(MrzParser.parse(listOf(VN_SPECIMEN[0], line2, VN_SPECIMEN[2])))
    }

    @Test
    fun `isPlausibleYymmdd chap nhan ngay hop le va loai ngay sai`() {
        assertTrue(MrzParser.isPlausibleYymmdd("040515"))
        assertTrue(MrzParser.isPlausibleYymmdd("991231"))
        assertFalse(MrzParser.isPlausibleYymmdd("041315")) // tháng 13
        assertFalse(MrzParser.isPlausibleYymmdd("040532")) // ngày 32
        assertFalse(MrzParser.isPlausibleYymmdd("0405"))   // thiếu ký tự
        assertFalse(MrzParser.isPlausibleYymmdd("04051A")) // có chữ
    }
}
