package com.example.crypto

import android.util.Base64
import com.example.model.EFileMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class CryptoManager {

    private val secureRandom = SecureRandom()

    companion object {
        private val MAGIC_HEADER = byteArrayOf(0x45, 0x46, 0x49, 0x4C, 0x45) // "EFILE"
        private const val CONTAINER_VERSION: Byte = 0x01
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH_BITS = 256
        private const val SALT_LENGTH_BYTES = 16
    }

    /**
     * Generates a cryptographically strong 256-bit AES symmetric key.
     */
    fun generateDataEncryptionKey(): SecretKey {
        val keyGen = KeyGenerator.getInstance("AES")
        keyGen.init(KEY_LENGTH_BITS, secureRandom)
        return keyGen.generateKey()
    }

    /**
     * Derives a 256-bit AES key from a user password using PBKDF2WithHmacSHA256.
     */
    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = factory.generateSecret(spec)
        return SecretKeySpec(secretKey.encoded, "AES")
    }

    /**
     * Generates a random cryptographic salt.
     */
    fun generateSalt(): ByteArray {
        val salt = ByteArray(SALT_LENGTH_BYTES)
        secureRandom.nextBytes(salt)
        return salt
    }

    /**
     * Generates a random IV for AES-GCM (12 bytes).
     */
    fun generateIv(): ByteArray {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /**
     * Encrypts raw plaintext bytes using AES-256-GCM.
     */
    fun encryptAesGcm(plainBytes: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        return cipher.doFinal(plainBytes)
    }

    /**
     * Decrypts ciphertext bytes using AES-256-GCM.
     * Throws AEADBadTagException if wrong key, password, or tampered bytes.
     */
    fun decryptAesGcm(cipherBytes: ByteArray, key: SecretKey, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(cipherBytes)
    }

    /**
     * Packs metadata and ciphertext into the official .efile binary container.
     */
    fun packEFileContainer(metadata: EFileMetadata, cipherBytes: ByteArray): ByteArray {
        val jsonHeaderBytes = metadata.toJson().toByteArray(StandardCharsets.UTF_8)
        val byteStream = ByteArrayOutputStream()
        val dataStream = DataOutputStream(byteStream)

        // 1. Magic header "EFILE"
        dataStream.write(MAGIC_HEADER)
        // 2. Container version byte
        dataStream.writeByte(CONTAINER_VERSION.toInt())
        // 3. Length of JSON Header (4 bytes Int)
        dataStream.writeInt(jsonHeaderBytes.size)
        // 4. JSON Header bytes
        dataStream.write(jsonHeaderBytes)
        // 5. Encrypted payload
        dataStream.write(cipherBytes)
        dataStream.flush()

        return byteStream.toByteArray()
    }

    /**
     * Unpacks an .efile binary container into metadata and ciphertext.
     */
    fun unpackEFileContainer(containerBytes: ByteArray): Pair<EFileMetadata, ByteArray> {
        val byteStream = ByteArrayInputStream(containerBytes)
        val dataStream = DataInputStream(byteStream)

        // Validate Magic Header
        val magic = ByteArray(MAGIC_HEADER.size)
        val readMagic = dataStream.read(magic)
        if (readMagic != MAGIC_HEADER.size || !magic.contentEquals(MAGIC_HEADER)) {
            throw IllegalArgumentException("Not a valid .efile container: missing magic signature")
        }

        val version = dataStream.readByte()
        if (version != CONTAINER_VERSION) {
            throw IllegalArgumentException("Unsupported .efile container version: $version")
        }

        val headerLength = dataStream.readInt()
        if (headerLength <= 0 || headerLength > containerBytes.size) {
            throw IllegalArgumentException("Invalid .efile metadata header length: $headerLength")
        }

        val headerBytes = ByteArray(headerLength)
        dataStream.readFully(headerBytes)
        val headerJson = String(headerBytes, StandardCharsets.UTF_8)
        val metadata = EFileMetadata.fromJson(headerJson)

        // The remainder is the ciphertext
        val cipherLength = containerBytes.size - MAGIC_HEADER.size - 1 - 4 - headerLength
        val cipherBytes = ByteArray(cipherLength)
        dataStream.readFully(cipherBytes)

        return Pair(metadata, cipherBytes)
    }

    // Helper conversion functions
    fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun bytesToBase64(bytes: ByteArray): String {
        return try {
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: RuntimeException) {
            java.util.Base64.getEncoder().encodeToString(bytes)
        }
    }

    fun base64ToBytes(base64: String): ByteArray {
        return try {
            Base64.decode(base64, Base64.NO_WRAP)
        } catch (e: RuntimeException) {
            java.util.Base64.getDecoder().decode(base64)
        }
    }
}
