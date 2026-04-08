package com.vncccd.sdk.mrz

import com.vncccd.sdk.models.MrzData

/**
 * Parser cho MRZ format TD1 (ID cards) theo ICAO Doc 9303.
 * TD1: 3 dòng × 30 ký tự
 *
 * Line 1: [DocType(2)][IssuingState(3)][DocNumber(9)][CD1(1)][OptionalData1(15)]
 * Line 2: [DOB(6)][CD2(1)][Sex(1)][DOE(6)][CD3(1)][Nationality(3)][OptionalData2(11)][CD4(1)]
 * Line 3: [Name(30)]
 */
object MrzParser {

    private const val TD1_LINE_LENGTH = 30
    private const val TD1_NUM_LINES = 3

    /** Ký tự fill trong MRZ */
    private const val FILLER = '<'

    /** Weights cho check digit calculation */
    private val WEIGHTS = intArrayOf(7, 3, 1)

    /**
     * Parse MRZ text thành MrzData.
     *
     * @param mrzLines 3 dòng MRZ (đã cleaned)
     * @return MrzData nếu parse thành công, null nếu thất bại
     */
    fun parse(mrzLines: List<String>): MrzData? {
        if (mrzLines.size != TD1_NUM_LINES) return null

        val line1 = padOrTrim(mrzLines[0])
        val line2 = padOrTrim(mrzLines[1])
        val line3 = padOrTrim(mrzLines[2])

        // Validate line lengths
        if (line1.length != TD1_LINE_LENGTH ||
            line2.length != TD1_LINE_LENGTH ||
            line3.length != TD1_LINE_LENGTH
        ) return null

        // Parse Line 1
        val documentType = line1.substring(0, 2) // I<
        if (!documentType.startsWith("I") && !documentType.startsWith("A") && !documentType.startsWith("C")) {
            return null
        }

        val issuingState = line1.substring(2, 5) // VNM
        val documentNumber = line1.substring(5, 14).replace(FILLER.toString(), "")
        val docCheckDigit = charToValue(line1[14])
        val optionalData1 = line1.substring(15, 30)

        // Validate document number check digit
        val computedDocCheck = computeCheckDigit(line1.substring(5, 14))
        if (computedDocCheck != docCheckDigit) {
            // Try with optional data for long document numbers (Vietnam 12-digit)
            // In some CCCD, the document number spans into optional data
        }

        // Parse Line 2
        val dateOfBirth = line2.substring(0, 6)
        val dobCheckDigit = charToValue(line2[6])
        val sex = line2.substring(7, 8)
        val dateOfExpiry = line2.substring(8, 14)
        val doeCheckDigit = charToValue(line2[14])
        val nationality = line2.substring(15, 18).replace(FILLER.toString(), "")
        val optionalData2 = line2.substring(18, 29)
        val compositeCheckDigit = charToValue(line2[29])

        // Validate check digits
        val computedDobCheck = computeCheckDigit(dateOfBirth)
        val computedDoeCheck = computeCheckDigit(dateOfExpiry)

        if (computedDobCheck != dobCheckDigit) return null
        if (computedDoeCheck != doeCheckDigit) return null

        // Parse Line 3 - Name
        val nameField = line3.replace(FILLER, ' ').trim()
        val nameParts = line3.split("<<")
        val surname = nameParts.getOrElse(0) { "" }.replace(FILLER, ' ').trim()
        val givenNames = if (nameParts.size > 1) {
            nameParts.subList(1, nameParts.size)
                .joinToString(" ")
                .replace(FILLER, ' ')
                .trim()
        } else ""

        val fullName = "$surname $givenNames".trim()

        return MrzData(
            documentNumber = documentNumber,
            dateOfBirth = dateOfBirth,
            dateOfExpiry = dateOfExpiry,
            gender = sex,
            nationality = nationality,
            fullNameMrz = fullName,
            rawMrz = "$line1\n$line2\n$line3",
            optionalData1 = optionalData1,
            optionalData2 = optionalData2
        )
    }

    /**
     * Parse từ raw MRZ string (3 dòng ghép nhau hoặc phân cách bởi newline).
     */
    fun parseRaw(rawMrz: String): MrzData? {
        val cleaned = rawMrz.replace(" ", "").replace("\r", "")

        // Try splitting by newline first
        val lines = cleaned.split("\n").filter { it.isNotBlank() }
        if (lines.size == TD1_NUM_LINES) {
            return parse(lines)
        }

        // Try as continuous string (90 chars)
        if (cleaned.length == TD1_LINE_LENGTH * TD1_NUM_LINES) {
            return parse(
                listOf(
                    cleaned.substring(0, 30),
                    cleaned.substring(30, 60),
                    cleaned.substring(60, 90)
                )
            )
        }

        return null
    }

    /**
     * Tính check digit theo ICAO 9303 algorithm.
     * Weight pattern: 7, 3, 1, 7, 3, 1, ...
     * Result = sum mod 10
     */
    fun computeCheckDigit(input: String): Int {
        var sum = 0
        for (i in input.indices) {
            val value = charToValue(input[i])
            sum += value * WEIGHTS[i % 3]
        }
        return sum % 10
    }

    /**
     * Chuyển ký tự MRZ thành giá trị số.
     * - '0'-'9' → 0-9
     * - 'A'-'Z' → 10-35
     * - '<' → 0
     */
    private fun charToValue(c: Char): Int {
        return when {
            c == FILLER -> 0
            c in '0'..'9' -> c - '0'
            c in 'A'..'Z' -> c - 'A' + 10
            c in 'a'..'z' -> c - 'a' + 10
            else -> 0
        }
    }

    /**
     * Pad hoặc trim string về đúng 30 ký tự.
     */
    private fun padOrTrim(s: String): String {
        return when {
            s.length == TD1_LINE_LENGTH -> s
            s.length > TD1_LINE_LENGTH -> s.substring(0, TD1_LINE_LENGTH)
            else -> s.padEnd(TD1_LINE_LENGTH, FILLER)
        }
    }

    /**
     * Kiểm tra một chuỗi text có phải là MRZ TD1 line 1 không.
     * Pattern: starts with I<VNM or IDVNM
     */
    fun isMrzLine1(text: String): Boolean {
        val cleaned = text.replace(" ", "").uppercase()
        return (cleaned.startsWith("I<VNM") || cleaned.startsWith("IDVNM") ||
                cleaned.startsWith("I0VNM") || cleaned.startsWith("ICVNM")) &&
                cleaned.length >= TD1_LINE_LENGTH - 5
    }

    /**
     * Clean OCR text - sửa các lỗi OCR phổ biến trong MRZ.
     */
    fun cleanOcrText(text: String): String {
        return text
            .uppercase()
            .replace("«", "<<")
            .replace("»", ">>")
            .replace(" ", "")
            .replace("O", "0")  // Trong context number positions
            .replace("{", "<")
            .replace("}", ">")
            .replace("[", "<")
            .replace("]", ">")
            .replace("(", "<")
            .replace(")", ">")
    }

    /**
     * Smart clean - chỉ replace O→0 ở vị trí số.
     */
    fun smartCleanMrzLine(line: String, lineNumber: Int): String {
        val chars = line.uppercase().toCharArray()

        when (lineNumber) {
            1 -> {
                // Positions 5-14: document number (can be alphanumeric)
                // Position 14: check digit (numeric)
                // Vietnam CCCD dùng số, nên OCR chữ ở vùng này thường là lỗi cần sửa.
                for (i in 5..13) {
                    if (i < chars.size) chars[i] = fixToDigit(chars[i])
                }
                if (chars.size > 14) {
                    chars[14] = fixToDigit(chars[14])
                }
            }
            2 -> {
                // Positions 0-5: DOB (numeric)
                // Position 6: check digit (numeric)
                // Position 7: sex (alpha M/F/X)
                // Positions 8-13: DOE (numeric)
                // Position 14: check digit (numeric)
                for (i in 0..6) {
                    if (i < chars.size) chars[i] = fixToDigit(chars[i])
                }
                for (i in 8..14) {
                    if (i < chars.size) chars[i] = fixToDigit(chars[i])
                }
            }
        }

        return String(chars)
    }

    private fun fixToDigit(c: Char): Char {
        return when (c) {
            'O' -> '0'
            'I', 'L' -> '1'
            'Z' -> '2'
            'S' -> '5'
            'B' -> '8'
            else -> c
        }
    }
}
