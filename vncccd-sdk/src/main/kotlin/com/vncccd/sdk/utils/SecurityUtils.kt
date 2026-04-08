package com.vncccd.sdk.utils

import android.util.Log
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Security provider utility cho JMRTD.
 * BouncyCastle cần được cài đặt để hỗ trợ các thuật toán crypto
 * cần thiết cho BAC/PACE authentication.
 */
object SecurityUtils {

    private const val TAG = "SecurityUtils"
    private var isInstalled = false

    /**
     * Install BouncyCastle security provider.
     * An toàn để gọi nhiều lần (idempotent).
     */
    @Synchronized
    fun installSecurityProvider() {
        if (isInstalled) return

        try {
            // Remove existing BC provider (Android may have an older version)
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)

            // Insert BouncyCastle at position 1 (highest priority)
            val position = Security.insertProviderAt(BouncyCastleProvider(), 1)

            if (position != -1) {
                Log.d(TAG, "BouncyCastle security provider installed at position $position")
                isInstalled = true
            } else {
                Log.w(TAG, "BouncyCastle security provider already exists")
                isInstalled = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install BouncyCastle security provider", e)
        }
    }

    /**
     * Check if BouncyCastle is properly installed.
     */
    fun isSecurityProviderInstalled(): Boolean {
        return Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) != null
    }
}
