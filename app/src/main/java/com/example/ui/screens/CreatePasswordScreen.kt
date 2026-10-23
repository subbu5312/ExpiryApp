package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.VaultItem
import com.example.ui.VaultViewModel
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusRevoked
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePasswordScreen(
    viewModel: VaultViewModel,
    onNavigateToViewer: (ByteArray, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedMimeType by remember { mutableStateOf("application/octet-stream") }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }

    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordHint by remember { mutableStateOf("") }

    var isEncrypting by remember { mutableStateOf(false) }
    var createdItem by remember { mutableStateOf<VaultItem?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val (bytes, mime) = viewModel.repository.readUriBytes(it)
                val name = viewModel.repository.getFileName(it)
                selectedFileName = name
                selectedMimeType = mime
                selectedFileBytes = bytes
                createdItem = null
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val (bytes, mime) = viewModel.repository.readUriBytes(it)
                val name = viewModel.repository.getFileName(it)
                selectedFileName = name
                selectedMimeType = mime
                selectedFileBytes = bytes
                createdItem = null
            } catch (e: Exception) {
                Toast.makeText(context, "Could not open gallery file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val passwordsMatch = password.isNotEmpty() && password == confirmPassword
    val canEncrypt = selectedFileBytes != null && passwordsMatch && !isEncrypting

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Header Banner
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(EmeraldTertiary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = EmeraldTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Feature B: Password-Protect File",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Permanent AES-256-GCM Protection",
                            fontSize = 12.sp,
                            color = EmeraldTertiary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Encrypt any file with a custom password into a protected .efile container. By default, password-protected files do NOT expire. They remain secure indefinitely until unlocked with the correct password.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // Step 1: Select File
        Text(
            text = "1. Select Any File",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (selectedFileBytes == null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        RoundedCornerShape(12.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = EmeraldTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Pick any file to protect with password",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Any format: PDF, images, video, text, zip, code",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    // Gallery or Phone options
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_upload_gallery_pw")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF003049), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Gallery", color = Color(0xFF003049), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_upload_phone_pw")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF003822), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phone Storage", color = Color(0xFF003822), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Or quick-load sample file:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val (name, mime, bytes) = viewModel.getSampleFile("SERVER_KEY_MANIFEST")
                                selectedFileName = name
                                selectedMimeType = mime
                                selectedFileBytes = bytes
                                createdItem = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_sample_manifest")
                        ) {
                            Text("Security Policy JSON", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val (name, mime, bytes) = viewModel.getSampleFile("CONFIDENTIAL_NOTE")
                                selectedFileName = name
                                selectedMimeType = mime
                                selectedFileBytes = bytes
                                createdItem = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_sample_secret_memo")
                        ) {
                            Text("Confidential Memo", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(EmeraldTertiary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedMimeType.startsWith("image/")) Icons.Default.PhotoLibrary else Icons.Default.Description,
                                contentDescription = null,
                                tint = EmeraldTertiary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedFileName ?: "Selected file",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${(selectedFileBytes?.size ?: 0) / 1024} KB • $selectedMimeType",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedFileBytes = null
                                selectedFileName = null
                                createdItem = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Remove selection",
                                tint = StatusRevoked
                            )
                        }
                    }

                    // Live Thumbnail Preview if user selected an image
                    if (selectedMimeType.startsWith("image/") && selectedFileBytes != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val cachedPreview = remember(selectedFileBytes) {
                            val f = File(context.cacheDir, "picked_thumb_pw_${selectedFileName}")
                            f.writeBytes(selectedFileBytes!!)
                            f
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = cachedPreview,
                                contentDescription = "Selected image preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                galleryPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change (Gallery)", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Change (Phone)", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // Step 2: Set Password
        Text(
            text = "2. Set User Password",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Encryption Password") },
                    placeholder = { Text("Enter a strong password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_password"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))
                PasswordStrengthBar(password = password)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm Password") },
                    placeholder = { Text("Re-enter password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPassword.isNotEmpty() && confirmPassword != password,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_confirm_password"),
                    singleLine = true
                )

                if (confirmPassword.isNotEmpty() && confirmPassword != password) {
                    Text(
                        text = "Passwords do not match",
                        color = StatusRevoked,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = passwordHint,
                    onValueChange = { passwordHint = it },
                    label = { Text("Password Hint (Optional)") },
                    placeholder = { Text("e.g., Mom's first pet or company badge #") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_password_hint"),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = EmeraldTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "By default, this file will NEVER expire. It remains safely encrypted until unlocked with this password.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Step 3: Action Button
        Button(
            onClick = {
                val bytes = selectedFileBytes ?: return@Button
                val name = selectedFileName ?: "protected_file.bin"
                isEncrypting = true
                viewModel.encryptPasswordProtected(
                    fileName = name,
                    mimeType = selectedMimeType,
                    fileBytes = bytes,
                    password = password,
                    passwordHint = passwordHint.ifBlank { null }
                ) { item ->
                    isEncrypting = false
                    createdItem = item
                }
            },
            enabled = canEncrypt,
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldTertiary,
                contentColor = Color(0xFF003822)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("button_generate_efile")
        ) {
            if (isEncrypting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF003822),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Deriving PBKDF2 & Encrypting...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Lock, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Generate Protected .efile", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Output Card
        createdItem?.let { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, EmeraldTertiary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = StatusActive)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Protected .efile Created!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Saved as: ${item.originalFileName.substringBeforeLast('.')}_protected.efile",
                        fontSize = 12.sp,
                        color = EmeraldTertiary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // Open in Local Compatible Viewer
                    Button(
                        onClick = {
                            val file = File(item.efilePath)
                            if (file.exists()) {
                                onNavigateToViewer(file.readBytes(), item.originalFileName)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_unlock_in_viewer")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, tint = Color(0xFF003822))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open in Compatible Viewer", color = Color(0xFF003822), fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Share .efile
                    OutlinedButton(
                        onClick = {
                            val file = File(item.efilePath)
                            if (file.exists()) {
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Password-Protected .efile: ${item.originalFileName}")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Attached is a password-protected .efile container. Unlock using the compatible EFile Vault viewer with the designated password."
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share .efile"))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_share_protected_efile")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share .efile Container")
                    }
                }
            }
        }
    }
}
