package com.example

import com.example.crypto.CryptoManager
import com.example.model.EFileMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.charset.StandardCharsets
import javax.crypto.AEADBadTagException

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleUnitTest {

    private val cryptoManager = CryptoManager()

    @Test
    fun testPasswordEncryptionAndDecryption() {
        val originalText = "Top secret document content for Feature B"
        val originalBytes = originalText.toByteArray(StandardCharsets.UTF_8)
        val password = "SuperSecretPassword123!"

        val salt = cryptoManager.generateSalt()
        val key = cryptoManager.deriveKeyFromPassword(password, salt)
        val iv = cryptoManager.generateIv()

        val cipherBytes = cryptoManager.encryptAesGcm(originalBytes, key, iv)
        assertFalse(cipherBytes.contentEquals(originalBytes))

        // Decrypt with correct password
        val decryptedBytes = cryptoManager.decryptAesGcm(cipherBytes, key, iv)
        val decryptedText = String(decryptedBytes, StandardCharsets.UTF_8)
        assertEquals(originalText, decryptedText)

        // Decrypt with wrong password should fail authentication tag check
        val wrongKey = cryptoManager.deriveKeyFromPassword("WrongPassword!", salt)
        try {
            cryptoManager.decryptAesGcm(cipherBytes, wrongKey, iv)
            fail("Decryption with wrong password should fail with AEADBadTagException")
        } catch (e: Exception) {
            assertTrue(e is AEADBadTagException)
        }
    }

    @Test
    fun testEFileContainerPackAndUnpack() {
        val originalText = "Self-expiring file payload"
        val payloadBytes = originalText.toByteArray(StandardCharsets.UTF_8)

        val metadata = EFileMetadata(
            originalFileName = "financial_statement.pdf",
            originalMimeType = "application/pdf",
            originalFileSize = payloadBytes.size.toLong(),
            isExpiring = true,
            expirationTimestamp = System.currentTimeMillis() + 3600000,
            keyId = "kms-key-test-999",
            ivBase64 = cryptoManager.bytesToBase64(cryptoManager.generateIv())
        )

        val packedContainer = cryptoManager.packEFileContainer(metadata, payloadBytes)
        assertNotNull(packedContainer)
        assertTrue(packedContainer.size > payloadBytes.size)

        val (unpackedMeta, unpackedPayload) = cryptoManager.unpackEFileContainer(packedContainer)
        assertEquals("financial_statement.pdf", unpackedMeta.originalFileName)
        assertEquals("application/pdf", unpackedMeta.originalMimeType)
        assertTrue(unpackedMeta.isExpiring)
        assertEquals("kms-key-test-999", unpackedMeta.keyId)
        assertTrue(unpackedPayload.contentEquals(payloadBytes))
    }
}
