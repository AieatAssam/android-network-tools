package com.example.netswissknife.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Router
import androidx.compose.runtime.Composable

@Composable
fun TracerouteScreen() {
    ToolPlaceholderContent(
        toolName    = "Traceroute",
        toolIcon    = Icons.Default.Router,
        description = "Trace the network path to any host, revealing each hop and its latency.",
        features    = listOf(
            "Animated hop-by-hop path display",
            "Reverse DNS resolution per hop",
            "Geographic location per hop",
            "Max hops and timeout configuration"
        )
    )
}
