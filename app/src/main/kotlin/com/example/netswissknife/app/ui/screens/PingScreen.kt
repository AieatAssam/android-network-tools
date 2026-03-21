package com.example.netswissknife.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.runtime.Composable

@Composable
fun PingScreen() {
    ToolPlaceholderContent(
        toolName    = "Ping",
        toolIcon    = Icons.Default.NetworkCheck,
        description = "Send ICMP echo requests to measure round-trip time and packet loss to any host.",
        features    = listOf(
            "Continuous ping with live chart",
            "Configurable packet size and TTL",
            "Min / avg / max / jitter statistics",
            "Export results as CSV"
        )
    )
}
