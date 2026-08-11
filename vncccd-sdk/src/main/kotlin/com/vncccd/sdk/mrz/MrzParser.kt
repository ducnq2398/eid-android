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

    const val TD1_LINE_LENGTH = 30
    private const val TD1_NUM_LINES = 3

    /** Ký tự fill trong MRZ */
    private const val FILLER = '<'

    /** Weights cho check digit calculation */
    private val WEIGHTS = intArrayOf(7, 3, 1)

    /** Bộ ký tự hợp lệ duy nhất của MRZ */
    private const val MRZ_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<"

    /**
     * Parse MRZ text thành MrzData.
     *
     * @param mrzLines 3 dòng MRZ (đã cleaned)
     * @return MrzData nếu parse thành công, null nếu thất bại
     */
    fun parse(mrzLines: List<String>): MrzData? {
        val lines = normalize(mrzLines) ?: return null
        val (line1, line2, line3) = lines

        // Parse Line 1
        val documentType = line1.substring(0, 2) // I<
        if (!documentType.startsWith("I") && !documentType.startsWith("A") && !documentType.startsWith("C")) {
            return null
        }

        val documentNumber = line1.substring(5, 14).replace(FILLER.toString(), "")
        val optionalData1 = line1.substring(15, 30)

        // Parse Line 2
        val dateOfBirth = line2.substring(0, 6)
        val sex = line2.substring(7, 8)
        val dateOfExpiry = line2.substring(8, 14)
        val nationality = line2.substring(15, 18).replace(FILLER.toString(), "")
        val optionalData2 = line2.substring(18, 29)

        // Check digit ngày sinh / ngày hết hạn là điều kiện bắt buộc: đây là hai
        // trường dùng để dẫn xuất khoá BAC, sai một ký tự là NFC không mở được.
        if (computeCheckDigit(dateOfBirth) != charToValue(line2[6])) return null
        if (computeCheckDigit(dateOfExpiry) != charToValue(line2[14])) return null

        // Ngày phải hợp lệ về mặt lịch, loại bỏ các frame nhiễu vô tình khớp check digit.
        if (!isPlausibleYymmdd(dateOfBirth) || !isPlausibleYymmdd(dateOfExpiry)) return null

        // Parse Line 3 - Name
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
     * Chấm điểm độ tin cậy của một bộ 3 dòng MRZ.
     *
     * Không reject gì cả - chỉ trả về từng tín hiệu để caller tự quyết định.
     * [MrzFrameAggregator] dùng kết quả này để rút ngắn số frame cần thiết
     * khi mọi check digit đều khớp.
     */
    fun validate(mrzLines: List<String>): MrzValidation {
        val lines = normalize(mrzLines) ?: return MrzValidation.NONE
        val (line1, line2, line3) = lines

        val dateOfBirth = line2.substring(0, 6)
        val dateOfExpiry = line2.substring(8, 14)

        val docValid = computeCheckDigit(line1.substring(5, 14)) == charToValue(line1[14])
        val dobValid = computeCheckDigit(dateOfBirth) == charToValue(line2[6])
        val doeValid = computeCheckDigit(dateOfExpiry) == charToValue(line2[14])
        val compositeValid = computeCheckDigit(compositeInput(line1, line2)) == charToValue(line2[29])
        val datesPlausible = isPlausibleYymmdd(dateOfBirth) && isPlausibleYymmdd(dateOfExpiry)

        return MrzValidation(
            documentNumberValid = docValid,
            dateOfBirthValid = dobValid,
            dateOfExpiryValid = doeValid,
            compositeValid = compositeValid,
            datesPlausible = datesPlausible,
            vietnamConsistent = parse(listOf(line1, line2, line3))
                ?.let { isVietnamConsistent(it) } ?: false
        )
    }

    /**
     * Chuỗi input cho composite check digit của TD1 theo ICAO 9303 Part 5.
     * Gồm: line1[5..29], line2[0..6], line2[8..14], line2[18..28].
     */
    private fun compositeInput(line1: String, line2: String): String =
        line1.substring(5, 30) +
                line2.substring(0, 7) +
                line2.substring(8, 15) +
                line2.substring(18, 29)

    /**
     * Cross-check đặc thù CCCD Việt Nam (12 số).
     *
     * Cấu trúc số CCCD: [mã tỉnh 3][mã thế kỷ+giới tính 1][2 số năm sinh][6 số ngẫu nhiên].
     * Ba trường này lấy từ ba vùng khác nhau của MRZ, nên việc chúng khớp nhau
     * là bằng chứng độc lập rất mạnh rằng OCR đã đọc đúng.
     */
    fun isVietnamConsistent(data: MrzData): Boolean {
        val id = data.fullDocumentNumber
        if (id.length != 12 || !id.all { it.isDigit() }) return false

        val province = id.substring(0, 3).toInt()
        if (province !in 1..96) return false

        // Mã thế kỷ/giới tính: chẵn = nam, lẻ = nữ.
        val genderCode = id[3] - '0'
        val expectedSex = if (genderCode % 2 == 0) "M" else "F"
        if (data.gender != expectedSex) return false

        // 2 số năm sinh trong CCCD phải khớp YY của ngày sinh trong MRZ.
        if (data.dateOfBirth.length < 2) return false
        return id.substring(4, 6) == data.dateOfBirth.substring(0, 2)
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
     * Kiểm tra chuỗi YYMMDD có hợp lệ về mặt lịch không.
     */
    fun isPlausibleYymmdd(date: String): Boolean {
        if (date.length != 6 || !date.all { it.isDigit() }) return false
        val month = date.substring(2, 4).toInt()
        val day = date.substring(4, 6).toInt()
        return month in 1..12 && day in 1..31
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
     * Chuẩn hoá về đúng 3 dòng × 30 ký tự, null nếu không đủ dòng.
     */
    private fun normalize(mrzLines: List<String>): Triple<String, String, String>? {
        if (mrzLines.size != TD1_NUM_LINES) return null
        return Triple(
            padOrTrim(mrzLines[0]),
            padOrTrim(mrzLines[1]),
            padOrTrim(mrzLines[2])
        )
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
     * Clean OCR text - đưa về đúng bộ ký tự MRZ.
     *
     * Chỉ sửa các lỗi *cấu trúc* (khoảng trắng ML Kit chèn giữa các element,
     * ký tự không thuộc bảng chữ MRZ). Việc phân biệt chữ/số phải làm theo
     * từng vị trí trường trong [smartCleanMrzLine] - thay O→0 toàn cục sẽ
     * phá hỏng phần họ tên ở line 3.
     */
    fun cleanOcrText(text: String): String {
        val sb = StringBuilder(text.length)
        for (c in text.uppercase()) {
            when {
                c.isWhitespace() -> Unit
                // Guillemet là lỗi OCR rất phổ biến của cặp '<<'
                c == '«' || c == '»' -> sb.append("<<")
                c in MRZ_ALPHABET -> sb.append(c)
                // Mọi ký tự lạ khác ({, [, (, -, ...) đều là filler đọc sai.
                // Thay bằng '<' giữ nguyên độ dài nên các trường không bị lệch.
                else -> sb.append(FILLER)
            }
        }
        return sb.toString()
    }

    /**
     * Smart clean - ép kiểu ký tự theo đúng vị trí trường của TD1.
     *
     * Mỗi trường MRZ chỉ nhận chữ hoặc chỉ nhận số, nên biết vị trí là biết
     * chắc ký tự nào bị OCR đọc nhầm loại.
     */
    fun smartCleanMrzLine(line: String, lineNumber: Int): String {
        val chars = line.uppercase().toCharArray()

        fun toDigits(from: Int, to: Int) {
            for (i in from..to) if (i < chars.size) chars[i] = fixToDigit(chars[i])
        }

        fun toAlpha(from: Int, to: Int) {
            for (i in from..to) if (i < chars.size) chars[i] = fixToAlpha(chars[i])
        }

        when (lineNumber) {
            1 -> {
                // 0-1: document type, 2-4: issuing state → chỉ chữ
                toAlpha(0, 4)
                // 5-13: document number, 14: check digit.
                // CCCD Việt Nam toàn số nên chữ ở vùng này luôn là lỗi OCR.
                toDigits(5, 14)
            }

            2 -> {
                // 0-5: DOB, 6: check digit
                toDigits(0, 6)
                // 7: sex (M/F/X/<) - để nguyên
                // 8-13: DOE, 14: check digit
                toDigits(8, 14)
                // 15-17: nationality → chỉ chữ
                toAlpha(15, 17)
                // 29: composite check digit
                if (chars.size > 29) chars[29] = fixToDigit(chars[29])
            }

            3 -> {
                // Trường họ tên chỉ chứa A-Z và '<', mọi chữ số đều là lỗi OCR.
                toAlpha(0, chars.size - 1)
            }
        }

        return String(chars)
    }

    private fun fixToDigit(c: Char): Char {
        return when (c) {
            'O', 'D', 'Q' -> '0'
            'I', 'L', 'T' -> '1'
            'Z' -> '2'
            'A' -> '4'
            'S' -> '5'
            'G' -> '6'
            'B' -> '8'
            else -> c
        }
    }

    private fun fixToAlpha(c: Char): Char {
        return when (c) {
            '0' -> 'O'
            '1' -> 'I'
            '2' -> 'Z'
            '4' -> 'A'
            '5' -> 'S'
            '6' -> 'G'
            '8' -> 'B'
            else -> c
        }
    }
}
