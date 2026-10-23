package com.example.kms

import com.example.crypto.CryptoManager
import com.example.data.VaultDao
import com.example.model.AuditLogEntry
import com.example.model.ServerKeyRecord
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.flow.Flow

sealed class KmsAccessResult {
    data class Success(val key: SecretKey, val keyRecord: ServerKeyRecord) : KmsAccessResult()
    data class Expired(val expirationTime: Long, val message: String) : KmsAccessResult()
    data class Revoked(val revokedAt: Long, val message: String) : KmsAccessResult()
    data class NotFound(val message: String) : KmsAccessResult()
}

class KeyManagementService(
    private val vaultDao: VaultDao,
    private val cryptoManager: CryptoManager
) {
    private val secureRandom = SecureRandom()

    /**
     * Registers a new Data Encryption Key (DEK) with the Server KMS.
     */
    suspend fun registerKey(
        fileId: String,
        fileName: String,
        dek: SecretKey,
        expiresAt: Long?
    ): ServerKeyRecord {
        val keyId = "kms-dek-" + UUID.randomUUID().toString().take(12)
        val dekHex = cryptoManager.bytesToHex(dek.encoded)
        val now = System.currentTimeMillis()
        val linkToken = UUID.randomUUID().toString().take(16)
        val desktopAuthToken = "DSK-" + UUID.randomUUID().toString().take(12).uppercase(Locale.ROOT)

        val record = ServerKeyRecord(
            keyId = keyId,
            dekHex = dekHex,
            fileId = fileId,
            fileName = fileName,
            createdAt = now,
            expiresAt = expiresAt,
            status = "ACTIVE",
            secureLinkToken = linkToken,
            desktopAuthToken = desktopAuthToken
        )

        vaultDao.insertServerKey(record)

        val expireStr = if (expiresAt != null) {
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(expiresAt))
        } else "Never"

        vaultDao.insertAuditLog(
            AuditLogEntry(
                keyId = keyId,
                timestamp = now,
                action = "KEY_CREATED",
                clientAgent = "EFile KMS Server Engine",
                ipAddress = "127.0.0.1 (Internal KMS Authority)",
                detail = "Key registered for $fileName. Expiration policy: $expireStr"
            )
        )

        return record
    }

    /**
     * Resolves the DEK from the server, checking expiration and owner revocation.
     */
    suspend fun requestDecryptionKey(
        keyId: String,
        clientAgent: String = "Android EFile Viewer",
        ipAddress: String = "192.168.1.105 (Client Network)"
    ): KmsAccessResult {
        val keyRecord = vaultDao.getServerKey(keyId)

        if (keyRecord == null) {
            vaultDao.insertAuditLog(
                AuditLogEntry(
                    keyId = keyId,
                    action = "ACCESS_DENIED_NOT_FOUND",
                    clientAgent = clientAgent,
                    ipAddress = ipAddress,
                    detail = "Key request failed: key ID does not exist in registry."
                )
            )
            return KmsAccessResult.NotFound("Key $keyId not found on KMS Authority server.")
        }

        val now = System.currentTimeMillis()

        // 1. Check Owner Revocation
        if (keyRecord.status == "REVOKED_BY_OWNER") {
            vaultDao.insertAuditLog(
                AuditLogEntry(
                    keyId = keyId,
                    action = "ACCESS_DENIED_REVOKED",
                    clientAgent = clientAgent,
                    ipAddress = ipAddress,
                    detail = "Decryption blocked: access was explicitly revoked by sender."
                )
            )
            return KmsAccessResult.Revoked(
                revokedAt = now,
                message = "Decryption access for this file has been revoked by the owner."
            )
        }

        // 2. Check Expiration Policy
        if (keyRecord.expiresAt != null && now > keyRecord.expiresAt) {
            if (keyRecord.status != "EXPIRED") {
                vaultDao.updateServerKeyStatus(keyId, "EXPIRED")
                vaultDao.updateVaultStatusByKeyId(keyId, "EXPIRED")
            }

            val expDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(keyRecord.expiresAt))
            vaultDao.insertAuditLog(
                AuditLogEntry(
                    keyId = keyId,
                    action = "ACCESS_DENIED_EXPIRED",
                    clientAgent = clientAgent,
                    ipAddress = ipAddress,
                    detail = "Decryption blocked: file expired on $expDate. Server key revoked."
                )
            )

            return KmsAccessResult.Expired(
                expirationTime = keyRecord.expiresAt,
                message = "This file expired on $expDate. Server-controlled key access has lapsed."
            )
        }

        // 3. Authorized access granted
        vaultDao.insertAuditLog(
            AuditLogEntry(
                keyId = keyId,
                action = "ACCESS_GRANTED",
                clientAgent = clientAgent,
                ipAddress = ipAddress,
                detail = "Decryption key authorized and released to client."
            )
        )

        val dekBytes = cryptoManager.hexToBytes(keyRecord.dekHex)
        val secretKey = SecretKeySpec(dekBytes, "AES")
        return KmsAccessResult.Success(secretKey, keyRecord)
    }

    /**
     * Immediately revokes key access, permanently preventing anyone from decrypting.
     */
    suspend fun revokeKeyAccess(keyId: String, reason: String = "Revoked by file owner"): Boolean {
        val record = vaultDao.getServerKey(keyId) ?: return false
        val now = System.currentTimeMillis()

        vaultDao.updateServerKeyStatus(keyId, "REVOKED_BY_OWNER")
        vaultDao.updateVaultStatusByKeyId(keyId, "REVOKED")

        vaultDao.insertAuditLog(
            AuditLogEntry(
                keyId = keyId,
                timestamp = now,
                action = "REVOKED_BY_OWNER",
                clientAgent = "EFile Vault Owner Console",
                ipAddress = "127.0.0.1 (Owner Device)",
                detail = "Owner executed immediate key revocation: $reason"
            )
        )
        return true
    }

    /**
     * Extends expiration time for an existing key.
     */
    suspend fun extendExpiration(keyId: String, newExpiresAt: Long): Boolean {
        val record = vaultDao.getServerKey(keyId) ?: return false
        vaultDao.updateServerKeyExpiration(keyId, newExpiresAt)
        vaultDao.updateVaultExpirationByKeyId(keyId, newExpiresAt)

        val expDate = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(newExpiresAt))
        vaultDao.insertAuditLog(
            AuditLogEntry(
                keyId = keyId,
                action = "EXPIRATION_EXTENDED",
                clientAgent = "EFile Vault Owner Console",
                ipAddress = "127.0.0.1 (Owner Device)",
                detail = "Owner extended expiration to $expDate"
            )
        )
        return true
    }

    /**
     * Simulates an authorized desktop browser accessing the file via secure web link.
     */
    suspend fun logDesktopAccess(keyId: String, desktopBrowser: String, result: String): Boolean {
        vaultDao.insertAuditLog(
            AuditLogEntry(
                keyId = keyId,
                action = "DESKTOP_VIEW_ACCESSED",
                clientAgent = desktopBrowser,
                ipAddress = "10.0.2.2 (Authorized Desktop Terminal)",
                detail = "Desktop Viewer Portal: $result"
            )
        )
        return true
    }

    fun getAuditLogsForKey(keyId: String): Flow<List<AuditLogEntry>> =
        vaultDao.getAuditLogsForKey(keyId)

    suspend fun getKeyRecord(keyId: String): ServerKeyRecord? =
        vaultDao.getServerKey(keyId)

    fun buildSecureLink(keyId: String, token: String): String =
        "https://vault.efile.app/v/$keyId?auth=$token"

    fun buildDesktopViewerLink(keyId: String, desktopAuthToken: String): String =
        "https://desktop.efilevault.com/portal/view?key=$keyId&token=$desktopAuthToken"
}
