package com.vncccd.sdk.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log

/**
 * Utility cho xử lý ảnh từ chip NFC.
 */
object ImageUtils {

    private const val TAG = "ImageUtils"

    /**
     * Decode byte array thành Bitmap.
     * Hỗ trợ JPEG, PNG. JPEG2000 cần xử lý riêng.
     */
    fun decodeBitmap(imageData: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap", e)
            null
        }
    }

    /**
     * Decode bitmap với options để giảm memory.
     */
    fun decodeBitmapSampled(imageData: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false

        return try {
            BitmapFactory.decodeByteArray(imageData, 0, imageData.size, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode sampled bitmap", e)
            null
        }
    }

    /**
     * Check if byte array is JPEG2000 format.
     * JP2 signature: 00 00 00 0C 6A 50 20 20 0D 0A 87 0A
     */
    fun isJpeg2000(data: ByteArray): Boolean {
        if (data.size < 12) return false
        return data[0] == 0x00.toByte() &&
                data[1] == 0x00.toByte() &&
                data[2] == 0x00.toByte() &&
                data[3] == 0x0C.toByte() &&
                data[4] == 0x6A.toByte() && // 'j'
                data[5] == 0x50.toByte()     // 'P'
    }

    /**
     * Check if byte array is standard JPEG format.
     * JPEG SOI marker: FF D8
     */
    fun isJpeg(data: ByteArray): Boolean {
        if (data.size < 2) return false
        return data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte()
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
