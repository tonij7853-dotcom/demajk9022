package chat.stoat.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogEncryptorTest {

    @Test
    fun testEncryptAndDecrypt() {
        val sampleLog = "{\"time\":\"2026-10-02T12:00:00.000Z\",\"level\":\"INFO\",\"action\":\"gif_crop_started\",\"details\":{\"frames\":24}}"
        val password = "MySecretPassword123!"

        val encrypted = LogEncryptor.encrypt(sampleLog, password)
        assertTrue(encrypted.contains("\"dismod_encrypted\":true"))
        assertFalse(encrypted.contains("gif_crop_started"))

        val decrypted = LogEncryptor.decrypt(encrypted, password)
        assertEquals(sampleLog, decrypted)
    }

    @Test
    fun testDefaultPassword() {
        val sampleLog = "Test log with default password"
        val encrypted = LogEncryptor.encrypt(sampleLog)
        val decrypted = LogEncryptor.decrypt(encrypted)
        assertEquals(sampleLog, decrypted)
    }

    @Test
    fun testNonEncryptedStringPassthrough() {
        val plain = "Regular unencrypted log line"
        val result = LogEncryptor.decrypt(plain)
        assertEquals(plain, result)
    }
}
