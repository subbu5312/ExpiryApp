package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.crypto.CryptoManager
import com.example.data.AppDatabase
import com.example.data.VaultRepository
import com.example.kms.KeyManagementService
import com.example.model.AuditLogEntry
import com.example.model.DecryptedFileResult
import com.example.model.EFileMetadata
import com.example.model.ServerKeyRecord
import com.example.model.VaultItem
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import com.example.auth.AuthManager
import com.example.auth.UserAccount

enum class VaultFilter {
    ALL,
    SELF_EXPIRING,
    PASSWORD_PROTECTED,
    ACTIVE,
    REVOKED_EXPIRED
}

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val filteredItems: List<VaultItem> = emptyList(),
    val activeFilter: VaultFilter = VaultFilter.ALL,
    val selectedItem: VaultItem? = null,
    val selectedKeyRecord: ServerKeyRecord? = null,
    val currentAuditLogs: List<AuditLogEntry> = emptyList(),
    val unlockedResult: DecryptedFileResult? = null,
    val inspectingMetadata: EFileMetadata? = null,
    val currentUser: UserAccount? = null,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val pendingEFileBytesToUnlock: ByteArray? = null,
    val pendingEFileName: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val cryptoManager = CryptoManager()
    val authManager = AuthManager(application, cryptoManager)
    val kmsService = KeyManagementService(db.vaultDao(), cryptoManager)
    val repository = VaultRepository(application, db.vaultDao(), cryptoManager, kmsService)

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                _uiState.value = _uiState.value.copy(currentUser = user)
            }
        }
        viewModelScope.launch {
            repository.vaultItems.collect { items ->
                _uiState.value = _uiState.value.copy(
                    items = items,
                    filteredItems = filterItems(items, _uiState.value.activeFilter)
                )
            }
        }
    }

    fun setFilter(filter: VaultFilter) {
        _uiState.value = _uiState.value.copy(
            activeFilter = filter,
            filteredItems = filterItems(_uiState.value.items, filter)
        )
    }

    private fun filterItems(items: List<VaultItem>, filter: VaultFilter): List<VaultItem> {
        val now = System.currentTimeMillis()
        return when (filter) {
            VaultFilter.ALL -> items
            VaultFilter.SELF_EXPIRING -> items.filter { it.isExpiring }
            VaultFilter.PASSWORD_PROTECTED -> items.filter { it.isPasswordProtected }
            VaultFilter.ACTIVE -> items.filter {
                it.serverStatus == "ACTIVE" && (it.expirationTimestamp == null || it.expirationTimestamp > now)
            }
            VaultFilter.REVOKED_EXPIRED -> items.filter {
                it.serverStatus != "ACTIVE" || (it.expirationTimestamp != null && it.expirationTimestamp <= now)
            }
        }
    }

    // Feature A: Encrypt Self-Expiring File
    fun encryptSelfExpiring(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        expirationTimestamp: Long,
        onSuccess: (VaultItem) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val result = repository.createSelfExpiringFile(
                fileBytes = fileBytes,
                originalFileName = fileName,
                mimeType = mimeType,
                expirationTimestamp = expirationTimestamp
            )
            _uiState.value = _uiState.value.copy(isProcessing = false)
            result.onSuccess { item ->
                _uiState.value = _uiState.value.copy(
                    successMessage = "Self-expiring encrypted .efile generated with server key management!"
                )
                onSuccess(item)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to create expiring file: ${err.message}"
                )
            }
        }
    }

    // Feature B: Encrypt Password-Protected File
    fun encryptPasswordProtected(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        password: String,
        passwordHint: String?,
        onSuccess: (VaultItem) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val result = repository.createPasswordProtectedFile(
                fileBytes = fileBytes,
                originalFileName = fileName,
                mimeType = mimeType,
                password = password,
                passwordHint = passwordHint
            )
            _uiState.value = _uiState.value.copy(isProcessing = false)
            result.onSuccess { item ->
                _uiState.value = _uiState.value.copy(
                    successMessage = "Password-protected .efile successfully encrypted with AES-256-GCM!"
                )
                onSuccess(item)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to password protect file: ${err.message}"
                )
            }
        }
    }

    // Optional Dual-Protected File
    fun encryptDualProtected(
        fileName: String,
        mimeType: String,
        fileBytes: ByteArray,
        password: String,
        expirationTimestamp: Long,
        passwordHint: String?,
        onSuccess: (VaultItem) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val result = repository.createDualProtectedFile(
                fileBytes = fileBytes,
                originalFileName = fileName,
                mimeType = mimeType,
                password = password,
                expirationTimestamp = expirationTimestamp,
                passwordHint = passwordHint
            )
            _uiState.value = _uiState.value.copy(isProcessing = false)
            result.onSuccess { item ->
                _uiState.value = _uiState.value.copy(
                    successMessage = "Dual-protected .efile created with both password & server key expiry!"
                )
                onSuccess(item)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to create dual protected file: ${err.message}"
                )
            }
        }
    }

    // Inspect .efile metadata
    fun prepareUnlockForFile(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val inspectResult = repository.inspectEFile(bytes)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            inspectResult.onSuccess { meta ->
                _uiState.value = _uiState.value.copy(
                    inspectingMetadata = meta,
                    pendingEFileBytesToUnlock = bytes,
                    pendingEFileName = fileName
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Failed to read .efile header: ${err.message}"
                )
            }
        }
    }

    // Unlock .efile
    fun unlockCurrentEFile(password: String?) {
        val bytes = _uiState.value.pendingEFileBytesToUnlock ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true, errorMessage = null)
            val result = repository.decryptEFile(bytes, password)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            result.onSuccess { dec ->
                _uiState.value = _uiState.value.copy(
                    unlockedResult = dec,
                    successMessage = "File successfully decrypted and integrity verified!"
                )
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.message ?: "Failed to unlock file."
                )
            }
        }
    }

    fun clearUnlockedResult() {
        _uiState.value = _uiState.value.copy(
            unlockedResult = null,
            inspectingMetadata = null,
            pendingEFileBytesToUnlock = null,
            pendingEFileName = null
        )
    }

    // Server Key Revocation (Feature A sender control)
    fun revokeServerKey(keyId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val success = kmsService.revokeKeyAccess(keyId, "Owner revoked key via Vault Console")
            _uiState.value = _uiState.value.copy(isProcessing = false)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Key access permanently revoked! No recipient can decrypt this file anymore."
                )
                loadItemDetailsForKey(keyId)
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Could not revoke key.")
            }
        }
    }

    // Extend Expiration (Feature A sender control)
    fun extendServerKey(keyId: String, newExpiresAt: Long) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val success = kmsService.extendExpiration(keyId, newExpiresAt)
            _uiState.value = _uiState.value.copy(isProcessing = false)
            if (success) {
                _uiState.value = _uiState.value.copy(
                    successMessage = "Expiration extended successfully!"
                )
                loadItemDetailsForKey(keyId)
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Could not extend expiration.")
            }
        }
    }

    fun loadItemDetailsForKey(keyId: String) {
        viewModelScope.launch {
            val keyRecord = kmsService.getKeyRecord(keyId)
            _uiState.value = _uiState.value.copy(selectedKeyRecord = keyRecord)
            kmsService.getAuditLogsForKey(keyId).collect { logs ->
                _uiState.value = _uiState.value.copy(currentAuditLogs = logs)
            }
        }
    }

    fun setSelectedItem(item: VaultItem?) {
        _uiState.value = _uiState.value.copy(selectedItem = item)
        if (item?.keyId != null) {
            loadItemDetailsForKey(item.keyId)
        } else {
            _uiState.value = _uiState.value.copy(
                selectedKeyRecord = null,
                currentAuditLogs = emptyList()
            )
        }
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch {
            repository.deleteVaultItem(item)
            if (_uiState.value.selectedItem?.id == item.id) {
                _uiState.value = _uiState.value.copy(selectedItem = null)
            }
            _uiState.value = _uiState.value.copy(successMessage = "Item removed from Vault.")
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }

    // Generate quick sample files so the user can test both features instantly!
    fun getSampleFile(type: String): Triple<String, String, ByteArray> {
        return when (type) {
            "CONFIDENTIAL_NOTE" -> {
                val text = """
                    CONFIDENTIAL PROJECT BLUEPRINT - TOP SECRET
                    ============================================
                    Document ID: SEC-2026-994
                    Security Clearance: Level 5
                    Classification: Server-Expiring & Restricted
                    
                    1. Architecture Overview:
                       - End-to-end AES-256-GCM envelope encryption.
                       - Zero-trust server key escrow with instantaneous revocation.
                       - Independent cryptographic password derivation via PBKDF2 (10,000 rounds).
                    
                    2. Destruction Protocol:
                       - Keys are cryptographically destroyed by the KMS authority upon expiration.
                       - Decryption attempts after expiry receive permanent 403 Access Denied.
                       - Tamper resistance verified via 128-bit GCM authentication tags.
                    
                    3. Authorized Recipients:
                       - Subbu (Executive Lead)
                       - Security Operations Center
                    ============================================
                    Copyright 2026 EFile Vault Security Systems.
                """.trimIndent()
                Triple("Project_Blueprint_Confidential.txt", "text/plain", text.toByteArray(StandardCharsets.UTF_8))
            }
            "FINANCIAL_REPORT" -> {
                val csv = """
                    Quarter,Revenue,Expenses,Net Profit,Status
                    Q1-2026,$4,250,000,$2,100,000,$2,150,000,Audited
                    Q2-2026,$5,820,000,$2,450,000,$3,370,000,Audited
                    Q3-2026,$6,190,000,$2,800,000,$3,390,000,Projected
                    Q4-2026,$7,400,000,$3,100,000,$4,300,000,Forecast
                """.trimIndent()
                Triple("Q3_Financial_Summary.csv", "text/csv", csv.toByteArray(StandardCharsets.UTF_8))
            }
            "SERVER_KEY_MANIFEST" -> {
                val json = """
                    {
                      "system": "EFile Enterprise Key Gateway",
                      "region": "us-central1",
                      "server_controlled": true,
                      "access_policy": "STRICT_EXPIRY",
                      "features": [
                        "Self-Expiring Files",
                        "Password-Protected Any File",
                        "Desktop Web Viewing Portal",
                        "Email Attachment Pipeline",
                        "Real-Time Revocation"
                      ]
                    }
                """.trimIndent()
                Triple("security_policy.json", "application/json", json.toByteArray(StandardCharsets.UTF_8))
            }
            else -> {
                val sample = "Personal Secure Document - Created with EFile Vault."
                Triple("secure_memo.txt", "text/plain", sample.toByteArray(StandardCharsets.UTF_8))
            }
        }
    }
}
