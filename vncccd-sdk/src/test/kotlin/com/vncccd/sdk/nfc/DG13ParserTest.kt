package com.vncccd.sdk.nfc

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests cho DG13Parser.
 */
class DG13ParserTest {

    @Test
    fun `parse - empty bytes returns null`() {
        val result = DG13Parser.parse(byteArrayOf())
        assertNull(result)
    }

    @Test
    fun `parse - text-based format with pipe separator`() {
        val text = "012345678901|NGUYEN VAN A|01/01/1995|Nam|Việt Nam|Kinh|Không|Hà Nội|123 Đường ABC, Q.1, TP.HCM|Sẹo ở trán|01/01/2021|01/01/2031"
        // Simulate DG13 with a simple tag+length header
        val headerBytes = byteArrayOf(0x6D.toByte(), text.length.toByte())
        val rawBytes = headerBytes + text.toByteArray(Charsets.UTF_8)

        val result = DG13Parser.parse(rawBytes)
        assertNotNull(result)
        assertEquals("012345678901", result!!.idNumber)
        assertEquals("NGUYEN VAN A", result.fullName)
        assertEquals("01/01/1995", result.dateOfBirth)
        assertEquals("Nam", result.gender)
    }

    @Test
    fun `parse - text with double pipe separator`() {
        val text = "012345678901||NGUYEN VAN B||15/06/1990||Nam||Việt Nam"
        val rawBytes = byteArrayOf(0x6D.toByte(), text.length.toByte()) + text.toByteArray(Charsets.UTF_8)

        val result = DG13Parser.parse(rawBytes)
        assertNotNull(result)
        // Should find at least the ID number via fallback regex
        assertTrue(
            result!!.idNumber == "012345678901" ||
                    result.idNumber?.contains("012345678901") == true
        )
    }

    @Test
    fun `parse - fallback extracts 12 digit ID`() {
        // Random binary with 12-digit number embedded
        val text = "Some random data 012345678901 more data 01/01/1995"
        val rawBytes = text.toByteArray(Charsets.UTF_8)

        val result = DG13Parser.parse(rawBytes)
        assertNotNull(result)
        assertEquals("012345678901", result!!.idNumber)
    }

    @Test
    fun `parse - Vietnamese UTF-8 text`() {
        val text = "012345678901|NGUYỄN VĂN AN|01/01/1995|Nam|Việt Nam|Kinh|Không|Thành phố Hà Nội|Số 1 Đường Trần Hưng Đạo"
        val rawBytes = byteArrayOf(0x6D.toByte(), 0x81.toByte(), text.length.toByte()) + text.toByteArray(Charsets.UTF_8)

        val result = DG13Parser.parse(rawBytes)
        assertNotNull(result)
        // At minimum, fallback should find the ID
        assertNotNull(result!!.idNumber)
    }
}
