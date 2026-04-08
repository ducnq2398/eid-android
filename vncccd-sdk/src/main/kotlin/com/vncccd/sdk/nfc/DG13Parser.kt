package com.vncccd.sdk.nfc

import android.util.Log
import com.vncccd.sdk.models.PersonalInfo
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.text.Normalizer

/**
 * Parser cho DG13 (Data Group 13) đặc thù Việt Nam.
 *
 * DG13 của CCCD Việt Nam chứa thông tin cá nhân ở dạng TLV (Tag-Length-Value)
 * hoặc text-based key-value format.
 *
 * Cấu trúc DG13 Vietnam CCCD (dưới dạng chuỗi phân tách):
 * - Các trường phân tách bởi ký tự đặc biệt hoặc TLV encoding
 * - Encoding: UTF-8 (hỗ trợ tiếng Việt có dấu)
 */
object DG13Parser {

    private const val TAG = "DG13Parser"

    // TLV Tags cho DG13 Vietnam (estimated based on common implementations)
    private const val TAG_DG13 = 0x6D
    private const val TAG_ID_NUMBER = 0x01
    private const val TAG_FULL_NAME = 0x02
    private const val TAG_DATE_OF_BIRTH = 0x03
    private const val TAG_GENDER = 0x04
    private const val TAG_NATIONALITY = 0x05
    private const val TAG_ETHNICITY = 0x06
    private const val TAG_RELIGION = 0x07
    private const val TAG_PLACE_OF_ORIGIN = 0x08
    private const val TAG_PLACE_OF_RESIDENCE = 0x09
    private const val TAG_PERSONAL_ID = 0x0A
    private const val TAG_DATE_OF_ISSUE = 0x0B
    private const val TAG_DATE_OF_EXPIRY = 0x0C
    private const val TAG_FATHER_NAME = 0x0D
    private const val TAG_MOTHER_NAME = 0x0E
    private const val TAG_SPOUSE_NAME = 0x0F
    private const val TAG_OLD_ID_NUMBER = 0x10
    private const val IDX_FAMILY = 13
    private const val IDX_CARD_INFO = 14

    /**
     * Parse raw DG13 bytes thành PersonalInfo.
     * Thử nhiều phương thức parse: TLV, text-based, regex.
     */
    fun parse(rawBytes: ByteArray): PersonalInfo? {
        if (rawBytes.isEmpty()) return null

        return try {
            // Method 0: Vietnam DG13 segmented format (0x30 .. 0x02 0x01 idx)
            val segmentedResult = parseVietnamSegmented(rawBytes)
            if (segmentedResult != null && segmentedResult.hasData()) {
                Log.d(TAG, "Parsed using Vietnam segmented method")
                return segmentedResult
            }

            // Method 1: Try TLV parsing
            val tlvResult = parseTLV(rawBytes)
            if (tlvResult != null && tlvResult.hasData()) {
                Log.d(TAG, "Parsed using TLV method")
                return tlvResult
            }

            // Method 2: Try text-based parsing (some CCCD use plain text format)
            val textResult = parseTextBased(rawBytes)
            if (textResult != null && textResult.hasData()) {
                Log.d(TAG, "Parsed using text-based method")
                return textResult
            }

            // Method 3: Best effort - extract any recognizable Vietnamese text
            val fallbackResult = parseFallback(rawBytes)
            Log.d(TAG, "Parsed using fallback method")
            fallbackResult
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing DG13", e)
            null
        }
    }

    /**
     * Parse theo format DG13 VN thường gặp:
     * segment mở đầu bằng: 30 xx 02 01 {idx}
     */
    private fun parseVietnamSegmented(rawBytes: ByteArray): PersonalInfo? {
        if (rawBytes.size < 8) return null

        val end = findEndOfData(rawBytes)
        val data = rawBytes.copyOf(end)
        val separators = mutableListOf<Int>()
        var expectedIdx = 1

        for (i in 0..data.size - 5) {
            val b0 = data[i].toInt() and 0xFF
            val b2 = data[i + 2].toInt() and 0xFF
            val b3 = data[i + 3].toInt() and 0xFF
            val b4 = data[i + 4].toInt() and 0xFF
            if (b0 == 0x30 && b2 == 0x02 && b3 == 0x01 && b4 == expectedIdx) {
                separators += i
                expectedIdx++
                if (expectedIdx > 20) break
            }
        }

        if (separators.size < 3) return null
        separators += data.size

        var idNumber: String? = null
        var fullName: String? = null
        var dateOfBirth: String? = null
        var gender: String? = null
        var nationality: String? = null
        var ethnicity: String? = null
        var religion: String? = null
        var placeOfOrigin: String? = null
        var placeOfResidence: String? = null
        var personalIdentification: String? = null
        var dateOfIssue: String? = null
        var dateOfExpiry: String? = null
        var fatherName: String? = null
        var motherName: String? = null
        var oldIdNumber: String? = null

        for (i in 0 until separators.size - 1) {
            val start = separators[i]
            val next = separators[i + 1]
            if (next - start < 6) continue
            val subset = data.copyOfRange(start, next)
            val idx = subset[4].toInt() and 0xFF

            if (idx == IDX_CARD_INFO) continue // thường rỗng

            val payload = subset.copyOfRange(5, subset.size)
            val texts = extractTextFields(payload)
            if (texts.isEmpty()) continue

            when (idx) {
                TAG_ID_NUMBER -> idNumber = normalizeId(texts.first())
                TAG_FULL_NAME -> fullName = normalizeName(texts.first())
                TAG_DATE_OF_BIRTH -> dateOfBirth = normalizeDate(texts.first())
                TAG_GENDER -> gender = normalizeGender(texts.first())
                TAG_NATIONALITY -> nationality = normalizeShortText(texts.first(), 32)
                TAG_ETHNICITY -> ethnicity = normalizeShortText(texts.first(), 64)
                TAG_RELIGION -> religion = normalizeShortText(texts.first(), 64)
                TAG_PLACE_OF_ORIGIN -> placeOfOrigin = normalizeAddress(texts.first())
                TAG_PLACE_OF_RESIDENCE -> placeOfResidence = normalizeAddress(texts.first())
                TAG_PERSONAL_ID -> personalIdentification = normalizeAddress(texts.first())
                TAG_DATE_OF_ISSUE -> dateOfIssue = normalizeDate(texts.first())
                TAG_DATE_OF_EXPIRY -> dateOfExpiry = normalizeDate(texts.first())
                IDX_FAMILY -> {
                    fatherName = normalizeName(texts.getOrNull(0))
                    motherName = normalizeName(texts.getOrNull(1))
                }
                TAG_OLD_ID_NUMBER -> oldIdNumber = normalizeId(texts.first())
            }
        }

        val info = PersonalInfo(
            idNumber = idNumber,
            fullName = fullName,
            dateOfBirth = dateOfBirth,
            gender = gender,
            nationality = nationality,
            ethnicity = ethnicity,
            religion = religion,
            placeOfOrigin = placeOfOrigin,
            placeOfResidence = placeOfResidence,
            personalIdentification = personalIdentification,
            dateOfIssue = dateOfIssue,
            dateOfExpiry = dateOfExpiry,
            fatherName = fatherName,
            motherName = motherName,
            oldIdNumber = oldIdNumber
        )
        return info.takeIf { it.hasData() }
    }

    /**
     * Parse DG13 using TLV (Tag-Length-Value) encoding.
     */
    private fun parseTLV(rawBytes: ByteArray): PersonalInfo? {
        return try {
            val topLevel = parseBerTlvList(rawBytes)
            if (topLevel.isEmpty()) return null

            val effectiveTlvs = if (topLevel.size == 1 && topLevel[0].tag == TAG_DG13) {
                parseBerTlvList(topLevel[0].value)
            } else {
                topLevel
            }

            val fieldsByTag = linkedMapOf<Int, String>()
            collectPrimitiveFields(effectiveTlvs, fieldsByTag)
            if (fieldsByTag.isEmpty()) return null

            val personalInfo = PersonalInfo(
                idNumber = normalizeId(findTagValue(fieldsByTag, TAG_ID_NUMBER)),
                fullName = normalizeName(findTagValue(fieldsByTag, TAG_FULL_NAME)),
                dateOfBirth = normalizeDate(findTagValue(fieldsByTag, TAG_DATE_OF_BIRTH)),
                gender = normalizeGender(findTagValue(fieldsByTag, TAG_GENDER)),
                nationality = normalizeShortText(findTagValue(fieldsByTag, TAG_NATIONALITY), maxLen = 32),
                ethnicity = normalizeShortText(findTagValue(fieldsByTag, TAG_ETHNICITY), maxLen = 64),
                religion = normalizeShortText(findTagValue(fieldsByTag, TAG_RELIGION), maxLen = 64),
                placeOfOrigin = normalizeAddress(findTagValue(fieldsByTag, TAG_PLACE_OF_ORIGIN)),
                placeOfResidence = normalizeAddress(findTagValue(fieldsByTag, TAG_PLACE_OF_RESIDENCE)),
                personalIdentification = normalizeAddress(findTagValue(fieldsByTag, TAG_PERSONAL_ID)),
                dateOfIssue = normalizeDate(findTagValue(fieldsByTag, TAG_DATE_OF_ISSUE)),
                dateOfExpiry = normalizeDate(findTagValue(fieldsByTag, TAG_DATE_OF_EXPIRY)),
                fatherName = normalizeName(findTagValue(fieldsByTag, TAG_FATHER_NAME)),
                motherName = normalizeName(findTagValue(fieldsByTag, TAG_MOTHER_NAME)),
                spouseName = normalizeName(findTagValue(fieldsByTag, TAG_SPOUSE_NAME)),
                oldIdNumber = normalizeId(findTagValue(fieldsByTag, TAG_OLD_ID_NUMBER))
            )

            if (personalInfo.hasData()) personalInfo else null
        } catch (e: Exception) {
            Log.w(TAG, "TLV parsing error", e)
            null
        }
    }

    /**
     * Parse DG13 dưới dạng text phân tách.
     * Một số CCCD encode DG13 dưới dạng text với separator.
     */
    private fun parseTextBased(rawBytes: ByteArray): PersonalInfo? {
        val text = decodeBestEffort(rawBytes)
        if (text.isBlank()) return null

        val keyValueResult = parseKeyValueText(text)
        if (keyValueResult?.hasData() == true) {
            return keyValueResult
        }

        // Try different separators
        val separators = listOf("||", "|", "\r\n", "\n", ";", "#")

        for (sep in separators) {
            val parts = text.split(sep).map { it.trim() }.filter { it.isNotBlank() }
            if (parts.size >= 5) {
                return parseFieldList(parts)
            }
        }

        return null
    }

    /**
     * Parse danh sách các trường thông tin.
     * Thứ tự phổ biến: CCCD Number, Name, DOB, Gender, Nationality,
     * Ethnicity, Religion, Place of Origin, Place of Residence, ...
     */
    private fun parseFieldList(fields: List<String>): PersonalInfo {
        return PersonalInfo(
            idNumber = normalizeId(fields.getOrNull(0)),
            fullName = normalizeName(fields.getOrNull(1)),
            dateOfBirth = normalizeDate(fields.getOrNull(2)),
            gender = normalizeGender(fields.getOrNull(3)),
            nationality = normalizeShortText(fields.getOrNull(4), 32),
            ethnicity = normalizeShortText(fields.getOrNull(5), 64),
            religion = normalizeShortText(fields.getOrNull(6), 64),
            placeOfOrigin = normalizeAddress(fields.getOrNull(7)),
            placeOfResidence = normalizeAddress(fields.getOrNull(8)),
            personalIdentification = normalizeAddress(fields.getOrNull(9)),
            dateOfIssue = normalizeDate(fields.getOrNull(10)),
            dateOfExpiry = normalizeDate(fields.getOrNull(11)),
            fatherName = normalizeName(fields.getOrNull(12)),
            motherName = normalizeName(fields.getOrNull(13)),
            spouseName = normalizeName(fields.getOrNull(14)),
            oldIdNumber = normalizeId(fields.getOrNull(15))
        )
    }

    /**
     * Fallback parser - trích xuất thông tin bằng regex patterns.
     */
    private fun parseFallback(rawBytes: ByteArray): PersonalInfo? {
        val text = decodeBestEffort(rawBytes)
        if (text.isBlank()) return null

        // Try to extract 12-digit CCCD number
        val idPattern = Regex("\\d{12}")
        val idMatch = idPattern.find(text)

        // Try to extract dates (dd/MM/yyyy, dd-MM-yyyy, yyyyMMdd)
        val datePattern = Regex("(\\d{2}[/.-]\\d{2}[/.-]\\d{4}|\\d{8})")
        val dates = datePattern.findAll(text).map { it.value }.toList()

        return PersonalInfo(
            idNumber = normalizeId(idMatch?.value),
            dateOfBirth = normalizeDate(dates.getOrNull(0)),
            dateOfIssue = normalizeDate(dates.getOrNull(1)),
            dateOfExpiry = normalizeDate(dates.getOrNull(2))
        )
    }

    /**
     * Decode raw bytes to text với nhiều charset fallback.
     */
    private fun decodeBestEffort(rawBytes: ByteArray): String {
        if (rawBytes.isEmpty()) return ""

        val payload = stripOuterTlv(rawBytes)
        val candidates = listOf(
            Charset.forName("UTF-8"),
            Charset.forName("windows-1258"),
            Charset.forName("windows-1252"),
            Charset.forName("UTF-16LE"),
            Charset.forName("UTF-16BE"),
            Charset.forName("ISO-8859-1")
        )

        var best = ""
        var bestScore = -1
        for (charset in candidates) {
            val decoded = runCatching { String(payload, charset) }.getOrElse { "" }
            val cleaned = cleanupText(decoded)
            val score = textScore(cleaned)
            if (score > bestScore) {
                bestScore = score
                best = cleaned
            }
        }
        return best
    }

    private fun stripOuterTlv(rawBytes: ByteArray): ByteArray {
        val top = parseBerTlvList(rawBytes)
        if (top.size == 1 && top[0].tag == TAG_DG13) {
            return top[0].value
        }
        return rawBytes
    }

    private data class BerTlv(val tag: Int, val constructed: Boolean, val value: ByteArray)

    private fun parseBerTlvList(bytes: ByteArray): List<BerTlv> {
        val tlvs = mutableListOf<BerTlv>()
        val input = ByteArrayInputStream(bytes)

        while (input.available() > 0) {
            val first = input.read()
            if (first == -1) break

            var tag = first and 0xFF
            if ((first and 0x1F) == 0x1F) {
                do {
                    val next = input.read()
                    if (next == -1) return tlvs
                    tag = (tag shl 8) or (next and 0xFF)
                } while ((tag and 0x80) == 0x80)
            }

            val length = readBerLength(input) ?: break
            if (length > input.available() || length < 0) break

            val value = ByteArray(length)
            val read = input.read(value)
            if (read != length) break

            tlvs += BerTlv(
                tag = tag,
                constructed = ((first and 0x20) != 0),
                value = value
            )
        }

        return tlvs
    }

    private fun readBerLength(input: ByteArrayInputStream): Int? {
        val first = input.read()
        if (first == -1) return null
        if (first and 0x80 == 0) return first

        val numBytes = first and 0x7F
        if (numBytes == 0 || numBytes > 4 || numBytes > input.available()) return null

        var len = 0
        repeat(numBytes) {
            val b = input.read()
            if (b == -1) return null
            len = (len shl 8) or (b and 0xFF)
        }
        return len
    }

    private fun extractTextFields(payload: ByteArray): List<String> {
        if (payload.isEmpty()) return emptyList()

        val rootTlvs = parseBerTlvList(payload)
        if (rootTlvs.isEmpty()) {
            val fallback = normalizeShortText(decodePrimitiveBestEffort(payload), 200)
            return listOfNotNull(fallback)
        }

        val result = mutableListOf<String>()
        fun walk(tlvs: List<BerTlv>) {
            for (tlv in tlvs) {
                if (tlv.constructed) {
                    walk(parseBerTlvList(tlv.value))
                } else {
                    val text = normalizeShortText(decodePrimitiveBestEffort(tlv.value), 200)
                    if (text != null) result += text
                }
            }
        }
        walk(rootTlvs)
        return result
    }

    private fun findEndOfData(bytes: ByteArray): Int {
        for (i in 0..bytes.size - 4) {
            if (bytes[i] == 0.toByte() &&
                bytes[i + 1] == 0.toByte() &&
                bytes[i + 2] == 0.toByte() &&
                bytes[i + 3] == 0.toByte()
            ) {
                return i
            }
        }
        return bytes.size
    }

    private fun collectPrimitiveFields(tlvs: List<BerTlv>, fieldsByTag: MutableMap<Int, String>) {
        for (tlv in tlvs) {
            if (tlv.constructed) {
                collectPrimitiveFields(parseBerTlvList(tlv.value), fieldsByTag)
                continue
            }

            val decoded = decodePrimitiveBestEffort(tlv.value)
            if (decoded.isBlank() || !isLikelyText(decoded)) continue
            fieldsByTag.putIfAbsent(tlv.tag, decoded.trim())
        }
    }

    private fun findTagValue(fieldsByTag: Map<Int, String>, expectedTag: Int): String? {
        fieldsByTag[expectedTag]?.let { return it }
        return fieldsByTag.entries
            .firstOrNull { (it.key and 0xFF) == expectedTag && isLikelyText(it.value) }
            ?.value
    }

    private fun parseKeyValueText(text: String): PersonalInfo? {
        val lines = text.split("\r\n", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val map = mutableMapOf<String, String>()
        for (line in lines) {
            val idx = line.indexOf(':').takeIf { it > 0 }
                ?: line.indexOf('-').takeIf { it > 0 }
                ?: continue
            val key = normalizeKey(line.substring(0, idx))
            val value = line.substring(idx + 1).trim()
            if (value.isNotBlank()) map[key] = value
        }
        if (map.isEmpty()) return null

        fun pick(vararg keys: String): String? =
            keys.firstNotNullOfOrNull { map[it] }?.trim()?.takeIf { it.isNotBlank() }

        val info = PersonalInfo(
            idNumber = normalizeId(pick("so_cccd", "so_dinh_danh", "id_number")),
            fullName = normalizeName(pick("ho_ten", "ho_va_ten", "full_name")),
            dateOfBirth = normalizeDate(pick("ngay_sinh", "dob", "date_of_birth")),
            gender = normalizeGender(pick("gioi_tinh", "gender", "sex")),
            nationality = normalizeShortText(pick("quoc_tich", "nationality"), 32),
            ethnicity = normalizeShortText(pick("dan_toc", "ethnicity"), 64),
            religion = normalizeShortText(pick("ton_giao", "religion"), 64),
            placeOfOrigin = normalizeAddress(pick("que_quan", "noi_sinh", "place_of_origin")),
            placeOfResidence = normalizeAddress(pick("noi_thuong_tru", "thuong_tru", "dia_chi", "place_of_residence")),
            personalIdentification = normalizeAddress(pick("dac_diem_nhan_dang", "personal_identification")),
            dateOfIssue = normalizeDate(pick("ngay_cap", "date_of_issue")),
            dateOfExpiry = normalizeDate(pick("ngay_het_han", "gia_tri_den", "date_of_expiry")),
            fatherName = normalizeName(pick("ho_ten_cha", "father_name")),
            motherName = normalizeName(pick("ho_ten_me", "mother_name")),
            spouseName = normalizeName(pick("ho_ten_vo_chong", "spouse_name")),
            oldIdNumber = normalizeId(pick("so_cmnd_cu", "old_id_number"))
        )
        return info.takeIf { it.hasData() }
    }

    private fun normalizeKey(key: String): String {
        val ascii = Normalizer.normalize(key.lowercase(), Normalizer.Form.NFD)
            .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
            .replace('đ', 'd')
        return ascii.replace("[^a-z0-9]+".toRegex(), "_").trim('_')
    }

    private fun normalizeId(value: String?): String? {
        val digits = value?.filter { it.isDigit() } ?: return null
        return digits.takeIf { it.length >= 9 }
    }

    private fun normalizeDate(value: String?): String? {
        val raw = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (raw.matches(Regex("\\d{2}[/.-]\\d{2}[/.-]\\d{4}"))) {
            return raw.replace('.', '/').replace('-', '/')
        }
        if (raw.matches(Regex("\\d{8}"))) {
            val yyyy = raw.substring(0, 4)
            return if (yyyy.toIntOrNull() in 1900..2200) {
                "${raw.substring(6, 8)}/${raw.substring(4, 6)}/$yyyy"
            } else {
                "${raw.substring(0, 2)}/${raw.substring(2, 4)}/${raw.substring(4, 8)}"
            }
        }
        return raw
    }

    private fun normalizeGender(value: String?): String? {
        val normalized = value?.trim()?.uppercase() ?: return null
        return when {
            normalized == "M" || normalized.contains("NAM") || normalized == "MALE" -> "Nam"
            normalized == "F" || normalized.contains("NU") || normalized == "FEMALE" -> "Nữ"
            else -> value.trim()
        }
    }

    private fun normalizeName(value: String?): String? {
        val cleaned = normalizeShortText(value, maxLen = 120) ?: return null
        return cleaned.takeIf { it.containsLetter() }
    }

    private fun normalizeAddress(value: String?): String? {
        return normalizeShortText(value, maxLen = 200)
    }

    private fun normalizeShortText(value: String?, maxLen: Int): String? {
        val cleaned = cleanupText(value).takeIf { it.isNotBlank() } ?: return null
        if (!isLikelyText(cleaned)) return null
        if (cleaned.length > maxLen) return null
        return cleaned
    }

    private fun cleanupText(value: String?): String {
        if (value == null) return ""
        val cleaned = value
            .replace('\u0000', ' ')
            .replace(Regex("[\\p{Cntrl}&&[^\\n\\r\\t]]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return Normalizer.normalize(repairVietnameseMojibake(cleaned), Normalizer.Form.NFC)
    }

    private fun decodePrimitiveBestEffort(rawBytes: ByteArray): String {
        if (rawBytes.isEmpty()) return ""
        val candidates = listOf(
            Charset.forName("UTF-8"),
            Charset.forName("windows-1258"),
            Charset.forName("windows-1252"),
            Charset.forName("UTF-16LE"),
            Charset.forName("UTF-16BE"),
            Charset.forName("ISO-8859-1")
        )

        var best = ""
        var bestScore = -1
        for (charset in candidates) {
            val decoded = runCatching { String(rawBytes, charset) }.getOrElse { "" }
            val cleaned = cleanupText(decoded)
            val score = textScore(cleaned)
            if (score > bestScore) {
                bestScore = score
                best = cleaned
            }
        }
        return best
    }

    private fun repairVietnameseMojibake(text: String): String {
        if (text.isBlank()) return text

        val candidates = listOf(
            text,
            convertCharset(text, "ISO-8859-1", "UTF-8"),
            convertCharset(text, "windows-1252", "UTF-8"),
            convertCharset(text, "ISO-8859-1", "windows-1258"),
            convertCharset(text, "windows-1252", "windows-1258")
        )

        return candidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .maxByOrNull { textScore(it) }
            ?: text
    }

    private fun convertCharset(text: String, from: String, to: String): String {
        return runCatching {
            String(text.toByteArray(Charset.forName(from)), Charset.forName(to))
        }.getOrElse { text }
    }

    private fun textScore(text: String): Int {
        if (text.isBlank()) return Int.MIN_VALUE / 4
        val base = text.count { it.isLetterOrDigit() || it in " /.-|:;,\n\r" }
        val viBonus = text.count { it in "ăâđêôơưĂÂĐÊÔƠƯáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệóòỏõọốồổỗộớờởỡợúùủũụứừửữựíìỉĩịýỳỷỹỵÁÀẢÃẠẮẰẲẴẶẤẦẨẪẬÉÈẺẼẸẾỀỂỄỆÓÒỎÕỌỐỒỔỖỘỚỜỞỠỢÚÙỦŨỤỨỪỬỮỰÍÌỈĨỊÝỲỶỸỴ" }
        val mojibakePenalty = text.count { it in "ÃÂÄÅÆÇÐÑØÞßáðñóôõö÷øùúûüýþÿ�" } * 2
        return base + viBonus * 3 - mojibakePenalty
    }

    private fun isLikelyText(value: String): Boolean {
        if (value.isBlank()) return false
        val cleaned = cleanupText(value)
        if (cleaned.isBlank()) return false
        val printable = cleaned.count { !it.isISOControl() }
        if (printable == 0) return false
        val ratio = printable.toDouble() / cleaned.length.coerceAtLeast(1)
        if (ratio < 0.85) return false
        val weird = cleaned.count { it.code in 0xE000..0xF8FF }
        if (weird > 0) return false
        return true
    }

    private fun String.containsLetter(): Boolean {
        return any { it.isLetter() }
    }

    /**
     * Check if PersonalInfo has any meaningful data.
     */
    private fun PersonalInfo.hasData(): Boolean {
        return !fullName.isNullOrBlank() || !idNumber.isNullOrBlank()
    }
}
