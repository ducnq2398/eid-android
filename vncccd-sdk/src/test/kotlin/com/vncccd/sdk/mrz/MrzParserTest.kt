package com.vncccd.sdk.mrz

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests cho MrzParser - validate TD1 format parsing
 * và ICAO 9303 check digit calculation.
 */
class MrzParserTest {

    @Test
    fun `computeCheckDigit - basic numeric input`() {
        // ICAO 9303 example: "520727" should give check digit 3
        val result = MrzParser.computeCheckDigit("520727")
        // Weighted: 5*7 + 2*3 + 0*1 + 7*7 + 2*3 + 7*1 = 35+6+0+49+6+7 = 103
        // 103 % 10 = 3
        assertEquals(3, result)
    }

    @Test
    fun `computeCheckDigit - with filler characters`() {
        // '<' should be treated as 0
        val result = MrzParser.computeCheckDigit("AB<123<<<")
        // A=10, B=11, <=0, 1, 2, 3, <=0, <=0, <=0
        // 10*7 + 11*3 + 0*1 + 1*7 + 2*3 + 3*1 + 0*7 + 0*3 + 0*1
        // = 70 + 33 + 0 + 7 + 6 + 3 + 0 + 0 + 0 = 119
        // 119 % 10 = 9
        assertEquals(9, result)
    }

    @Test
    fun `computeCheckDigit - all fillers`() {
        val result = MrzParser.computeCheckDigit("<<<<<<")
        assertEquals(0, result)
    }

    @Test
    fun `parse - valid TD1 Vietnam CCCD`() {
        val lines = listOf(
            "I<VNM0123456784<<<<<<<<<<<<<<<",
            "9501016M3001019VNM<<<<<<<<<<<0",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        )

        val result = MrzParser.parse(lines)
        assertNotNull(result)
        assertEquals("012345678", result!!.documentNumber)
        assertEquals("950101", result.dateOfBirth)
        assertEquals("300101", result.dateOfExpiry)
        assertEquals("M", result.gender)
        assertEquals("VNM", result.nationality)
        assertTrue(result.fullNameMrz.contains("NGUYEN"))
    }

    @Test
    fun `parse - invalid line count`() {
        val lines = listOf("I<VNM0123456784<<<<<<<<<<<<<<<", "9501011M3001019VNM<<<<<<<<<<<0")
        val result = MrzParser.parse(lines)
        assertNull(result)
    }

    @Test
    fun `parse - invalid DOB check digit returns null`() {
        val lines = listOf(
            "I<VNM0123456784<<<<<<<<<<<<<<<",
            "9501019M3001019VNM<<<<<<<<<<<0", // DOB check digit 9 is wrong
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        )
        val result = MrzParser.parse(lines)
        assertNull(result) // Should fail DOB check digit validation
    }

    @Test
    fun `parseRaw - from continuous string`() {
        val raw = "I<VNM0123456784<<<<<<<<<<<<<<<" +
                "9501016M3001019VNM<<<<<<<<<<<0" +
                "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"

        val result = MrzParser.parseRaw(raw)
        assertNotNull(result)
        assertEquals("012345678", result!!.documentNumber)
    }

    @Test
    fun `parseRaw - from newline separated`() {
        val raw = """
            I<VNM0123456784<<<<<<<<<<<<<<<
            9501016M3001019VNM<<<<<<<<<<<0
            NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<
        """.trimIndent()

        val result = MrzParser.parseRaw(raw)
        assertNotNull(result)
    }

    @Test
    fun `isMrzLine1 - valid Vietnam CCCD`() {
        assertTrue(MrzParser.isMrzLine1("I<VNM012345678<<<<<<<<<<<<<<<"))
        assertTrue(MrzParser.isMrzLine1("IDVNM012345678<<<<<<<<<<<<<<<"))
    }

    @Test
    fun `isMrzLine1 - invalid line`() {
        assertFalse(MrzParser.isMrzLine1("HELLO WORLD"))
        assertFalse(MrzParser.isMrzLine1("P<USA"))  // Too short for TD1
    }

    @Test
    fun `cleanOcrText - fixes common OCR errors`() {
        val input = "i<vnm0123456"
        val result = MrzParser.cleanOcrText(input)
        assertTrue(result.startsWith("I"))
        assertTrue(result.contains("VNM"))
    }

    @Test
    fun `fullDocumentNumber - reconstructs 12 digit number`() {
        val mrzData = com.vncccd.sdk.models.MrzData(
            documentNumber = "012345678",
            dateOfBirth = "950101",
            dateOfExpiry = "300101",
            optionalData1 = "901<<<<<<<<<<<<" // Extra 3 digits
        )
        assertEquals("012345678901", mrzData.fullDocumentNumber)
    }

    @Test
    fun `fullDocumentNumber - no optional data`() {
        val mrzData = com.vncccd.sdk.models.MrzData(
            documentNumber = "012345678",
            dateOfBirth = "950101",
            dateOfExpiry = "300101",
            optionalData1 = "<<<<<<<<<<<<<<<" // All fillers
        )
        assertEquals("012345678", mrzData.fullDocumentNumber)
    }

    @Test
    fun `fullDocumentNumber - only leading digits in optional data are used`() {
        val mrzData = com.vncccd.sdk.models.MrzData(
            documentNumber = "012345678",
            dateOfBirth = "950101",
            dateOfExpiry = "300101",
            optionalData1 = "901ABC<<<<<<<<<<"
        )
        assertEquals("012345678901", mrzData.fullDocumentNumber)
    }

    @Test
    fun `fullDocumentNumber - truncates noisy over-length numeric result to 12`() {
        val mrzData = com.vncccd.sdk.models.MrzData(
            documentNumber = "012345678",
            dateOfBirth = "950101",
            dateOfExpiry = "300101",
            optionalData1 = "9012<<<<<<<<<<<<<"
        )
        assertEquals("012345678901", mrzData.fullDocumentNumber)
    }
}
