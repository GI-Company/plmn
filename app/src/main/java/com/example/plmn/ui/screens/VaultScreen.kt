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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plmn.data.model.VaultDocument
import com.example.plmn.data.security.CryptoVaultHelper
import com.example.plmn.ui.components.CyberCard
import com.example.plmn.ui.components.StatItem
import com.example.plmn.ui.viewmodel.PLMNViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ShieldEmerald

@Composable
fun VaultScreen(
    viewModel: PLMNViewModel,
    modifier: Modifier = Modifier
) {
    val documents by viewModel.vaultDocuments.collectAsState()
    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingDoc by remember { mutableStateOf<VaultDocument?>(null) }

    val categories = listOf("All", "Active Context", "Secret", "Note", "Code", "Specs")

    val filteredDocs = documents.filter { doc ->
        val matchesCategory = when (selectedCategory) {
            "All" -> true
            "Active Context" -> doc.isContextActive
            else -> doc.category.equals(selectedCategory, ignoreCase = true)
        }
        val matchesSearch = searchQuery.isBlank() ||
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.decryptedContent.contains(searchQuery, ignoreCase = true) ||
                doc.tags.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    val activeCount = documents.count { it.isContextActive }
    val totalTokens = documents.sumOf { CryptoVaultHelper.estimateTokenCount(it.decryptedContent) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Vault Header
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = ShieldEmerald,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENCRYPTED MEMORY VAULT",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ShieldEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "KEYSTORE AES-GCM",
                                style = MaterialTheme.typography.labelSmall,
                                color = ShieldEmerald,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Stats Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatItem(
                            title = "Stored Docs",
                            value = "${documents.size}",
                            modifier = Modifier.weight(1f),
                            accentColor = MaterialTheme.colorScheme.onSurface
                        )
                        StatItem(
                            title = "Active Context",
                            value = "$activeCount",
                            subtitle = "Injected in Turn",
                            modifier = Modifier.weight(1f),
                            accentColor = ShieldEmerald
                        )
                        StatItem(
                            title = "Decrypted Tok",
                            value = "$totalTokens",
                            modifier = Modifier.weight(1f),
                            accentColor = CyberCyan
                        )
                    }
                }
            }

            // Search bar & Filters
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search vault documents, secrets, tags...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth().testTag("vault_search_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyberCyan.copy(alpha = 0.2f),
                                selectedLabelColor = CyberCyan
                            )
                        )
                    }
                }
            }

            // Documents List
            if (filteredDocs.isEmpty()) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Vault Documents Found",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add encrypted notes or private docs below to inject into your PLMN node context.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredDocs, key = { it.id }) { doc ->
                        VaultDocCard(
                            doc = doc,
                            onToggleContext = { active -> viewModel.toggleVaultContext(doc.id, active) },
                            onClick = { viewingDoc = doc },
                            onDelete = { viewModel.deleteVaultDoc(doc) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
            }
        }

        // Add Document FAB
        FloatingActionButton(
            onClick = { showAddDialog = true },
            containerColor = CyberCyan,
            contentColor = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("add_vault_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Document")
        }
    }

    // Add Document Dialog
    if (showAddDialog) {
        AddVaultDocDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, content, cat, tags ->
                viewModel.addVaultDoc(title, content, cat, tags)
                showAddDialog = false
            }
        )
    }

    // View Document Dialog
    viewingDoc?.let { doc ->
        ViewVaultDocDialog(
            doc = doc,
            onDismiss = { viewingDoc = null },
            onDelete = {
                viewModel.deleteVaultDoc(doc)
                viewingDoc = null
            }
        )
    }
}

@Composable
fun VaultDocCard(
    doc: VaultDocument,
    onToggleContext: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    CyberCard(
        borderColor = if (doc.isContextActive) ShieldEmerald.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
        onClick = onClick
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (doc.category.lowercase()) {
                            "secret" -> AlertAmber.copy(alpha = 0.2f)
                            "code" -> CyberCyan.copy(alpha = 0.2f)
                            else -> ShieldEmerald.copy(alpha = 0.2f)
                        }
                    ) {
                        Text(
                            text = doc.category.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = when (doc.category.lowercase()) {
                                "secret" -> AlertAmber
                                "code" -> CyberCyan
                                else -> ShieldEmerald
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = doc.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Inject Context",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (doc.isContextActive) ShieldEmerald else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Switch(
                        checked = doc.isContextActive,
                        onCheckedChange = onToggleContext,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShieldEmerald,
                            checkedTrackColor = ShieldEmerald.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = doc.decryptedContent.ifBlank { "[Encrypted Keystore Payload]" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${CryptoVaultHelper.estimateTokenCount(doc.decryptedContent)} tokens • Tags: ${doc.tags.ifBlank { "none" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = AlertAmber,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddVaultDocDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, content: String, category: String, tags: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Note") }
    var tags by remember { mutableStateOf("") }

    val categories = listOf("Note", "Secret", "Code", "Specs", "Credentials")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = "Add Encrypted Knowledge Document")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Document Title") },
                    modifier = Modifier.fillMaxWidth().testTag("vault_doc_title_input"),
                    singleLine = true
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.take(3).forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Content (Encrypted with Keystore AES-GCM)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .testTag("vault_doc_content_input"),
                    maxLines = 6
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, content, category, tags) },
                enabled = title.isNotBlank() && content.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Encrypt & Save", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ViewVaultDocDialog(
    doc: VaultDocument,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
        },
        text = {
            Column {
                Text(
                    text = "Category: ${doc.category} • Keystore AES-256 GCM • Tokens: ${CryptoVaultHelper.estimateTokenCount(doc.decryptedContent)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = doc.decryptedContent,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        ),
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(doc.decryptedContent))
                }
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Copy")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDelete,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = AlertAmber)
            ) {
                Text("Delete")
            }
        }
    )
}
