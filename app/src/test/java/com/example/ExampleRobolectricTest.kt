package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.crypto.CryptoManager
import com.example.data.AppDatabase
import com.example.data.VaultRepository
import com.example.kms.KeyManagementService
import com.example.kms.KmsAccessResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var cryptoManager: CryptoManager
    private lateinit var kmsService: KeyManagementService
    private lateinit var repository: VaultRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cryptoManager = CryptoManager()
        kmsService = KeyManagementService(db.vaultDao(), cryptoManager)
        repository = VaultRepository(context, db.vaultDao(), cryptoManager, kmsService)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `read string from context`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("EFile Vault", appName)
    }

    @Test
    fun `feature A - self expiring file flow with server revocation`() = runBlocking {
        val sampleBytes = "Top Secret Server Expiring Content".toByteArray(StandardCharsets.UTF_8)
        val expirationTime = System.currentTimeMillis() + 100000 // In the future

        // 1. Create Self-Expiring File
        val createResult = repository.createSelfExpiringFile(
            fileBytes = sampleBytes,
            originalFileName = "secret_memo.txt",
            mimeType = "text/plain",
            expirationTimestamp = expirationTime
        )
        assertTrue(createResult.isSuccess)
        val vaultItem = createResult.getOrThrow()
        assertTrue(vaultItem.isExpiring)
        assertNotNull(vaultItem.keyId)

        // 2. Read .efile container from disk
        val efile = java.io.File(vaultItem.efilePath)
        assertTrue(efile.exists())
        val efileBytes = efile.readBytes()

        // 3. Unlock with active KMS key
        val decryptResult = repository.decryptEFile(efileBytes)
        assertTrue(decryptResult.isSuccess)
        val decrypted = decryptResult.getOrThrow()
        assertEquals("Top Secret Server Expiring Content", decrypted.textContent)

        // 4. Test Server Revocation: Owner immediately revokes access
        val revoked = kmsService.revokeKeyAccess(vaultItem.keyId!!)
        assertTrue(revoked)

        // 5. Subsequent unlock attempt must fail because KMS revokes access
        val attemptAfterRevocation = repository.decryptEFile(efileBytes)
        assertFalse(attemptAfterRevocation.isSuccess)
        assertTrue(attemptAfterRevocation.exceptionOrNull()?.message?.contains("revoked") == true)
    }

    @Test
    fun `feature A - self expiring file denied after expiration date`() = runBlocking {
        val sampleBytes = "Time-sensitive contract".toByteArray(StandardCharsets.UTF_8)
        val pastExpiration = System.currentTimeMillis() - 5000 // Already expired

        val createResult = repository.createSelfExpiringFile(
            fileBytes = sampleBytes,
            originalFileName = "contract.txt",
            mimeType = "text/plain",
            expirationTimestamp = pastExpiration
        )
        val vaultItem = createResult.getOrThrow()
        val efileBytes = java.io.File(vaultItem.efilePath).readBytes()

        // Decryption attempt must fail due to expiration
        val decryptResult = repository.decryptEFile(efileBytes)
        assertFalse(decryptResult.isSuccess)
        assertTrue(decryptResult.exceptionOrNull()?.message?.contains("expired") == true)
    }

    @Test
    fun `feature B - password protected file does not expire and unlocks with password`() = runBlocking {
        val sampleBytes = "Confidential customer database".toByteArray(StandardCharsets.UTF_8)
        val password = "StrongPassword#2026"

        // 1. Create Password Protected File
        val createResult = repository.createPasswordProtectedFile(
            fileBytes = sampleBytes,
            originalFileName = "customers.csv",
            mimeType = "text/csv",
            password = password,
            passwordHint = "Year 2026"
        )
        assertTrue(createResult.isSuccess)
        val item = createResult.getOrThrow()
        assertFalse(item.isExpiring) // By default does not expire!
        assertTrue(item.isPasswordProtected)

        val efileBytes = java.io.File(item.efilePath).readBytes()

        // 2. Unlock with wrong password should fail
        val wrongAttempt = repository.decryptEFile(efileBytes, "WrongPassword")
        assertFalse(wrongAttempt.isSuccess)

        // 3. Unlock with correct password succeeds
        val rightAttempt = repository.decryptEFile(efileBytes, password)
        assertTrue(rightAttempt.isSuccess)
        assertEquals("Confidential customer database", rightAttempt.getOrThrow().textContent)
    }

    @Test
    fun `auth - manual registration and login flow`() = runBlocking {
        val authManager = com.example.auth.AuthManager(context, cryptoManager)

        // 1. Register manual account
        val regResult = authManager.registerManually("subbu.test@vault.com", "SecretPass123!", "Subbu")
        assertTrue(regResult.isSuccess)
        val user = regResult.getOrThrow()
        assertEquals("subbu.test@vault.com", user.email)
        assertEquals("Subbu", user.displayName)
        assertEquals(com.example.auth.AuthProvider.MANUAL, user.authProvider)

        // 2. Sign out
        authManager.signOut()
        assertEquals(null, authManager.currentUser.value)

        // 3. Sign in with wrong password fails
        val wrongLogin = authManager.signInManually("subbu.test@vault.com", "WrongPassword")
        assertFalse(wrongLogin.isSuccess)

        // 4. Sign in with correct password succeeds
        val correctLogin = authManager.signInManually("subbu.test@vault.com", "SecretPass123!")
        assertTrue(correctLogin.isSuccess)
        assertEquals("Subbu", correctLogin.getOrThrow().displayName)
    }

    @Test
    fun `auth - google account sign in flow`() = runBlocking {
        val authManager = com.example.auth.AuthManager(context, cryptoManager)

        val googleLogin = authManager.signInWithGoogleAccount("subbutext999@gmail.com", "Subbu")
        assertTrue(googleLogin.isSuccess)
        val user = googleLogin.getOrThrow()
        assertEquals("subbutext999@gmail.com", user.email)
        assertEquals("Subbu", user.displayName)
        assertEquals(com.example.auth.AuthProvider.GOOGLE, user.authProvider)

        // Verify sign out
        authManager.signOut()
        assertEquals(null, authManager.currentUser.value)
    }

    @Test
    fun `auth - google account signup and signin dual flow`() = runBlocking {
        val authManager = com.example.auth.AuthManager(context, cryptoManager)

        // 1. Sign up / First-time access with Google Account
        val signupGoogle = authManager.signInWithGoogleAccount("newuser@gmail.com", "New Google User")
        assertTrue(signupGoogle.isSuccess)
        val signedUp = signupGoogle.getOrThrow()
        assertEquals("newuser@gmail.com", signedUp.email)
        assertEquals("New Google User", signedUp.displayName)
        assertEquals(com.example.auth.AuthProvider.GOOGLE, signedUp.authProvider)

        // 2. Sign out
        authManager.signOut()
        assertEquals(null, authManager.currentUser.value)

        // 3. Sign in again with same Google Account
        val signinGoogle = authManager.signInWithGoogleAccount("newuser@gmail.com")
        assertTrue(signinGoogle.isSuccess)
        val signedIn = signinGoogle.getOrThrow()
        assertEquals("newuser@gmail.com", signedIn.email)
    }
}
