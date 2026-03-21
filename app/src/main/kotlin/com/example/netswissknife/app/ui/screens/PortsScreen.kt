package com.example.netswissknife.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable

@Composable
fun PortsScreen() {
    ToolPlaceholderContent(
        toolName    = "Port Scanner",
        toolIcon    = Icons.Default.Search,
        description = "Scan TCP/UDP ports on any host to check which services are reachable.",
        features    = listOf(
            "Custom port range and common presets",
            "Service name resolution per port",
            "Animated real-time progress bar",
            "Save and share scan reports"
        )
    )
}
