package chat.stoat.logging

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end client-side encryption for Dismod cloud logs using AES-256-CBC.
 * Protects cloud log dumps so that third parties or public visitors cannot read them.
 * The AI assistant and developer can automatically decrypt using the shared password.
 */
object LogEncryptor {
    const val DEFAULT_PASSWORD = "DismodLogs#2026"

    fun deriveKey(password: String): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(password.toByteArray(Charsets.UTF_8))
    }

    /**
     * Encrypts plaintext log content into a structured JSON string containing IV and AES ciphertext.
     */
    fun encrypt(plainText: String, password: String = DEFAULT_PASSWORD): String {
        val keyBytes = deriveKey(password)
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val ivBytes = ByteArray(16)
        SecureRandom().nextBytes(ivBytes)
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)

        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val ivBase64 = Base64.getEncoder().encodeToString(ivBytes)
        val cipherBase64 = Base64.getEncoder().encodeToString(cipherBytes)

        return """{"dismod_encrypted":true,"v":1,"alg":"AES-256-CBC","iv":"$ivBase64","data":"$cipherBase64"}"""
    }

    /**
     * Decrypts an encrypted log JSON string using the provided password.
     * If the payload is not encrypted, returns the original text unmodified.
     */
    fun decrypt(encryptedJson: String, password: String = DEFAULT_PASSWORD): String {
        val trimmed = encryptedJson.trim()
        if (!trimmed.startsWith("{") || !trimmed.contains("\"dismod_encrypted\"")) {
            return encryptedJson
        }

        val ivBase64 = Regex("\"iv\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1) ?: return encryptedJson
        val cipherBase64 = Regex("\"data\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1) ?: return encryptedJson

        val ivBytes = Base64.getDecoder().decode(ivBase64)
        val cipherBytes = Base64.getDecoder().decode(cipherBase64)

        val keyBytes = deriveKey(password)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val ivSpec = IvParameterSpec(ivBytes)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)

        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, Charsets.UTF_8)
    }
}
