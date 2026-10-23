package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONObject
import java.util.UUID

/**
 * Metadata stored in the header of every .efile container.
 */
data class EFileMetadata(
    val version: Int = 1,
    val fileId: String = UUID.randomUUID().toString(),
    val originalFileName: String,
    val originalMimeType: String,
    val originalFileSize: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val isExpiring: Boolean = false,
    val expirationTimestamp: Long? = null,
    val keyId: String? = null,
    val serverKmsEndpoint: String = "https://kms.efilevault.internal",
    val isPasswordProtected: Boolean = false,
    val passwordSaltBase64: String? = null,
    val ivBase64: String,
    val passwordHint: String? = null,
    val envelopeEncryptedDekHex: String? = null
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put("v", version)
        json.put("id", fileId)
        json.put("name", originalFileName)
        json.put("mime", originalMimeType)
        json.put("size", originalFileSize)
        json.put("created", createdAt)
        json.put("expiring", isExpiring)
        if (expirationTimestamp != null) json.put("expiresAt", expirationTimestamp)
        if (keyId != null) json.put("keyId", keyId)
        json.put("kms", serverKmsEndpoint)
        json.put("pwProtected", isPasswordProtected)
        if (passwordSaltBase64 != null) json.put("salt", passwordSaltBase64)
        json.put("iv", ivBase64)
        if (passwordHint != null) json.put("hint", passwordHint)
        if (envelopeEncryptedDekHex != null) json.put("envDek", envelopeEncryptedDekHex)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): EFileMetadata {
            val json = JSONObject(jsonStr)
            return EFileMetadata(
                version = json.optInt("v", 1),
                fileId = json.optString("id", UUID.randomUUID().toString()),
                originalFileName = json.optString("name", "unknown_file"),
                originalMimeType = json.optString("mime", "application/octet-stream"),
                originalFileSize = json.optLong("size", 0L),
                createdAt = json.optLong("created", System.currentTimeMillis()),
                isExpiring = json.optBoolean("expiring", false),
                expirationTimestamp = if (json.has("expiresAt")) json.getLong("expiresAt") else null,
                keyId = if (json.has("keyId")) json.getString("keyId") else null,
                serverKmsEndpoint = json.optString("kms", "https://kms.efilevault.internal"),
                isPasswordProtected = json.optBoolean("pwProtected", false),
                passwordSaltBase64 = if (json.has("salt")) json.getString("salt") else null,
                ivBase64 = json.optString("iv", ""),
                passwordHint = if (json.has("hint")) json.getString("hint") else null,
                envelopeEncryptedDekHex = if (json.has("envDek")) json.getString("envDek") else null
            )
        }
    }
}

/**
 * Local record representing an encrypted item created or imported in the Vault.
 */
@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey val id: String,
    val title: String,
    val originalFileName: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val efilePath: String,
    val isExpiring: Boolean,
    val expirationTimestamp: Long?,
    val keyId: String?,
    val isPasswordProtected: Boolean,
    val passwordHint: String?,
    val serverStatus: String, // "ACTIVE", "EXPIRED", "REVOKED"
    val createdAt: Long,
    val secureShareLink: String?,
    val accessCount: Int = 0
)

/**
 * Server-controlled Key Management Record.
 * Held by the KMS to enforce revocation and expiration independently of client files.
 */
@Entity(tableName = "server_keys")
data class ServerKeyRecord(
    @PrimaryKey val keyId: String,
    val dekHex: String, // 256-bit AES DEK in Hex
    val fileId: String,
    val fileName: String,
    val createdAt: Long,
    val expiresAt: Long?,
    val status: String, // "ACTIVE", "EXPIRED", "REVOKED_BY_OWNER"
    val secureLinkToken: String,
    val desktopAuthToken: String
)

/**
 * Server access and audit trail log entry.
 */
@Entity(tableName = "audit_logs")
data class AuditLogEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val action: String, // "KEY_CREATED", "ACCESS_GRANTED", "ACCESS_DENIED_EXPIRED", "ACCESS_DENIED_REVOKED", "REVOKED_BY_OWNER", "EXPIRATION_EXTENDED", "DESKTOP_VIEW_ACCESSED"
    val clientAgent: String,
    val ipAddress: String,
    val detail: String
)

/**
 * Result of unlocking/decrypting an efile.
 */
data class DecryptedFileResult(
    val metadata: EFileMetadata,
    val bytes: ByteArray,
    val mimeType: String,
    val fileName: String,
    val previewType: FilePreviewType,
    val textContent: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DecryptedFileResult
        return metadata == other.metadata && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = metadata.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

enum class FilePreviewType {
    IMAGE,
    TEXT,
    AUDIO,
    DOCUMENT,
    GENERIC
}
