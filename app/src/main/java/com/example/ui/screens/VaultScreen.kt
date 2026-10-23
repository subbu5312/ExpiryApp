package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.core.content.FileProvider
import com.example.model.VaultItem
import com.example.ui.VaultFilter
import com.example.ui.VaultViewModel
import com.example.ui.components.AuditLogList
import com.example.ui.components.StatusBadge
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusRevoked
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onNavigateToViewer: (ByteArray, String) -> Unit,
    onNavigateToDesktopSim: (String) -> Unit,
    onNavigateToCreateExpiring: () -> Unit,
    onNavigateToCreatePassword: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    var showDetailsSheet by remember { mutableStateOf(false) }
    var showAuditLogsDialog by remember { mutableStateOf(false) }
    var itemToDelete by remember { mutableStateOf<VaultItem?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Top Summary Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Vault & KMS Console",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${uiState.items.size} encrypted containers • ${uiState.items.count { it.isExpiring }} expiring",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(VaultFilter.values()) { filter ->
                val label = when (filter) {
                    VaultFilter.ALL -> "All Files"
                    VaultFilter.SELF_EXPIRING -> "Self-Expiring"
                    VaultFilter.PASSWORD_PROTECTED -> "Password-Protected"
                    VaultFilter.ACTIVE -> "Active Access"
                    VaultFilter.REVOKED_EXPIRED -> "Revoked / Expired"
                }
                val isSelected = uiState.activeFilter == filter

                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = CyanPrimary
                    ),
                    modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.filteredItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No encrypted files found",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Create a self-expiring file with server revocation or password protect any file.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onNavigateToCreateExpiring,
                            colors = ButtonDefaults.buttonColors(containerColor = AmberSecondary),
                            modifier = Modifier.testTag("button_empty_create_expiring")
                        ) {
                            Text("Self-Expiring", color = Color(0xFF261A00))
                        }
                        Button(
                            onClick = onNavigateToCreatePassword,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldTertiary),
                            modifier = Modifier.testTag("button_empty_create_password")
                        ) {
                            Text("Password File", color = Color(0xFF003822))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(uiState.filteredItems, key = { it.id }) { item ->
                    VaultItemCard(
                        item = item,
                        onOpen = {
                            viewModel.setSelectedItem(item)
                            val file = File(item.efilePath)
                            if (file.exists()) {
                                onNavigateToViewer(file.readBytes(), item.originalFileName)
                            } else {
                                Toast.makeText(context, "Container file missing on disk", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onManage = {
                            viewModel.setSelectedItem(item)
                            showDetailsSheet = true
                        },
                        onDelete = {
                            itemToDelete = item
                        }
                    )
                }
            }
        }
    }

    // Modal Details & Key Management Sheet
    if (showDetailsSheet && uiState.selectedItem != null) {
        val selected = uiState.selectedItem!!
        val sheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            onDismissRequest = { showDetailsSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = selected.originalFileName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${selected.fileSizeBytes / 1024} KB • ${selected.mimeType}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    StatusBadge(
                        status = selected.serverStatus,
                        isExpiring = selected.isExpiring,
                        expiresAt = selected.expirationTimestamp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(14.dp))

                // Protection Details
                if (selected.isExpiring) {
                    Text(
                        text = "Server-Controlled Key Management",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = AmberSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Key ID: ${selected.keyId ?: "N/A"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    selected.expirationTimestamp?.let { exp ->
                        val dateStr = SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a", Locale.getDefault()).format(Date(exp))
                        Text(
                            text = "Expires: $dateStr",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Key Management Actions: Revoke & Extend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                selected.keyId?.let { viewModel.revokeServerKey(it) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRevoked),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_button_revoke")
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Revoke Key Access", fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                selected.keyId?.let {
                                    val currentExp = selected.expirationTimestamp ?: System.currentTimeMillis()
                                    viewModel.extendServerKey(it, currentExp + 24 * 60 * 60 * 1000)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_button_extend")
                        ) {
                            Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+24h Extend", fontSize = 12.sp)
                        }
                    }
                }

                if (selected.isPasswordProtected) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Password Protected (AES-256-GCM)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = EmeraldTertiary
                    )
                    selected.passwordHint?.let { hint ->
                        Text(
                            text = "Hint: $hint",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Distribution Actions
                Text(
                    text = "Share & Viewing Channels",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Email Attachment
                    Button(
                        onClick = {
                            val file = File(selected.efilePath)
                            if (file.exists()) {
                                val contentUri = FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    file
                                )
                                val emailIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    putExtra(Intent.EXTRA_SUBJECT, "Protected File: ${selected.originalFileName}")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Please find the encrypted file attached. Open with EFile Vault.\nSecure Link: ${selected.secureShareLink ?: "N/A"}"
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(emailIntent, "Send Email"))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sheet_share_email")
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, tint = Color(0xFF003049))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Email", color = Color(0xFF003049), fontSize = 12.sp)
                    }

                    // Secure Link Copy
                    if (selected.secureShareLink != null) {
                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Secure Link", selected.secureShareLink))
                                Toast.makeText(context, "Secure link copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_copy_link")
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy Link", fontSize = 12.sp)
                        }
                    }

                    // Desktop Simulator
                    if (selected.keyId != null) {
                        OutlinedButton(
                            onClick = {
                                showDetailsSheet = false
                                onNavigateToDesktopSim(selected.keyId)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sheet_desktop_sim")
                        ) {
                            Icon(Icons.Default.Computer, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Desktop", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Audit Logs Button
                if (selected.keyId != null) {
                    OutlinedButton(
                        onClick = {
                            showAuditLogsDialog = true
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("sheet_button_audit_logs")
                    ) {
                        Icon(Icons.Default.History, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("View Server Key Access & Audit Trail (${uiState.currentAuditLogs.size})")
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Audit Logs Dialog
    if (showAuditLogsDialog && uiState.selectedItem?.keyId != null) {
        AlertDialog(
            onDismissRequest = { showAuditLogsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyanPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Server KMS Audit Trail")
                }
            },
            text = {
                AuditLogList(logs = uiState.currentAuditLogs)
            },
            confirmButton = {
                TextButton(onClick = { showAuditLogsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Delete confirmation dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete Encrypted File?") },
            text = { Text("This will permanently delete '${item.originalFileName}' and its .efile container from the vault.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StatusRevoked)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun VaultItemCard(
    item: VaultItem,
    onOpen: () -> Unit,
    onManage: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .testTag("card_item_${item.id.take(6)}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconBg = if (item.isExpiring) AmberSecondary.copy(alpha = 0.2f) else EmeraldTertiary.copy(alpha = 0.2f)
                val iconTint = if (item.isExpiring) AmberSecondary else EmeraldTertiary
                val iconVector = if (item.isExpiring) Icons.Default.Timer else Icons.Default.Lock

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(iconBg, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = iconVector, contentDescription = null, tint = iconTint)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.originalFileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.fileSizeBytes / 1024} KB • ${SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(item.createdAt))}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                StatusBadge(
                    status = item.serverStatus,
                    isExpiring = item.isExpiring,
                    expiresAt = item.expirationTimestamp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.isExpiring) {
                        Surface(
                            color = AmberSecondary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "KMS Escrow",
                                color = AmberSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.isPasswordProtected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = EmeraldTertiary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Password",
                                color = EmeraldTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row {
                    TextButton(onClick = onOpen) {
                        Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Unlock", fontSize = 12.sp)
                    }
                    IconButton(onClick = onManage) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Manage")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRevoked.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}
