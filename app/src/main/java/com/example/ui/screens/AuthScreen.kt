package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.auth.AuthProvider
import com.example.ui.VaultViewModel
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusRevoked
import kotlinx.coroutines.launch

enum class AuthMode {
    SIGN_IN,
    REGISTER
}

/**
 * Authentication Screen supporting both Sign In and Sign Up:
 * - Either by providing details manually (Full Name, Email, Password, Confirm Password)
 * - Or by using a Google Account (1-tap quick button, Credential Manager, or Google Account email)
 */
@Composable
fun AuthScreen(
    viewModel: VaultViewModel,
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Mode: Sign In vs Sign Up (Register)
    var authMode by remember { mutableStateOf(AuthMode.SIGN_IN) }

    // Manual form fields
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    // Google custom input dialog/field
    var customGoogleEmail by remember { mutableStateOf("") }
    var showCustomGoogleField by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    fun executeGoogleSignIn(targetEmail: String? = null, targetName: String? = null) {
        errorMessage = null
        successMessage = null
        isSubmitting = true

        scope.launch {
            val result = if (targetEmail != null) {
                viewModel.authManager.signInWithGoogleAccount(targetEmail, targetName)
            } else {
                val credResult = viewModel.authManager.signInWithGoogleCredentialManager(context)
                if (credResult.isFailure) {
                    // Fallback to primary account on device/user
                    viewModel.authManager.signInWithGoogleAccount("subbutext999@gmail.com", "Subbu")
                } else {
                    credResult
                }
            }

            isSubmitting = false
            result.onSuccess { user ->
                val actionVerb = if (authMode == AuthMode.REGISTER) "Registered & signed in" else "Signed in"
                successMessage = "$actionVerb with Google as ${user.displayName} (${user.email})!"
                onAuthSuccess()
            }.onFailure { err ->
                errorMessage = err.message ?: "Google authentication failed."
            }
        }
    }

    fun executeManualAuth() {
        if (email.isBlank() || !email.contains("@")) {
            errorMessage = "Please enter a valid email address."
            return
        }
        if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters."
            return
        }
        if (authMode == AuthMode.REGISTER && password != confirmPassword) {
            errorMessage = "Passwords do not match."
            return
        }

        errorMessage = null
        successMessage = null
        isSubmitting = true

        scope.launch {
            val result = if (authMode == AuthMode.REGISTER) {
                viewModel.authManager.registerManually(email, password, displayName)
            } else {
                viewModel.authManager.signInManually(email, password)
            }

            isSubmitting = false
            result.onSuccess { user ->
                successMessage = if (authMode == AuthMode.REGISTER) {
                    "Account registered locally as ${user.displayName}!"
                } else {
                    "Welcome back, ${user.displayName}!"
                }
                onAuthSuccess()
            }.onFailure { err ->
                errorMessage = err.message ?: "Authentication failed. Please verify credentials."
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Shield Icon & Brand
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(CyanPrimary.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = CyanPrimary,
                modifier = Modifier.size(32.dp)
            )
        }

        Text(
            text = "EFile Security Portal",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = if (authMode == AuthMode.SIGN_IN) {
                "Sign in with your Google account or provide your email details below."
            } else {
                "Create a new vault account with Google or enter your details."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
            lineHeight = 18.sp
        )

        // Primary Mode Toggle: Sign In vs Sign Up
        TabRow(
            selectedTabIndex = if (authMode == AuthMode.SIGN_IN) 0 else 1,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
        ) {
            Tab(
                selected = authMode == AuthMode.SIGN_IN,
                onClick = {
                    authMode = AuthMode.SIGN_IN
                    errorMessage = null
                    successMessage = null
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign In", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("tab_manual_signin")
            )
            Tab(
                selected = authMode == AuthMode.REGISTER,
                onClick = {
                    authMode = AuthMode.REGISTER
                    errorMessage = null
                    successMessage = null
                },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Sign Up", fontWeight = FontWeight.Bold)
                    }
                },
                modifier = Modifier.testTag("tab_manual_register")
            )
        }

        // Error message banner
        AnimatedVisibility(visible = errorMessage != null, enter = fadeIn(), exit = fadeOut()) {
            errorMessage?.let { err ->
                Surface(
                    color = StatusRevoked.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = StatusRevoked, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = err, color = StatusRevoked, fontSize = 12.sp)
                    }
                }
            }
        }

        // Success message banner
        AnimatedVisibility(visible = successMessage != null, enter = fadeIn(), exit = fadeOut()) {
            successMessage?.let { msg ->
                Surface(
                    color = StatusActive.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusActive, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = msg, color = StatusActive, fontSize = 12.sp)
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════════
        // SECTION 1: GOOGLE ACCOUNT OPTION (SIGN IN / SIGN UP WITH GOOGLE)
        // ════════════════════════════════════════════════════════════════════════
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    GoogleLogoIcon(modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (authMode == AuthMode.SIGN_IN) "Sign In with Google" else "Sign Up with Google",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = if (authMode == AuthMode.SIGN_IN) {
                        "One-tap secure sign-in via your Google Account."
                    } else {
                        "Instant account creation with your Google Account, no password required."
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    lineHeight = 16.sp
                )

                // Quick One-Tap Google Account Button (e.g. Subbu subbutext999@gmail.com)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CyanPrimary.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .clickable(enabled = !isSubmitting) {
                            executeGoogleSignIn("subbutext999@gmail.com", "Subbu")
                        }
                        .testTag("button_google_primary_account")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            GoogleLogoIcon(modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Subbu",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "subbutext999@gmail.com",
                                fontSize = 11.sp,
                                color = CyanPrimary
                            )
                        }
                        Text(
                            text = if (authMode == AuthMode.SIGN_IN) "Sign In ➔" else "Sign Up ➔",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                }

                // Android Credential Manager Sign-In Button
                OutlinedButton(
                    onClick = { executeGoogleSignIn(null, null) },
                    enabled = !isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("button_google_credential_manager")
                ) {
                    GoogleLogoIcon(modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (authMode == AuthMode.SIGN_IN) "Sign In with Google Account" else "Sign Up with Google Account",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }

                // Expandable option to enter any other Google Account email
                if (!showCustomGoogleField) {
                    TextButton(
                        onClick = { showCustomGoogleField = true },
                        modifier = Modifier.testTag("button_show_other_google")
                    ) {
                        Text(
                            text = if (authMode == AuthMode.SIGN_IN) "Sign in with other Google email" else "Sign up with other Google email",
                            fontSize = 11.sp,
                            color = CyanPrimary
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = customGoogleEmail,
                            onValueChange = { customGoogleEmail = it },
                            label = { Text("Google Email") },
                            placeholder = { Text("you@gmail.com") },
                            leadingIcon = { GoogleLogoIcon(modifier = Modifier.size(16.dp)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_custom_google_email"),
                            singleLine = true
                        )
                        Button(
                            onClick = { executeGoogleSignIn(customGoogleEmail, null) },
                            enabled = !isSubmitting && customGoogleEmail.contains("@"),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (authMode == AuthMode.SIGN_IN) "Continue Sign In with Google" else "Create Account with Google",
                                color = Color(0xFF003049),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // ════════════════════════════════════════════════════════════════════════
        // DIVIDER: OR PROVIDE DETAILS
        // ════════════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(modifier = Modifier.weight(1f))
            Text(
                text = if (authMode == AuthMode.SIGN_IN) "  OR SIGN IN WITH DETAILS  " else "  OR SIGN UP WITH DETAILS  ",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.weight(1f))
        }

        // ════════════════════════════════════════════════════════════════════════
        // SECTION 2: MANUAL DETAILS FORM (EMAIL, PASSWORD, ETC.)
        // ════════════════════════════════════════════════════════════════════════
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (authMode == AuthMode.SIGN_IN) "Provide Account Details" else "Enter Registration Details",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Sign Up extra field: Full Name
                if (authMode == AuthMode.REGISTER) {
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Full Name") },
                        placeholder = { Text("e.g. Subbu") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auth_name"),
                        singleLine = true
                    )
                }

                // Email Address
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    placeholder = { Text("name@example.com") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_auth_email"),
                    singleLine = true
                )

                // Password
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter at least 6 characters") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_auth_password"),
                    singleLine = true
                )

                // Sign Up extra fields: Password Strength & Confirm Password
                if (authMode == AuthMode.REGISTER) {
                    PasswordStrengthBar(password = password)

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password") },
                        placeholder = { Text("Re-enter password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_auth_confirm_password"),
                        singleLine = true
                    )
                }

                // Submit Button
                Button(
                    onClick = { executeManualAuth() },
                    enabled = !isSubmitting && email.isNotBlank() && password.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (authMode == AuthMode.SIGN_IN) CyanPrimary else EmeraldTertiary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("button_submit_auth")
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Processing...", color = Color.White, fontWeight = FontWeight.Bold)
                    } else {
                        Text(
                            text = if (authMode == AuthMode.REGISTER) "Sign Up with Details" else "Sign In with Details",
                            color = if (authMode == AuthMode.SIGN_IN) Color(0xFF003049) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Quick Demo credentials helper for testing
                if (authMode == AuthMode.SIGN_IN) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(
                            onClick = {
                                email = "user@filevault.com"
                                password = "Password@2026!"
                            }
                        ) {
                            Text("⚡ Fill demo credentials", fontSize = 11.sp, color = CyanPrimary)
                        }
                    }
                }

                // Bottom Switch link: Toggle between Sign In and Sign Up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (authMode == AuthMode.SIGN_IN) "Don't have an account?" else "Already have an account?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            authMode = if (authMode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN
                            errorMessage = null
                            successMessage = null
                        }
                    ) {
                        Text(
                            text = if (authMode == AuthMode.SIGN_IN) "Sign Up" else "Sign In",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanPrimary
                        )
                    }
                }
            }
        }

        // Continue as Guest / Offline Vault
        TextButton(
            onClick = onSkip,
            modifier = Modifier.testTag("button_skip_auth")
        ) {
            Text(
                text = "Continue as Guest / Offline Vault",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * Clean vector rendering of the Google 'G' icon for Sign-In and Sign-Up with Google branding.
 */
@Composable
fun GoogleLogoIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "G",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = Color(0xFF4285F4)
            )
        }
    }
}
