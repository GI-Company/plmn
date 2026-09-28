package com.example.plmn.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plmn.data.model.AuditLog
import com.example.plmn.data.security.CryptoVaultHelper
import com.example.plmn.ui.components.CyberCard
import com.example.plmn.ui.components.StatItem
import com.example.plmn.ui.viewmodel.PLMNViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.AlertRed
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ShieldEmerald

@Composable
fun PrivacyScreen(
    viewModel: PLMNViewModel,
    modifier: Modifier = Modifier
) {
    val auditLogs by viewModel.auditLogs.collectAsState()
    val ledgerIntegrity by viewModel.ledgerIntegrity.collectAsState()
    val isAirgapEnabled by viewModel.isAirgapEnabled.collectAsState()
    val isSanitizerEnabled by viewModel.isSanitizationEnabled.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()

    var testInput by remember {
        mutableStateOf("Contact admin@company.org or ping inference node 192.168.1.120 with key sk-abcdef01234567890abcdef")
    }
    var showWipeConfirm by remember { mutableStateOf(false) }

    val scrubResult = viewModel.testScrubber(testInput)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = ShieldEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PRIVACY SHIELD & AUDIT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Hardware Keystore AES-256 GCM, client-side regex & SHA-256 hash chain",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Privacy Telemetry Stats
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                StatItem(
                    title = "Ledger Integrity",
                    value = if (ledgerIntegrity?.isValid == true) "VERIFIED" else "CHECKING",
                    subtitle = "${auditLogs.size} Linked Blocks",
                    modifier = Modifier.weight(1f),
                    accentColor = if (ledgerIntegrity?.isValid == true) ShieldEmerald else AlertAmber
                )
                StatItem(
                    title = "Session ID",
                    value = currentSessionId.take(8),
                    subtitle = "Ephemeral RAM",
                    modifier = Modifier.weight(1f),
                    accentColor = CyberCyan
                )
                StatItem(
                    title = "Vault Key",
                    value = "KEYSTORE",
                    subtitle = "AES-256 GCM",
                    modifier = Modifier.weight(1f),
                    accentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Tamper-Evident Hash Chain Verification Card
        item {
            CyberCard(
                borderColor = if (ledgerIntegrity?.isValid == true) ShieldEmerald.copy(alpha = 0.5f) else AlertRed
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = null,
                                tint = if (ledgerIntegrity?.isValid == true) ShieldEmerald else AlertAmber,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tamper-Evident SHA-256 Chain",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        IconButton(
                            onClick = { viewModel.verifyLedger() },
                            modifier = Modifier.size(32.dp).testTag("verify_ledger_button")
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Verify Chain", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Every inference, prompt sanitization, and key operation is linked via: entry[n].hash = SHA256(entry[n-1].hash || payload). Any modified or omitted entry breaks the mathematical chain.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = "Latest Hash: ${ledgerIntegrity?.latestHash?.take(24) ?: "Calculating..."}...",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = CyberCyan
                            )
                            Text(
                                text = "Status: ${if (ledgerIntegrity?.isValid == true) "100% Cryptographically Intact (${ledgerIntegrity?.totalEntries} Blocks)" else "Unverified"}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = if (ledgerIntegrity?.isValid == true) ShieldEmerald else AlertAmber
                            )
                        }
                    }
                }
            }
        }

        // Interactive PII Scrubber Playground
        item {
            CyberCard(borderColor = CyberCyan.copy(alpha = 0.5f)) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Client-Side Regex Sanitizer",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (scrubResult.redactedItems.isNotEmpty()) ShieldEmerald.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "${scrubResult.redactedItems.size} Redactions",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (scrubResult.redactedItems.isNotEmpty()) ShieldEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Type any sample prompt with emails, IPs, phone numbers, or API keys to preview client-side redaction prior to transmission:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = testInput,
                        onValueChange = { testInput = it },
                        modifier = Modifier.fillMaxWidth().testTag("scrubber_test_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyberCyan,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Sanitized Wire Payload:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = scrubResult.sanitizedText,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            ),
                            color = ShieldEmerald,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // Section Title: Audit Ledger
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    text = "IMMUTABLE AUDIT LEDGER (SHA-256 CHAIN)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }
        }

        // Audit Log Items
        if (auditLogs.isEmpty()) {
            item {
                Text(
                    text = "No audit entries recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(auditLogs.take(15), key = { it.id }) { log ->
                AuditLogChainItem(log = log)
            }
        }

        // Emergency Cryptographic Nuclear Wipe Section
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = AlertRed.copy(alpha = 0.1f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = AlertRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Cryptographic Nuclear Wipe",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AlertRed
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Permanently destroys the master AES key in Android Keystore (cryptographically unrecoverable), wipes all SQLite storage and resets session credentials.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { showWipeConfirm = true },
                        colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                        modifier = Modifier.testTag("nuclear_wipe_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete Key & Shred All Data", color = Color.White)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            title = { Text("Confirm Cryptographic Wipe") },
            text = {
                Text("This will delete the hardware-backed AES master key from Android Keystore, permanently rendering all encrypted Vault documents unrecoverable, and shred local message logs.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cryptographicNuclearWipe()
                        showWipeConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                ) {
                    Text("Execute Key Destruction", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AuditLogChainItem(log: AuditLog) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                when (log.securityStatus) {
                                    "SECURE", "REACHABLE", "PAIRING_AUTH" -> ShieldEmerald
                                    "SCRUBBED", "AIRGAP_LOCAL" -> CyberCyan
                                    else -> AlertAmber
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = log.action,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = log.securityStatus,
                    style = MaterialTheme.typography.labelSmall,
                    color = when (log.securityStatus) {
                        "SECURE", "REACHABLE", "PAIRING_AUTH" -> ShieldEmerald
                        "SCRUBBED", "AIRGAP_LOCAL" -> CyberCyan
                        else -> AlertAmber
                    },
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = log.details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )

            if (log.currentHash.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Prev: ${log.previousHash.take(8)}...",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 9.sp
                    )
                    Text(
                        text = "Block Hash: ${log.currentHash.take(12)}...",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = CyberCyan,
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}
