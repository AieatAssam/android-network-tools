package com.example.netswissknife.app.ui.screens

import androidx.compose.runtime.Composable
import com.example.netswissknife.app.ui.screens.lan.LanScreen as LanScanScreen

/**
 * Top-level navigation target for the LAN Scanner.
 * Delegates to the full [LanScanScreen] implementation.
 */
@Composable
fun LanScreen() {
    LanScanScreen()
}
