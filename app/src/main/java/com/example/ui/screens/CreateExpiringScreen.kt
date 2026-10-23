package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.model.VaultItem
import com.example.ui.VaultViewModel
import com.example.ui.components.ExpirationPresetSelector
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusRevoked
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExpiringScreen(
    viewModel: VaultViewModel,
    onNavigateToViewer: (ByteArray, String) -> Unit,
    onNavigateToDesktopSim: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var selectedMimeType by remember { mutableStateOf("application/octet-stream") }
    var selectedFileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var expirationTimestamp by remember {
        mutableLongStateOf(System.currentTimeMillis() + 24 * 60 * 60 * 1000) // Default 24h
    }

    // Independent password toggle
    var enablePasswordToo by remember { mutableStateOf(false) }
    var optionalPassword by remember { mutableStateOf("") }
    var passwordHint by remember { mutableStateOf("") }

    var createdItem by remember { mutableStateOf<VaultItem?>(null) }
    var isEncrypting by remember { mutableStateOf(false) }

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
                Toast.makeText(context, "Could not read file: ${e.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, "Could not open gallery media: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                            .background(AmberSecondary.copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = AmberSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Feature A: Self-Expiring Files",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Server-Controlled Key Revocation",
                            fontSize = 12.sp,
                            color = AmberSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Files are encrypted with a 256-bit AES DEK registered with the Server KMS. When the expiration timestamp arrives, the server permanently revokes decryption access. Works completely independently of password settings.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )
            }
        }

        // Section 1: File Selection
        Text(
            text = "1. Select File to Encrypt",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (selectedFileBytes == null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
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
                        tint = CyanPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Upload from Gallery or Phone Storage",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "Choose photos/videos from gallery or documents/files from device",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))

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
                                .testTag("button_upload_gallery")
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF003049), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("From Gallery", color = Color(0xFF003049), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        Button(
                            onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberSecondary),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_upload_phone")
                        ) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, tint = Color(0xFF261A00), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Phone Storage", color = Color(0xFF261A00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Or quick-load sample for testing:",
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
                                val (name, mime, bytes) = viewModel.getSampleFile("CONFIDENTIAL_NOTE")
                                selectedFileName = name
                                selectedMimeType = mime
                                selectedFileBytes = bytes
                                createdItem = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_sample_confidential")
                        ) {
                            Text("Top Secret Note", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                val (name, mime, bytes) = viewModel.getSampleFile("FINANCIAL_REPORT")
                                selectedFileName = name
                                selectedMimeType = mime
                                selectedFileBytes = bytes
                                createdItem = null
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_sample_financial")
                        ) {
                            Text("Financial CSV", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            // Selected File Card with Image Thumbnail if Media
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(CyanPrimary.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedMimeType.startsWith("image/")) Icons.Default.PhotoLibrary else Icons.Default.Description,
                                contentDescription = null,
                                tint = CyanPrimary
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

                    // Live Thumbnail Preview if user selected an image from gallery
                    if (selectedMimeType.startsWith("image/") && selectedFileBytes != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        val cachedPreview = remember(selectedFileBytes) {
                            val f = File(context.cacheDir, "picked_thumb_${selectedFileName}")
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
                                contentDescription = "Gallery image preview",
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

        // Section 2: Expiration Setting
        Text(
            text = "2. Set Server Key Expiration",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        ExpirationPresetSelector(
            selectedExpirationTimestamp = expirationTimestamp,
            onExpirationSelected = { expirationTimestamp = it }
        )

        // Section 3: Independent Password Protection (Optional)
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (enablePasswordToo) Icons.Default.Lock else Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = if (enablePasswordToo) AmberSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Require Password as well?",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Optional. Expiration works independently without a password.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = enablePasswordToo,
                        onCheckedChange = { enablePasswordToo = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmberSecondary,
                            checkedTrackColor = AmberSecondary.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("switch_expiring_password")
                    )
                }

                AnimatedVisibility(visible = enablePasswordToo) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = optionalPassword,
                            onValueChange = { optionalPassword = it },
                            label = { Text("Password") },
                            placeholder = { Text("Enter a password") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("input_expiring_password"),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        PasswordStrengthBar(password = optionalPassword)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordHint,
                            onValueChange = { passwordHint = it },
                            label = { Text("Optional Password Hint") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Section 4: Action Button
        Button(
            onClick = {
                val bytes = selectedFileBytes ?: return@Button
                val name = selectedFileName ?: "document.bin"
                isEncrypting = true
                if (enablePasswordToo && optionalPassword.isNotEmpty()) {
                    viewModel.encryptDualProtected(
                        fileName = name,
                        mimeType = selectedMimeType,
                        fileBytes = bytes,
                        password = optionalPassword,
                        expirationTimestamp = expirationTimestamp,
                        passwordHint = passwordHint.ifBlank { null }
                    ) { item ->
                        isEncrypting = false
                        createdItem = item
                    }
                } else {
                    viewModel.encryptSelfExpiring(
                        fileName = name,
                        mimeType = selectedMimeType,
                        fileBytes = bytes,
                        expirationTimestamp = expirationTimestamp
                    ) { item ->
                        isEncrypting = false
                        createdItem = item
                    }
                }
            },
            enabled = selectedFileBytes != null && !isEncrypting && (!enablePasswordToo || optionalPassword.isNotEmpty()),
            colors = ButtonDefaults.buttonColors(
                containerColor = AmberSecondary,
                contentColor = Color(0xFF261A00)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("button_encrypt_expiring")
        ) {
            if (isEncrypting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color(0xFF261A00),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Registering with Server KMS...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Encrypt & Register with KMS", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Section 5: Distribution & Sharing Channels (Email, Secure Link, Desktop Viewer)
        createdItem?.let { item ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, AmberSecondary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = StatusActive
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Self-Expiring .efile Ready!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "File Key ID: ${item.keyId}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = AmberSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Share Encrypted File:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Channel 1: Email Attachment
                    Button(
                        onClick = {
                            val file = File(item.efilePath)
                            if (file.exists()) {
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val expDateStr = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                    .format(Date(expirationTimestamp))

                                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_SUBJECT, "Secure Self-Expiring File: ${item.originalFileName}")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        """
                                            Hello,

                                            I have shared a confidential, self-expiring encrypted file with you:
                                            File: ${item.originalFileName}
                                            Expiration Deadline: $expDateStr

                                            This file is protected by Server-Controlled Key Management.
                                            Once expired, the server will permanently revoke decryption access.

                                            Open with EFile Vault or view in an authorized desktop browser.
                                            Secure Viewer Link: ${item.secureShareLink}
                                        """.trimIndent()
                                    )
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(emailIntent, "Send via Email"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_share_email")
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share via Email Attachment", color = Color(0xFF003049))
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Channel 2: Secure Link
                    FilledTonalButton(
                        onClick = {
                            item.secureShareLink?.let { link ->
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Secure Link", link))
                                Toast.makeText(context, "Secure link copied to clipboard!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_copy_secure_link")
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Copy Secure Link")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Channel 3: Authorized Desktop Viewing
                    OutlinedButton(
                        onClick = {
                            item.keyId?.let { onNavigateToDesktopSim(it) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_desktop_view")
                    ) {
                        Icon(Icons.Default.Computer, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Simulate Authorized Desktop Viewing")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Channel 4: Open in Local Viewer to test decryption
                    OutlinedButton(
                        onClick = {
                            val file = File(item.efilePath)
                            if (file.exists()) {
                                onNavigateToViewer(file.readBytes(), item.originalFileName)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_test_local_unlock")
                    ) {
                        Icon(Icons.Default.LockOpen, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Test Decryption in Compatible Viewer")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Sender Revoke Action
                    Button(
                        onClick = {
                            item.keyId?.let { viewModel.revokeServerKey(it) }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StatusRevoked,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("button_instant_revoke")
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Revoke Decryption Access Now (KMS)")
                    }
                }
            }
        }
    }
}
