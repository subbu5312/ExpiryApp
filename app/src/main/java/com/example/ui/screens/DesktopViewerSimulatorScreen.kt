package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kms.KmsAccessResult
import com.example.model.ServerKeyRecord
import com.example.ui.VaultViewModel
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusRevoked
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
fun DesktopViewerSimulatorScreen(
    keyId: String,
    viewModel: VaultViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var isLoading by remember { mutableStateOf(true) }
    var keyRecord by remember { mutableStateOf<ServerKeyRecord?>(null) }
    var accessResult by remember { mutableStateOf<KmsAccessResult?>(null) }
    var decryptedTextContent by remember { mutableStateOf<String?>(null) }

    fun checkDesktopAccess() {
        scope.launch {
            isLoading = true
            keyRecord = viewModel.kmsService.getKeyRecord(keyId)

            val result = viewModel.kmsService.requestDecryptionKey(
                keyId = keyId,
                clientAgent = "Desktop Web Portal (macOS 15 / Chrome 129)",
                ipAddress = "192.168.1.140 (Authorized Desktop Workstation)"
            )
            accessResult = result

            if (result is KmsAccessResult.Success) {
                // Find matching vault file if present to simulate content rendering
                val vaultItem = viewModel.uiState.value.items.find { it.keyId == keyId }
                if (vaultItem != null) {
                    val file = File(vaultItem.efilePath)
                    if (file.exists()) {
                        viewModel.repository.decryptEFile(
                            efileBytes = file.readBytes(),
                            clientAgent = "Desktop Web Portal (macOS 15)"
                        ).onSuccess { dec ->
                            decryptedTextContent = dec.textContent ?: "[Binary Document Loaded Securely in Desktop Sandbox: ${dec.fileName} (${dec.bytes.size / 1024} KB)]"
                        }
                    }
                }
                viewModel.kmsService.logDesktopAccess(keyId, "Chrome Desktop Agent", "Authorized View Session Opened")
            } else {
                decryptedTextContent = null
                viewModel.kmsService.logDesktopAccess(keyId, "Chrome Desktop Agent", "Access Denied by KMS Policy")
            }
            isLoading = false
        }
    }

    LaunchedEffect(keyId) {
        checkDesktopAccess()
    }

    BackHandler { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Navigation Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "Authorized Desktop Viewing",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Server KMS Web Portal Gateway",
                    fontSize = 12.sp,
                    color = CyanPrimary
                )
            }
        }

        // Mock Desktop Browser Window Frame
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
        ) {
            Column {
                // Browser Chrome / Title Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Window control dots
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFEF4444), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFF59E0B), CircleShape))
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF10B981), CircleShape))
                    Spacer(modifier = Modifier.width(12.dp))

                    // Address Bar
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = StatusActive,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "https://desktop.efilevault.com/portal/view?key=${keyId.take(14)}...",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { checkDesktopAccess() },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Desktop Portal Body
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = CyanPrimary)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Connecting to KMS Authority Server...",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    } else {
                        when (val result = accessResult) {
                            is KmsAccessResult.Success -> {
                                // Authorized Desktop Portal State
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusActive)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Authorized Desktop Session Active",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = StatusActive
                                        )
                                    }

                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Token: ${keyRecord?.desktopAuthToken ?: "VALID"}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = CyanPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Expiration Countdown Banner
                                keyRecord?.expiresAt?.let { exp ->
                                    val now = System.currentTimeMillis()
                                    val diffSeconds = (exp - now) / 1000
                                    val hours = diffSeconds / 3600
                                    val minutes = (diffSeconds % 3600) / 60

                                    Surface(
                                        color = AmberSecondary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AccessTime,
                                                contentDescription = null,
                                                tint = AmberSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Server Revocation in: ${hours}h ${minutes}m (${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(exp))})",
                                                fontSize = 11.sp,
                                                color = AmberSecondary,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                // Secure Render Window with Watermark
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(240.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Watermark
                                        Text(
                                            text = "CONFIDENTIAL • AUTHORIZED DESKTOP ONLY • DO NOT DISTRIBUTE",
                                            color = Color.White.copy(alpha = 0.05f),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.align(Alignment.Center)
                                        )

                                        // Decrypted Content
                                        SelectionContainer {
                                            Text(
                                                text = decryptedTextContent ?: "[File content loaded in authorized viewer]",
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color(0xFFE2E8F0),
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(12.dp)
                                                    .verticalScroll(rememberScrollState())
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Client: Mac Desktop Chrome • IP: 192.168.1.140",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )
                                    Text(
                                        text = "KMS: Online",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = StatusActive
                                    )
                                }
                            }

                            is KmsAccessResult.Expired -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = StatusRevoked,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "403 ACCESS REVOKED (EXPIRED)",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = StatusRevoked
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = result.message,
                                            fontSize = 12.sp,
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 16.sp
                                        )
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = "The server has withheld the DEK because the expiration deadline has elapsed. The encrypted payload cannot be unlocked.",
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            }

                            is KmsAccessResult.Revoked -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = null,
                                            tint = StatusRevoked,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "ACCESS REVOKED BY SENDER",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = StatusRevoked
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = result.message,
                                            fontSize = 12.sp,
                                            color = Color(0xFFCBD5E1)
                                        )
                                    }
                                }
                            }

                            else -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Unable to resolve key from server.",
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Test Revocation Live Button to immediately see the desktop portal react
        keyRecord?.let { rec ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Sender Control Verification",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Revoke access now to test server-controlled enforcement on this desktop session:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.revokeServerKey(keyId)
                                checkDesktopAccess()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRevoked),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_test_desktop_revoke")
                        ) {
                            Text("Revoke Now", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                val currentExp = rec.expiresAt ?: System.currentTimeMillis()
                                viewModel.extendServerKey(keyId, currentExp + 24 * 60 * 60 * 1000)
                                checkDesktopAccess()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("button_test_desktop_extend")
                        ) {
                            Text("Extend (+24h)", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
