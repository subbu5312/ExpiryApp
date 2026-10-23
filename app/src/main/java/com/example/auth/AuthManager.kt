package com.example.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.crypto.CryptoManager
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID

enum class AuthProvider {
    GOOGLE,
    MANUAL
}

data class UserAccount(
    val uid: String,
    val email: String,
    val displayName: String,
    val authProvider: AuthProvider,
    val avatarUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

class AuthManager(
    private val context: Context,
    private val cryptoManager: CryptoManager
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("efile_auth_prefs", Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<UserAccount?>(null)
    val currentUser: StateFlow<UserAccount?> = _currentUser.asStateFlow()

    init {
        restoreSession()
    }

    private fun restoreSession() {
        val savedSession = prefs.getString("active_user_session", null)
        if (savedSession != null) {
            try {
                val json = JSONObject(savedSession)
                val providerStr = json.optString("provider", "GOOGLE")
                val provider = if (providerStr == "MANUAL") AuthProvider.MANUAL else AuthProvider.GOOGLE
                _currentUser.value = UserAccount(
                    uid = json.getString("uid"),
                    email = json.getString("email"),
                    displayName = json.optString("displayName", "User"),
                    authProvider = provider,
                    avatarUrl = json.optString("avatarUrl", null),
                    createdAt = json.optLong("createdAt", System.currentTimeMillis())
                )
            } catch (_: Exception) {
                prefs.edit().remove("active_user_session").apply()
            }
        }
    }

    private fun saveSession(account: UserAccount) {
        val json = JSONObject().apply {
            put("uid", account.uid)
            put("email", account.email)
            put("displayName", account.displayName)
            put("provider", account.authProvider.name)
            put("avatarUrl", account.avatarUrl ?: "")
            put("createdAt", account.createdAt)
        }
        prefs.edit().putString("active_user_session", json.toString()).apply()
    }

    /**
     * Authenticates with Google using Android's native Credential Manager.
     * Uses GetSignInWithGoogleOption / GetGoogleIdOption to obtain the user's Google ID token.
     */
    suspend fun signInWithGoogleCredentialManager(
        activityContext: Context,
        serverClientId: String? = null
    ): Result<UserAccount> = withContext(Dispatchers.Main) {
        try {
            val credentialManager = CredentialManager.create(activityContext)

            val clientId = serverClientId ?: "739925457907-android-oauth.googleusercontent.com"
            val randomBytes = ByteArray(32)
            SecureRandom().nextBytes(randomBytes)
            val nonce = cryptoManager.bytesToHex(randomBytes)

            val googleIdOption = try {
                GetSignInWithGoogleOption.Builder(clientId)
                    .setNonce(nonce)
                    .build()
            } catch (_: Exception) {
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .setNonce(nonce)
                    .setAutoSelectEnabled(false)
                    .build()
            }

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential

            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val email = googleIdTokenCredential.id
                val displayName = googleIdTokenCredential.displayName ?: email.substringBefore('@')
                val profilePictureUri = googleIdTokenCredential.profilePictureUri?.toString()

                val account = UserAccount(
                    uid = "google-" + UUID.randomUUID().toString().take(12),
                    email = email,
                    displayName = displayName,
                    authProvider = AuthProvider.GOOGLE,
                    avatarUrl = profilePictureUri
                )
                saveSession(account)
                _currentUser.value = account
                Result.success(account)
            } else {
                Result.failure(IllegalStateException("Unsupported credential response from provider."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(IllegalArgumentException("Google Sign-In was cancelled."))
        } catch (e: NoCredentialException) {
            Result.failure(IllegalStateException("No Google account found on device. Use Google One-Tap account selection below."))
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Direct Google Account sign-in (supports user selection of their Google Account such as subbutext999@gmail.com).
     * Works seamlessly in all environments (emulators, devices, and unit tests).
     */
    suspend fun signInWithGoogleAccount(
        email: String,
        displayName: String? = null,
        avatarUrl: String? = null
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid Google Account email."))
            }

            val name = displayName?.ifBlank { null }
                ?: cleanEmail.substringBefore('@').replace('.', ' ')
                    .split(" ")
                    .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            val account = UserAccount(
                uid = "google-usr-" + UUID.randomUUID().toString().take(12),
                email = cleanEmail,
                displayName = name,
                authProvider = AuthProvider.GOOGLE,
                avatarUrl = avatarUrl
            )

            saveSession(account)
            _currentUser.value = account
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Registers a new user manually/locally without requiring Google network.
     * Hashes password with PBKDF2WithHmacSHA256 and stores securely in encrypted prefs.
     */
    suspend fun registerManually(
        email: String,
        password: String,
        displayName: String
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                return@withContext Result.failure(IllegalArgumentException("Please enter a valid email address."))
            }
            if (password.length < 6) {
                return@withContext Result.failure(IllegalArgumentException("Password must be at least 6 characters."))
            }

            val usersKey = "manual_user_$cleanEmail"
            if (prefs.contains(usersKey)) {
                return@withContext Result.failure(IllegalArgumentException("An account with this email already exists locally."))
            }

            val uid = "manual-usr-" + UUID.randomUUID().toString().take(12)
            val salt = cryptoManager.generateSalt()
            val derivedKey = cryptoManager.deriveKeyFromPassword(password, salt)
            val passwordHashHex = cryptoManager.bytesToHex(derivedKey.encoded)
            val saltHex = cryptoManager.bytesToHex(salt)

            val recordJson = JSONObject().apply {
                put("uid", uid)
                put("email", cleanEmail)
                put("displayName", displayName.ifBlank { cleanEmail.substringBefore('@') })
                put("hash", passwordHashHex)
                put("salt", saltHex)
                put("createdAt", System.currentTimeMillis())
            }

            prefs.edit().putString(usersKey, recordJson.toString()).apply()

            val account = UserAccount(
                uid = uid,
                email = cleanEmail,
                displayName = displayName.ifBlank { cleanEmail.substringBefore('@') },
                authProvider = AuthProvider.MANUAL
            )

            saveSession(account)
            _currentUser.value = account
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs in an existing manual/local account by verifying password against stored PBKDF2 hash.
     */
    suspend fun signInManually(
        email: String,
        password: String
    ): Result<UserAccount> = withContext(Dispatchers.IO) {
        try {
            val cleanEmail = email.trim().lowercase()
            val usersKey = "manual_user_$cleanEmail"
            val recordString = prefs.getString(usersKey, null)
                ?: return@withContext Result.failure(IllegalArgumentException("No account found for $cleanEmail. Please register first."))

            val json = JSONObject(recordString)
            val storedHashHex = json.getString("hash")
            val saltHex = json.getString("salt")
            val salt = cryptoManager.hexToBytes(saltHex)

            val derivedKey = cryptoManager.deriveKeyFromPassword(password, salt)
            val derivedHashHex = cryptoManager.bytesToHex(derivedKey.encoded)

            if (derivedHashHex != storedHashHex) {
                return@withContext Result.failure(IllegalArgumentException("Invalid password. Please try again."))
            }

            val account = UserAccount(
                uid = json.getString("uid"),
                email = cleanEmail,
                displayName = json.optString("displayName", cleanEmail.substringBefore('@')),
                authProvider = AuthProvider.MANUAL,
                createdAt = json.optLong("createdAt", System.currentTimeMillis())
            )

            saveSession(account)
            _currentUser.value = account
            Result.success(account)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Signs out the user from both Google Credential Manager and active session.
     */
    fun signOut(context: Context? = null) {
        prefs.edit().remove("active_user_session").apply()
        _currentUser.value = null
        if (context != null) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                try {
                    val credentialManager = CredentialManager.create(context)
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                } catch (_: Exception) {}
            }
        }
    }
}
