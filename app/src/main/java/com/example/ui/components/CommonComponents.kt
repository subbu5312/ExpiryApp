package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuditLogEntry
import com.example.ui.theme.AmberSecondary
import com.example.ui.theme.CyanPrimary
import com.example.ui.theme.EmeraldTertiary
import com.example.ui.theme.StatusActive
import com.example.ui.theme.StatusExpiring
import com.example.ui.theme.StatusRevoked
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun StatusBadge(
    status: String,
    isExpiring: Boolean,
    expiresAt: Long?,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val isActuallyExpired = isExpiring && expiresAt != null && expiresAt <= now
    val effectiveStatus = if (isActuallyExpired && status == "ACTIVE") "EXPIRED" else status

    val (bgColor, textColor, label, icon) = when (effectiveStatus) {
        "ACTIVE" -> {
            if (isExpiring && expiresAt != null) {
                val diffHours = (expiresAt - now) / (1000 * 60 * 60)
                val timeLabel = if (diffHours > 24) "${diffHours / 24}d left" else "${diffHours.coerceAtLeast(1)}h left"
                Quad(AmberSecondary.copy(alpha = 0.2f), AmberSecondary, "Expiring ($timeLabel)", Icons.Default.Timer)
            } else {
                Quad(StatusActive.copy(alpha = 0.2f), StatusActive, "Active (Permanent)", Icons.Default.CheckCircle)
            }
        }
        "EXPIRED" -> Quad(StatusRevoked.copy(alpha = 0.2f), StatusRevoked, "Access Expired", Icons.Default.AccessTime)
        "REVOKED_BY_OWNER", "REVOKED" -> Quad(StatusRevoked.copy(alpha = 0.2f), StatusRevoked, "Revoked by Owner", Icons.Outlined.Cancel)
        else -> Quad(Color.Gray.copy(alpha = 0.2f), Color.LightGray, status, Icons.Default.Info)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun PasswordStrengthBar(
    password: String,
    modifier: Modifier = Modifier
) {
    val strength = remember(password) {
        calculatePasswordStrength(password)
    }

    val (progress, color, label) = when {
        password.isEmpty() -> Triple(0f, Color.Gray, "Enter a secure password")
        strength < 2 -> Triple(0.25f, Color(0xFFEF4444), "Weak (Add symbols & length)")
        strength < 4 -> Triple(0.6f, Color(0xFFF59E0B), "Moderate (Add numbers & uppercase)")
        strength < 5 -> Triple(0.85f, Color(0xFF3B82F6), "Strong")
        else -> Triple(1f, EmeraldTertiary, "Fortified (256-bit PBKDF2)")
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Key Derivation Strength:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

private fun calculatePasswordStrength(password: String): Int {
    if (password.length < 6) return 1
    var score = 0
    if (password.length >= 8) score++
    if (password.length >= 12) score++
    if (password.any { it.isUpperCase() }) score++
    if (password.any { it.isDigit() }) score++
    if (password.any { !it.isLetterOrDigit() }) score++
    return score
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ExpirationPresetSelector(
    selectedExpirationTimestamp: Long,
    onExpirationSelected: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCustomPicker by remember { mutableStateOf(false) }

    val presets = listOf(
        "1 Hour" to (1L * 60 * 60 * 1000),
        "6 Hours" to (6L * 60 * 60 * 1000),
        "24 Hours" to (24L * 60 * 60 * 1000),
        "3 Days" to (3L * 24 * 60 * 60 * 1000),
        "7 Days" to (7L * 24 * 60 * 60 * 1000),
        "30 Days" to (30L * 24 * 60 * 60 * 1000)
    )

    Column(modifier = modifier) {
        Text(
            text = "Select Expiration Duration",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { (label, durationMillis) ->
                val targetTime = System.currentTimeMillis() + durationMillis
                val isSelected = Math.abs(selectedExpirationTimestamp - targetTime) < 5000

                AssistChip(
                    onClick = {
                        onExpirationSelected(System.currentTimeMillis() + durationMillis)
                    },
                    label = { Text(label) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AccessTime,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    colors = if (isSelected) {
                        AssistChipDefaults.assistChipColors(
                            containerColor = AmberSecondary.copy(alpha = 0.25f),
                            labelColor = AmberSecondary
                        )
                    } else {
                        AssistChipDefaults.assistChipColors()
                    },
                    modifier = Modifier.testTag("preset_$label")
                )
            }

            AssistChip(
                onClick = { showCustomPicker = true },
                label = { Text("Custom Date...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.testTag("preset_custom")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        val expDateStr = SimpleDateFormat("EEE, MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
            .format(Date(selectedExpirationTimestamp))

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
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = AmberSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Server Revocation Scheduled For:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = expDateStr,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberSecondary
                    )
                }
            }
        }
    }

    if (showCustomPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedExpirationTimestamp
        )
        DatePickerDialog(
            onDismissRequest = { showCustomPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            // Set to end of chosen day (23:59:59)
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = dateMillis
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                            }
                            onExpirationSelected(cal.timeInMillis)
                        }
                        showCustomPicker = false
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun AuditLogList(
    logs: List<AuditLogEntry>,
    modifier: Modifier = Modifier
) {
    if (logs.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No access attempts logged yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp
            )
        }
    } else {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            logs.forEach { log ->
                val timeStr = SimpleDateFormat("HH:mm:ss MMM dd", Locale.getDefault())
                    .format(Date(log.timestamp))

                val (badgeBg, badgeText, badgeColor) = when {
                    log.action.contains("GRANTED") || log.action.contains("CREATED") ->
                        Triple(StatusActive.copy(alpha = 0.15f), log.action, StatusActive)
                    log.action.contains("DENIED") || log.action.contains("REVOKED") ->
                        Triple(StatusRevoked.copy(alpha = 0.15f), log.action, StatusRevoked)
                    else ->
                        Triple(CyanPrimary.copy(alpha = 0.15f), log.action, CyanPrimary)
                }

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    color = badgeColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = timeStr,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.detail,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Client: ${log.clientAgent} • IP: ${log.ipAddress}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
