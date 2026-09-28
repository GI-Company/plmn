package com.example.plmn.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plmn.ui.screens.NodesScreen
import com.example.plmn.ui.screens.PrivacyScreen
import com.example.plmn.ui.screens.TerminalScreen
import com.example.plmn.ui.screens.VaultScreen
import com.example.plmn.ui.viewmodel.PLMNViewModel
import com.example.ui.theme.CyberCyan

data class NavItem(
    val title: String,
    val icon: ImageVector,
    val tag: String
)

@Composable
fun PLMNApp(
    viewModel: PLMNViewModel,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val statusNotice by viewModel.statusNotice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusNotice) {
        statusNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearNotice()
        }
    }

    val navItems = listOf(
        NavItem("Terminal", Icons.Default.Terminal, "nav_terminal"),
        NavItem("Vault", Icons.Default.Lock, "nav_vault"),
        NavItem("Mesh Nodes", Icons.Default.Hub, "nav_nodes"),
        NavItem("Privacy", Icons.Default.Shield, "nav_privacy")
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        val isSelected = selectedTab == index
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { viewModel.selectTab(index) },
                            icon = {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title
                                )
                            },
                            label = {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = CyberCyan,
                                selectedTextColor = CyberCyan,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = CyberCyan.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag(item.tag)
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = selectedTab, label = "tab_transition") { tabIndex ->
                when (tabIndex) {
                    0 -> TerminalScreen(
                        viewModel = viewModel,
                        onNavigateToVault = { viewModel.selectTab(1) },
                        onNavigateToNodes = { viewModel.selectTab(2) }
                    )
                    1 -> VaultScreen(viewModel = viewModel)
                    2 -> NodesScreen(viewModel = viewModel)
                    3 -> PrivacyScreen(viewModel = viewModel)
                }
            }
        }
    }
}
