package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.AuditLogEntry
import com.example.model.ServerKeyRecord
import com.example.model.VaultItem
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    // Vault Items
    @Query("SELECT * FROM vault_items ORDER BY createdAt DESC")
    fun getAllVaultItems(): Flow<List<VaultItem>>

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: String): VaultItem?

    @Query("SELECT * FROM vault_items WHERE keyId = :keyId LIMIT 1")
    suspend fun getVaultItemByKeyId(keyId: String): VaultItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem)

    @Update
    suspend fun updateVaultItem(item: VaultItem)

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("UPDATE vault_items SET serverStatus = :status WHERE keyId = :keyId")
    suspend fun updateVaultStatusByKeyId(keyId: String, status: String)

    @Query("UPDATE vault_items SET expirationTimestamp = :newExpiresAt WHERE keyId = :keyId")
    suspend fun updateVaultExpirationByKeyId(keyId: String, newExpiresAt: Long)

    @Query("UPDATE vault_items SET accessCount = accessCount + 1 WHERE id = :id")
    suspend fun incrementAccessCount(id: String)

    // Server KMS Keys
    @Query("SELECT * FROM server_keys WHERE keyId = :keyId LIMIT 1")
    suspend fun getServerKey(keyId: String): ServerKeyRecord?

    @Query("SELECT * FROM server_keys ORDER BY createdAt DESC")
    fun getAllServerKeys(): Flow<List<ServerKeyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerKey(key: ServerKeyRecord)

    @Query("UPDATE server_keys SET status = :status WHERE keyId = :keyId")
    suspend fun updateServerKeyStatus(keyId: String, status: String)

    @Query("UPDATE server_keys SET expiresAt = :newExpiresAt, status = 'ACTIVE' WHERE keyId = :keyId")
    suspend fun updateServerKeyExpiration(keyId: String, newExpiresAt: Long)

    // Audit Logs
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLogEntry)

    @Query("SELECT * FROM audit_logs WHERE keyId = :keyId ORDER BY timestamp DESC")
    fun getAuditLogsForKey(keyId: String): Flow<List<AuditLogEntry>>

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 100")
    fun getAllAuditLogs(): Flow<List<AuditLogEntry>>
}
