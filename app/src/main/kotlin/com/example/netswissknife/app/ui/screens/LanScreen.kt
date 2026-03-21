package com.example.netswissknife.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.runtime.Composable

@Composable
fun LanScreen() {
    ToolPlaceholderContent(
        toolName    = "LAN Scanner",
        toolIcon    = Icons.Default.Wifi,
        description = "Discover all active devices on your local Wi-Fi or Ethernet network.",
        features    = listOf(
            "ARP-based host discovery",
            "Vendor/OUI identification from MAC address",
            "Open port summary per device",
            "Network topology map view"
        )
    )
}
