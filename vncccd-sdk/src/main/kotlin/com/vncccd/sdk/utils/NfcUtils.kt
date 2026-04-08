package com.vncccd.sdk.utils

import android.content.Context
import android.nfc.NfcAdapter

/**
 * NFC utility functions.
 */
object NfcUtils {

    /**
     * Check if device supports NFC.
     */
    fun isNfcSupported(context: Context): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }

    /**
     * Check if NFC is enabled on device.
     */
    fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return adapter.isEnabled
    }

    /**
     * Get NFC adapter or null.
     */
    fun getNfcAdapter(context: Context): NfcAdapter? {
        return NfcAdapter.getDefaultAdapter(context)
    }
}
