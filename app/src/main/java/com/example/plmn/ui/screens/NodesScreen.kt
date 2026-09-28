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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellWifi
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plmn.data.model.NodeEndpoint
import com.example.plmn.ui.components.CyberCard
import com.example.plmn.ui.viewmodel.PLMNViewModel
import com.example.ui.theme.AlertAmber
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ShieldEmerald

@Composable
fun NodesScreen(
    viewModel: PLMNViewModel,
    modifier: Modifier = Modifier
) {
    val allNodes by viewModel.allNodes.collectAsState()
    val activeNode by viewModel.activeNode.collectAsState()
    val isAirgapEnabled by viewModel.isAirgapEnabled.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var showPairPLMHDialog by remember { mutableStateOf(false) }
    var editingNode by remember { mutableStateOf<NodeEndpoint?>(null) }

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
                                imageVector = Icons.Default.Hub,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PLMH & INFERENCE MESH",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "PLMH desktop protocol, INTENT engine, and LAN socket routing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Airgap Master Switch
        item {
            CyberCard(
                borderColor = if (isAirgapEnabled) ShieldEmerald else MaterialTheme.colorScheme.outline
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (isAirgapEnabled) Icons.Default.WifiOff else Icons.Default.CellWifi,
                            contentDescription = null,
                            tint = if (isAirgapEnabled) ShieldEmerald else CyberCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isAirgapEnabled) "Airgap Mode: ENGAGED" else "Airgap Mode: OFF",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isAirgapEnabled) ShieldEmerald else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isAirgapEnabled)
                                    "Network sockets bypassed. Local-first on-device inference locked."
                                else
                                    "LAN and authenticated hub endpoints permitted.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Switch(
                        checked = isAirgapEnabled,
                        onCheckedChange = { viewModel.toggleAirgap(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ShieldEmerald,
                            checkedTrackColor = ShieldEmerald.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("airgap_master_switch")
                    )
                }
            }
        }

        // PLMH Desktop Hub Hero Banner
        item {
            CyberCard(
                borderColor = CyberCyan.copy(alpha = 0.6f)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = CyberCyan.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "PRIMARY PROTOCOL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PLMH Desktop Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Button(
                            onClick = { showPairPLMHDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("pair_plmh_button")
                        ) {
                            Icon(Icons.Default.Devices, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Pair Hub", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "PLMH (Private Language Model Hub) is your self-hosted desktop inference coordinator. Android Keystore ECDSA P-256 device signatures authenticate PLMN directly to INTENT, MLX, llama.cpp, or Ollama runtimes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )
                }
            }
        }

        // Section Title: Configured Nodes
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                Text(
                    text = "CONFIGURED INFERENCE ENDPOINTS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )

                OutlinedButton(
                    onClick = { showAddDialog = true },
                    modifier = Modifier.testTag("add_endpoint_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Endpoint", fontSize = 11.sp)
                }
            }
        }

        // Nodes List
        items(allNodes, key = { it.id }) { node ->
            val isActive = node.id == activeNode?.id
            NodeCard(
                node = node,
                isActive = isActive,
                onSelectActive = { viewModel.switchActiveNode(node.id) },
                onPing = { viewModel.pingNode(node) },
                onEdit = { editingNode = node },
                onDelete = { viewModel.deleteNode(node) },
                canDelete = node.endpointType != "EMBEDDED"
            )
        }

        // Architecture info
        item {
            CyberCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "PLMN Unified Protocol Architecture",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "PLMN Android Client\n" +
                                "  └── PLM Protocol (ECDSA Device Auth + SSE Streaming)\n" +
                                "        └── PLMH Desktop Hub (192.168.x.x:8080)\n" +
                                "              ├── INTENT Neural Model (Native)\n" +
                                "              ├── MLX Runtime (Apple Silicon)\n" +
                                "              ├── llama.cpp (GGUF)\n" +
                                "              └── Ollama / LM Studio Backends",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Pair PLMH Dialog
    if (showPairPLMHDialog) {
        PairPLMHDialog(
            onDismiss = { showPairPLMHDialog = false },
            onPair = { url, token, name ->
                viewModel.pairPLMHHub(url, token, name)
                showPairPLMHDialog = false
            }
        )
    }

    // Add Node Dialog
    if (showAddDialog) {
        NodeEditDialog(
            node = null,
            onDismiss = { showAddDialog = false },
            onSave = { newNode, secret ->
                viewModel.saveNode(newNode, secret)
                showAddDialog = false
            }
        )
    }

    // Edit Node Dialog
    editingNode?.let { nodeToEdit ->
        NodeEditDialog(
            node = nodeToEdit,
            onDismiss = { editingNode = null },
            onSave = { updated, secret ->
                viewModel.saveNode(updated, secret)
                editingNode = null
            }
        )
    }
}

@Composable
fun PairPLMHDialog(
    onDismiss: () -> Unit,
    onPair: (url: String, token: String, name: String) -> Unit
) {
    var url by remember { mutableStateOf("http://192.168.1.100:8080") }
    var token by remember { mutableStateOf("plmh_auth_tok_") }
    var name by remember { mutableStateOf("Home Desktop PLMH Hub") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Devices, contentDescription = null, tint = CyberCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Pair PLMH Desktop Hub")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter your desktop PLMH hub socket address and one-time authentication token to establish mutual HMAC encrypted streaming.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Hub Friendly Name") },
                    modifier = Modifier.fillMaxWidth().testTag("plmh_name_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("PLMH Hub URL / Socket") },
                    modifier = Modifier.fillMaxWidth().testTag("plmh_url_input"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Pairing / Auth Token") },
                    modifier = Modifier.fillMaxWidth().testTag("plmh_token_input"),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPair(url, token, name) },
                enabled = url.isNotBlank() && token.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Complete Pairing", color = Color.Black, fontWeight = FontWeight.Bold)
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
fun NodeCard(
    node: NodeEndpoint,
    isActive: Boolean,
    onSelectActive: () -> Unit,
    onPing: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    CyberCard(
        borderColor = if (isActive) CyberCyan else MaterialTheme.colorScheme.outline
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSelectActive() }
                ) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Active status",
                        tint = if (isActive) CyberCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (isActive) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = CyberCyan.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CyberCyan,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${node.endpointType} • ${node.selectedModel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val level = try {
                        com.example.plmn.inference.NodeVerificationLevel.valueOf(node.verificationLevel)
                    } catch (_: Exception) {
                        com.example.plmn.inference.NodeVerificationLevel.CONFIGURED
                    }
                    val isVerifiedGreen = level.isVerified
                    val statusColor = when {
                        isVerifiedGreen -> ShieldEmerald
                        level == com.example.plmn.inference.NodeVerificationLevel.REACHABLE -> AlertAmber
                        else -> MaterialTheme.colorScheme.outline
                    }

                    val badgeText = buildString {
                        append(level.name)
                        if (node.latencyMs > 0) append(" (${node.latencyMs}ms)")
                        if (!node.lastFailureCode.isNullOrBlank()) append(" - ${node.lastFailureCode}")
                    }

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onPing() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkCheck,
                                contentDescription = "Ping",
                                tint = statusColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusColor,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    if (canDelete) {
                        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
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

            Spacer(modifier = Modifier.height(6.dp))

            Surface(
                color = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = node.url,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeEditDialog(
    node: NodeEndpoint?,
    onDismiss: () -> Unit,
    onSave: (NodeEndpoint, String?) -> Unit
) {
    var name by remember { mutableStateOf(node?.name ?: "") }
    var endpointType by remember { mutableStateOf(node?.endpointType ?: "PLMH") }
    var url by remember { mutableStateOf(node?.url ?: "http://192.168.1.100:8080") }
    var model by remember { mutableStateOf(node?.selectedModel ?: "intent-1m") }
    var apiKey by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf(node?.systemPrompt ?: "You are a private LLM node.") }

    var expandedTypeDropdown by remember { mutableStateOf(false) }
    val endpointTypes = listOf("PLMH", "OLLAMA", "LM_STUDIO", "LLAMA_CPP", "OPENAI_COMPATIBLE", "ON_DEVICE", "CUSTOM")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (node == null) "Add Private Node Endpoint" else "Edit Node Endpoint")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Node Name") },
                    modifier = Modifier.fillMaxWidth().testTag("node_name_input"),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = expandedTypeDropdown,
                    onExpandedChange = { expandedTypeDropdown = !expandedTypeDropdown }
                ) {
                    OutlinedTextField(
                        value = endpointType,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Endpoint Architecture") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTypeDropdown) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTypeDropdown,
                        onDismissRequest = { expandedTypeDropdown = false }
                    ) {
                        endpointTypes.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type) },
                                onClick = {
                                    endpointType = type
                                    expandedTypeDropdown = false
                                    when (type) {
                                        "PLMH" -> if (url.isBlank() || url.contains("11434") || url.contains("1234")) url = "http://192.168.1.100:8080"
                                        "OLLAMA" -> if (url.isBlank() || url.contains("8080") || url.contains("1234")) url = "http://192.168.1.100:11434"
                                        "LM_STUDIO" -> if (url.isBlank() || url.contains("11434") || url.contains("8080")) url = "http://192.168.1.100:1234/v1"
                                        "LLAMA_CPP" -> if (url.isBlank()) url = "http://192.168.1.100:8080"
                                        "OPENAI_COMPATIBLE" -> if (url.isBlank()) url = "http://192.168.1.100:8000/v1"
                                        "ON_DEVICE" -> url = "local://on-device-processor"
                                    }
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Base URL / Socket") },
                    modifier = Modifier.fillMaxWidth().testTag("node_url_input"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    label = { Text("Model Identifier") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (endpointType != "ON_DEVICE" && endpointType != "OLLAMA") {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("Auth Token / API Key (Keystore Encrypted)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Instruction Persona") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalNode = NodeEndpoint(
                        id = node?.id ?: ("node-" + System.currentTimeMillis()),
                        name = name.ifBlank { "Private Node" },
                        endpointType = endpointType,
                        url = url.trim(),
                        selectedModel = model.trim(),
                        credentialRef = node?.credentialRef,
                        isActive = node?.isActive ?: false,
                        systemPrompt = systemPrompt.trim()
                    )
                    onSave(finalNode, apiKey.ifBlank { null })
                },
                enabled = name.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan)
            ) {
                Text("Save Node", color = Color.Black)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
