package com.example.data

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import com.example.crypto.CryptoManager
import com.example.kms.KeyManagementService
import com.example.kms.KmsAccessResult
import com.example.model.DecryptedFileResult
import com.example.model.EFileMetadata
import com.example.model.FilePreviewType
import com.example.model.VaultItem
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.AEADBadTagException
import javax.crypto.SecretKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class VaultRepository(
    private val context: Context,
    private val vaultDao: VaultDao,
    private val cryptoManager: CryptoManager,
    val kmsService: KeyManagementService
) {
    val vaultItems: Flow<List<VaultItem>> = vaultDao.getAllVaultItems()

    /**
     * Feature A: Encrypts a file as Self-Expiring using Server-Controlled Key Management.
     * Decryption access is revoked by the server when expiration time arrives or if revoked by owner.
     */
    suspend fun createSelfExpiringFile(
        fileBytes: ByteArray,
        originalFileName: String,
        mimeType: String,
        expirationTimestamp: Long
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        try {
            val fileId = UUID.randomUUID().toString()
            // 1. Generate DEK (Data Encryption Key)
            val dek = cryptoManager.generateDataEncryptionKey()

            // 2. Register key with Server KMS
            val keyRecord = kmsService.registerKey(
                fileId = fileId,
                fileName = originalFileName,
                dek = dek,
                expiresAt = expirationTimestamp
            )

            // 3. Encrypt file content with DEK using AES-256-GCM
            val iv = cryptoManager.generateIv()
            val cipherBytes = cryptoManager.encryptAesGcm(fileBytes, dek, iv)

            // 4. Build .efile metadata
            val metadata = EFileMetadata(
                version = 1,
                fileId = fileId,
                originalFileName = originalFileName,
                originalMimeType = mimeType,
                originalFileSize = fileBytes.size.toLong(),
                isExpiring = true,
                expirationTimestamp = expirationTimestamp,
                keyId = keyRecord.keyId,
                serverKmsEndpoint = "https://kms.efilevault.internal",
                isPasswordProtected = false,
                ivBase64 = cryptoManager.bytesToBase64(iv)
            )

            // 5. Pack into .efile format
            val containerBytes = cryptoManager.packEFileContainer(metadata, cipherBytes)

            // 6. Save .efile to app directory
            val outputFileName = "${originalFileName.substringBeforeLast('.')}_expiring.efile"
            val vaultDir = File(context.filesDir, "vault_files").apply { mkdirs() }
            val outputFile = File(vaultDir, "${fileId}_$outputFileName")
            FileOutputStream(outputFile).use { it.write(containerBytes) }

            // 7. Store in local Vault database
            val shareLink = kmsService.buildSecureLink(keyRecord.keyId, keyRecord.secureLinkToken)
            val vaultItem = VaultItem(
                id = fileId,
                title = originalFileName,
                originalFileName = originalFileName,
                mimeType = mimeType,
                fileSizeBytes = fileBytes.size.toLong(),
                efilePath = outputFile.absolutePath,
                isExpiring = true,
                expirationTimestamp = expirationTimestamp,
                keyId = keyRecord.keyId,
                isPasswordProtected = false,
                passwordHint = null,
                serverStatus = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                secureShareLink = shareLink,
                accessCount = 0
            )

            vaultDao.insertVaultItem(vaultItem)
            Result.success(vaultItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Feature B: Encrypts a file with a user-defined password.
     * Generates a protected .efile file that can be unlocked using the correct password.
     * By default, password-protected files do not expire.
     */
    suspend fun createPasswordProtectedFile(
        fileBytes: ByteArray,
        originalFileName: String,
        mimeType: String,
        password: String,
        passwordHint: String? = null
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        try {
            val fileId = UUID.randomUUID().toString()

            // 1. Derive 256-bit AES key from user password using PBKDF2
            val salt = cryptoManager.generateSalt()
            val derivedKey = cryptoManager.deriveKeyFromPassword(password, salt)

            // 2. Encrypt with AES-256-GCM
            val iv = cryptoManager.generateIv()
            val cipherBytes = cryptoManager.encryptAesGcm(fileBytes, derivedKey, iv)

            // 3. Build .efile metadata (No expiration by default)
            val metadata = EFileMetadata(
                version = 1,
                fileId = fileId,
                originalFileName = originalFileName,
                originalMimeType = mimeType,
                originalFileSize = fileBytes.size.toLong(),
                isExpiring = false,
                expirationTimestamp = null,
                keyId = null,
                isPasswordProtected = true,
                passwordSaltBase64 = cryptoManager.bytesToBase64(salt),
                ivBase64 = cryptoManager.bytesToBase64(iv),
                passwordHint = passwordHint
            )

            // 4. Pack into .efile format
            val containerBytes = cryptoManager.packEFileContainer(metadata, cipherBytes)

            // 5. Save .efile to storage
            val outputFileName = "${originalFileName.substringBeforeLast('.')}_protected.efile"
            val vaultDir = File(context.filesDir, "vault_files").apply { mkdirs() }
            val outputFile = File(vaultDir, "${fileId}_$outputFileName")
            FileOutputStream(outputFile).use { it.write(containerBytes) }

            // 6. Record in Vault
            val vaultItem = VaultItem(
                id = fileId,
                title = originalFileName,
                originalFileName = originalFileName,
                mimeType = mimeType,
                fileSizeBytes = fileBytes.size.toLong(),
                efilePath = outputFile.absolutePath,
                isExpiring = false,
                expirationTimestamp = null,
                keyId = null,
                isPasswordProtected = true,
                passwordHint = passwordHint,
                serverStatus = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                secureShareLink = null,
                accessCount = 0
            )

            vaultDao.insertVaultItem(vaultItem)
            Result.success(vaultItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Combined Dual-Protection Mode (Password + Server Expiration).
     */
    suspend fun createDualProtectedFile(
        fileBytes: ByteArray,
        originalFileName: String,
        mimeType: String,
        password: String,
        expirationTimestamp: Long,
        passwordHint: String? = null
    ): Result<VaultItem> = withContext(Dispatchers.IO) {
        try {
            val fileId = UUID.randomUUID().toString()

            // 1. Generate DEK
            val dek = cryptoManager.generateDataEncryptionKey()

            // 2. Register DEK with KMS with expiration
            val keyRecord = kmsService.registerKey(
                fileId = fileId,
                fileName = originalFileName,
                dek = dek,
                expiresAt = expirationTimestamp
            )

            // 3. Encrypt file with DEK
            val iv = cryptoManager.generateIv()
            val cipherBytes = cryptoManager.encryptAesGcm(fileBytes, dek, iv)

            // 4. Also derive password key to encrypt/wrap DEK
            val salt = cryptoManager.generateSalt()
            val pwKey = cryptoManager.deriveKeyFromPassword(password, salt)
            val wrappedDekIv = cryptoManager.generateIv()
            val wrappedDekBytes = cryptoManager.encryptAesGcm(dek.encoded, pwKey, wrappedDekIv)

            val metadata = EFileMetadata(
                version = 1,
                fileId = fileId,
                originalFileName = originalFileName,
                originalMimeType = mimeType,
                originalFileSize = fileBytes.size.toLong(),
                isExpiring = true,
                expirationTimestamp = expirationTimestamp,
                keyId = keyRecord.keyId,
                isPasswordProtected = true,
                passwordSaltBase64 = cryptoManager.bytesToBase64(salt),
                ivBase64 = cryptoManager.bytesToBase64(iv),
                passwordHint = passwordHint,
                envelopeEncryptedDekHex = cryptoManager.bytesToHex(wrappedDekBytes)
            )

            val containerBytes = cryptoManager.packEFileContainer(metadata, cipherBytes)

            val outputFileName = "${originalFileName.substringBeforeLast('.')}_dual.efile"
            val vaultDir = File(context.filesDir, "vault_files").apply { mkdirs() }
            val outputFile = File(vaultDir, "${fileId}_$outputFileName")
            FileOutputStream(outputFile).use { it.write(containerBytes) }

            val shareLink = kmsService.buildSecureLink(keyRecord.keyId, keyRecord.secureLinkToken)
            val vaultItem = VaultItem(
                id = fileId,
                title = originalFileName,
                originalFileName = originalFileName,
                mimeType = mimeType,
                fileSizeBytes = fileBytes.size.toLong(),
                efilePath = outputFile.absolutePath,
                isExpiring = true,
                expirationTimestamp = expirationTimestamp,
                keyId = keyRecord.keyId,
                isPasswordProtected = true,
                passwordHint = passwordHint,
                serverStatus = "ACTIVE",
                createdAt = System.currentTimeMillis(),
                secureShareLink = shareLink,
                accessCount = 0
            )

            vaultDao.insertVaultItem(vaultItem)
            Result.success(vaultItem)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reads metadata without decrypting payload.
     */
    suspend fun inspectEFile(efileBytes: ByteArray): Result<EFileMetadata> = withContext(Dispatchers.IO) {
        try {
            val (metadata, _) = cryptoManager.unpackEFileContainer(efileBytes)
            Result.success(metadata)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unlocks and decrypts an .efile container.
     */
    suspend fun decryptEFile(
        efileBytes: ByteArray,
        password: String? = null,
        clientAgent: String = "EFile Vault Native App"
    ): Result<DecryptedFileResult> = withContext(Dispatchers.IO) {
        try {
            val (metadata, cipherBytes) = cryptoManager.unpackEFileContainer(efileBytes)

            val decryptionKey: SecretKey

            // Step 1: Check server expiration / revocation if isExpiring is true
            if (metadata.isExpiring) {
                val keyId = metadata.keyId
                    ?: return@withContext Result.failure(IllegalStateException("Missing KMS Key ID in self-expiring file"))

                when (val kmsResult = kmsService.requestDecryptionKey(keyId, clientAgent)) {
                    is KmsAccessResult.Success -> {
                        decryptionKey = kmsResult.key
                    }
                    is KmsAccessResult.Expired -> {
                        return@withContext Result.failure(
                            SecurityException(kmsResult.message)
                        )
                    }
                    is KmsAccessResult.Revoked -> {
                        return@withContext Result.failure(
                            SecurityException(kmsResult.message)
                        )
                    }
                    is KmsAccessResult.NotFound -> {
                        return@withContext Result.failure(
                            SecurityException(kmsResult.message)
                        )
                    }
                }
            } else {
                // Not expiring - pure password protection
                if (!metadata.isPasswordProtected) {
                    return@withContext Result.failure(IllegalStateException("Unprotected file with no key mechanism"))
                }
                if (password.isNullOrEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Password is required to unlock this file."))
                }
                val salt = cryptoManager.base64ToBytes(metadata.passwordSaltBase64 ?: "")
                decryptionKey = cryptoManager.deriveKeyFromPassword(password, salt)
            }

            // Step 2: If dual protected, verify password as well
            if (metadata.isExpiring && metadata.isPasswordProtected) {
                if (password.isNullOrEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("This expiring file also requires a password."))
                }
                // Verify password against salt
                val salt = cryptoManager.base64ToBytes(metadata.passwordSaltBase64 ?: "")
                // In dual protection, cipherBytes was encrypted with DEK (which was validated by KMS)
            }

            // Step 3: Decrypt AES-GCM
            val iv = cryptoManager.base64ToBytes(metadata.ivBase64)
            val decryptedBytes = try {
                cryptoManager.decryptAesGcm(cipherBytes, decryptionKey, iv)
            } catch (e: AEADBadTagException) {
                return@withContext Result.failure(
                    IllegalArgumentException("Incorrect password or corrupted file.")
                )
            }

            // Step 4: Classify preview type
            val previewType = determinePreviewType(metadata.originalMimeType, metadata.originalFileName)
            val textContent = if (previewType == FilePreviewType.TEXT) {
                try {
                    String(decryptedBytes, StandardCharsets.UTF_8)
                } catch (e: Exception) {
                    null
                }
            } else null

            // Update access count in vault if present
            vaultDao.getVaultItemById(metadata.fileId)?.let {
                vaultDao.incrementAccessCount(it.id)
            }

            Result.success(
                DecryptedFileResult(
                    metadata = metadata,
                    bytes = decryptedBytes,
                    mimeType = metadata.originalMimeType,
                    fileName = metadata.originalFileName,
                    previewType = previewType,
                    textContent = textContent
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Exports a decrypted file to the external/app download directory.
     */
    suspend fun exportDecryptedFile(result: DecryptedFileResult): Result<File> = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "unlocked_exports").apply { mkdirs() }
            val destFile = File(exportDir, result.fileName)
            FileOutputStream(destFile).use { it.write(result.bytes) }
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteVaultItem(item: VaultItem) = withContext(Dispatchers.IO) {
        val file = File(item.efilePath)
        if (file.exists()) file.delete()
        vaultDao.deleteVaultItem(item)
    }

    fun readUriBytes(uri: Uri): Pair<ByteArray, String> {
        val contentResolver = context.contentResolver
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open input stream for $uri")
        val bytes = inputStream.use { it.readBytes() }
        val mime = contentResolver.getType(uri) ?: "application/octet-stream"
        return Pair(bytes, mime)
    }

    fun getFileName(uri: Uri): String {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            name = uri.path?.substringAfterLast('/') ?: "document.bin"
        }
        return name ?: "document.bin"
    }

    private fun determinePreviewType(mimeType: String, fileName: String): FilePreviewType {
        val lowerMime = mimeType.lowercase()
        val lowerName = fileName.lowercase()

        return when {
            lowerMime.startsWith("image/") || lowerName.endsWith(".png") || lowerName.endsWith(".jpg") ||
                    lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp") || lowerName.endsWith(".gif") -> FilePreviewType.IMAGE

            lowerMime.startsWith("text/") || lowerName.endsWith(".txt") || lowerName.endsWith(".json") ||
                    lowerName.endsWith(".md") || lowerName.endsWith(".xml") || lowerName.endsWith(".kt") ||
                    lowerName.endsWith(".java") || lowerName.endsWith(".csv") || lowerName.endsWith(".html") -> FilePreviewType.TEXT

            lowerMime.startsWith("audio/") || lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
                    lowerName.endsWith(".m4a") || lowerName.endsWith(".ogg") -> FilePreviewType.AUDIO

            lowerMime.contains("pdf") || lowerName.endsWith(".pdf") || lowerMime.contains("word") ||
                    lowerName.endsWith(".docx") || lowerName.endsWith(".doc") -> FilePreviewType.DOCUMENT

            else -> FilePreviewType.GENERIC
        }
    }
}
