package chat.stoat.c2dm

import android.content.Context
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import logcat.LogPriority
import logcat.asLog
import logcat.logcat

object WebPushKeyManager {
    private const val PREFS_NAME = "dismod_webpush_keys"
    private const val KEY_P256DH = "p256dh"
    private const val KEY_AUTH = "auth_secret"

    /**
     * Retrieves cached or generates new NIST P-256 (secp256r1) keypair and auth secret.
     * Returns Pair(p256dh, auth) as URL-safe base64 strings without padding.
     */
    fun getOrCreateKeys(context: Context): Pair<String, String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val p256dh = prefs.getString(KEY_P256DH, null)
        val auth = prefs.getString(KEY_AUTH, null)
        if (!p256dh.isNullOrEmpty() && !auth.isNullOrEmpty()) {
            return Pair(p256dh, auth)
        }

        return try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = kpg.generateKeyPair()

            val ecPubKey = keyPair.public as ECPublicKey
            val w = ecPubKey.w
            val xBytes = toUnsignedFixedLength(w.affineX.toByteArray(), 32)
            val yBytes = toUnsignedFixedLength(w.affineY.toByteArray(), 32)

            // ANSI X9.62 uncompressed point format: 0x04 || X || Y
            val rawPoint = ByteArray(65)
            rawPoint[0] = 0x04
            System.arraycopy(xBytes, 0, rawPoint, 1, 32)
            System.arraycopy(yBytes, 0, rawPoint, 33, 32)
            val p256dhBase64 = Base64.encodeToString(
                rawPoint,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            // 16-byte random authentication secret
            val authBytes = ByteArray(16)
            SecureRandom().nextBytes(authBytes)
            val authBase64 = Base64.encodeToString(
                authBytes,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            prefs.edit()
                .putString(KEY_P256DH, p256dhBase64)
                .putString(KEY_AUTH, authBase64)
                .apply()

            Pair(p256dhBase64, authBase64)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR) { "Failed to generate Web Push keys: ${e.asLog()}" }
            Pair("", "")
        }
    }

    private fun toUnsignedFixedLength(src: ByteArray, length: Int): ByteArray {
        val result = ByteArray(length)
        var srcOffset = 0
        var srcLength = src.size

        if (srcLength > length && src[0] == 0.toByte()) {
            srcOffset = 1
            srcLength--
        }

        val destOffset = (length - srcLength).coerceAtLeast(0)
        val copyLength = minOf(srcLength, length)
        System.arraycopy(src, srcOffset, result, destOffset, copyLength)
        return result
    }
}
