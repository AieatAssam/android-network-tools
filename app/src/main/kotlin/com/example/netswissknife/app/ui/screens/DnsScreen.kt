package com.example.netswissknife.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable

@Composable
fun DnsScreen() {
    ToolPlaceholderContent(
        toolName    = "DNS Lookup",
        toolIcon    = Icons.Default.Language,
        description = "Query DNS records for any domain — A, AAAA, MX, TXT, CNAME, NS and more.",
        features    = listOf(
            "All record types (A, MX, TXT, CNAME, NS, SOA)",
            "Custom DNS server support",
            "Reverse lookup (PTR)",
            "Response time measurement"
        )
    )
}
