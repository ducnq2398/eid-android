package com.vncccd.sdk.nfc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.nfc.tech.IsoDep
import android.util.Base64
import android.util.Log
import com.vncccd.sdk.CCCDConfig
import com.vncccd.sdk.models.*
import com.vncccd.sdk.utils.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.scuba.smartcards.CardService
import net.sf.scuba.smartcards.CardServiceException
import org.jmrtd.BACKey
import org.jmrtd.BACKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.CardSecurityFile
import org.jmrtd.lds.PACEInfo
import org.jmrtd.lds.icao.DG1File
import org.jmrtd.lds.icao.DG2File
import org.jmrtd.lds.iso19794.FaceImageInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream

/**
 * Core NFC card reader sử dụng JMRTD.
 * Hỗ trợ BAC và PACE authentication, đọc DG1, DG2, DG13.
 */
class NfcCardReader {

    companion object {
        private const val TAG = "NfcCardReader"
        private const val MAX_BLOCK_SIZE = 224 // JMRTD default
    }

    private var passportService: PassportService? = null

    /**
     * Đọc toàn bộ dữ liệu từ chip CCCD.
     *
     * @param isoDep NFC IsoDep connection
     * @param mrzData MRZ data đã scan
     * @param config SDK configuration
     * @param onProgress Progress callback
     * @return CCCDData chứa toàn bộ dữ liệu đọc được
     */
    suspend fun readCard(
        isoDep: IsoDep,
        mrzData: MrzData,
        config: CCCDConfig,
        onProgress: (ReadingStatus) -> Unit
    ): CCCDData = withContext(Dispatchers.IO) {

        // Ensure security provider
        SecurityUtils.installSecurityProvider()

        // Configure IsoDep
        isoDep.timeout = config.nfcTimeoutMs.toInt()

        onProgress(ReadingStatus.CONNECTING)

        // Create JMRTD CardService from IsoDep
        val cardService = CardService.getInstance(isoDep)
        val ps = PassportService(
            cardService,
            PassportService.NORMAL_MAX_TRANCEIVE_LENGTH,
            PassportService.DEFAULT_MAX_BLOCKSIZE,
            false,
            false
        )

        passportService = ps

        try {
            ps.open()

            // Authentication
            onProgress(ReadingStatus.AUTHENTICATING)
            authenticate(ps, mrzData)

            // Read DG1
            onProgress(ReadingStatus.READING_DG1)
            val dg1Result = readDG1(ps)

            // Read DG2 (face image) if enabled
            var faceImageBase64: String? = null
            var rawDG2: ByteArray? = null
            if (config.readFaceImage) {
                onProgress(ReadingStatus.READING_DG2)
                val dg2Result = readDG2(ps)
                faceImageBase64 = encodeBitmapToBase64(dg2Result.first)
                rawDG2 = dg2Result.second
            }

            // Read DG13 (personal info) if enabled
            var personalInfo: PersonalInfo? = null
            var rawDG13: ByteArray? = null
            if (config.readPersonalInfo) {
                onProgress(ReadingStatus.READING_DG13)
                val dg13Result = readDG13(ps)
                personalInfo = dg13Result.first
                rawDG13 = dg13Result.second
            }

            onProgress(ReadingStatus.COMPLETED)

            CCCDData(
                mrzData = mrzData,
                personalInfo = personalInfo,
                faceImageBase64 = faceImageBase64,
                rawDG1 = dg1Result.second,
                rawDG2 = rawDG2,
                rawDG13 = rawDG13,
                isPassiveAuthSuccess = true
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error reading card", e)
            onProgress(ReadingStatus.ERROR)
            throw e
        } finally {
            try {
                ps.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing passport service", e)
            }
        }
    }

    /**
     * Thực hiện authentication (try PACE first, fallback to BAC).
     */
    private fun authenticate(ps: PassportService, mrzData: MrzData) {
        val fullDoc = mrzData.fullDocumentNumber.replace("<", "").trim()
        val fullDocDigits = fullDoc.filter { it.isDigit() }
        val dob = mrzData.dateOfBirth.filter { it.isDigit() }.take(6)
        val doe = mrzData.dateOfExpiry.filter { it.isDigit() }.take(6)
        if (dob.length != 6 || doe.length != 6) {
            throw CardServiceException("Invalid MRZ dates for BAC key: dob=$dob, doe=$doe")
        }

        val documentCandidates = linkedSetOf<String>()
        if (fullDocDigits.length >= 12) documentCandidates.add(fullDocDigits.take(12))
        if (fullDoc.isNotBlank()) documentCandidates.add(fullDoc)

        val rawDocument = mrzData.documentNumber.replace("<", "").trim()
        if (rawDocument.isNotBlank()) {
            documentCandidates.add(rawDocument)
            documentCandidates.add(rawDocument.take(9))
        }
        if (fullDocDigits.length >= 9) {
            documentCandidates.add(fullDocDigits.take(9))
        }

        var lastError: Exception? = null

        for (documentNumber in documentCandidates.filter { it.isNotBlank() }) {
            val bacKey: BACKeySpec = BACKey(documentNumber, dob, doe)
            Log.d(TAG, "Trying authentication with MRZ key variant: $documentNumber")

            var paceSucceeded = false
            ps.sendSelectApplet(false)
            try {
                val cardSecurityFile = CardSecurityFile(
                    ps.getInputStream(PassportService.EF_CARD_SECURITY)
                )
                val securityInfos = cardSecurityFile.securityInfos
                val paceInfo = securityInfos?.filterIsInstance<PACEInfo>()?.firstOrNull()

                if (paceInfo != null) {
                    ps.doPACE(
                        bacKey,
                        paceInfo.objectIdentifier,
                        PACEInfo.toParameterSpec(paceInfo.parameterId),
                        paceInfo.parameterId
                    )
                    ps.sendSelectApplet(true)
                    paceSucceeded = true
                    Log.d(TAG, "PACE authentication succeeded")
                    return
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "PACE failed with current MRZ key variant", e)
            }

            if (!paceSucceeded) {
                try {
                    ps.sendSelectApplet(false)
                    ps.doBAC(bacKey)
                    Log.d(TAG, "BAC authentication succeeded")
                    return
                } catch (e: Exception) {
                    lastError = e
                    Log.w(TAG, "BAC failed with current MRZ key variant", e)
                }
            }
        }

        throw CardServiceException(
            "Authentication failed with all MRZ key variants",
            lastError
        )
    }

    /**
     * Đọc DG1 (MRZ Information).
     * @return Pair(DG1File, raw bytes)
     */
    private fun readDG1(ps: PassportService): Pair<DG1File?, ByteArray?> {
        return try {
            val inputStream = ps.getInputStream(PassportService.EF_DG1)
            val rawBytes = inputStream.readBytes()

            val dg1File = DG1File(ByteArrayInputStream(rawBytes))
            Log.d(TAG, "DG1 read successfully. MRZ: ${dg1File.mrzInfo}")

            Pair(dg1File, rawBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading DG1", e)
            Pair(null, null)
        }
    }

    /**
     * Đọc DG2 (Face Image / Portrait).
     * @return Pair(Bitmap?, raw bytes?)
     */
    private fun readDG2(ps: PassportService): Pair<Bitmap?, ByteArray?> {
        return try {
            val inputStream = ps.getInputStream(PassportService.EF_DG2)
            val rawBytes = inputStream.readBytes()

            val dg2File = DG2File(ByteArrayInputStream(rawBytes))
            val faceInfos = dg2File.faceInfos

            if (faceInfos.isNotEmpty()) {
                val faceImageInfos = faceInfos[0].faceImageInfos
                if (faceImageInfos.isNotEmpty()) {
                    val faceImageInfo = faceImageInfos[0]
                    val bitmap = decodeFaceImage(faceImageInfo)
                    Log.d(TAG, "DG2 face image decoded: ${bitmap?.width}x${bitmap?.height}")
                    return Pair(bitmap, rawBytes)
                }
            }

            Log.w(TAG, "No face image found in DG2")
            Pair(null, rawBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading DG2", e)
            Pair(null, null)
        }
    }

    /**
     * Decode face image from FaceImageInfo.
     * Supports JPEG and tries to handle JPEG2000.
     */
    private fun decodeFaceImage(faceImageInfo: FaceImageInfo): Bitmap? {
        return try {
            val imageLength = faceImageInfo.imageLength
            val dataInputStream = DataInputStream(faceImageInfo.imageInputStream)
            val buffer = ByteArray(imageLength)
            dataInputStream.readFully(buffer)

            // Try standard JPEG decode first
            val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
            if (bitmap != null) {
                return bitmap
            }

            // If JPEG2000 (JP2), try to find JPEG data inside
            // JP2 files start with 0x00 0x00 0x00 0x0C 0x6A 0x50
            if (buffer.size > 6 && buffer[4] == 0x6A.toByte() && buffer[5] == 0x50.toByte()) {
                Log.w(TAG, "JPEG2000 format detected. Raw bytes available but may not decode on all devices.")
                // Try decoding anyway (some devices support JP2)
                BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding face image", e)
            null
        }
    }

    private fun encodeBitmapToBase64(bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        return try {
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding face image to Base64", e)
            null
        }
    }

    /**
     * Đọc DG13 (Vietnam-specific personal information).
     * @return Pair(PersonalInfo?, raw bytes?)
     */
    private fun readDG13(ps: PassportService): Pair<PersonalInfo?, ByteArray?> {
        return try {
            val inputStream = ps.getInputStream(PassportService.EF_DG13)
            val rawBytes = inputStream.readBytes()

            val personalInfo = DG13Parser.parse(rawBytes)
            Log.d(TAG, "DG13 parsed: ${personalInfo?.fullName}")

            Pair(personalInfo, rawBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading DG13", e)
            Pair(null, null)
        }
    }

    /**
     * Close connection.
     */
    fun close() {
        try {
            passportService?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing card reader", e)
        }
    }
}
