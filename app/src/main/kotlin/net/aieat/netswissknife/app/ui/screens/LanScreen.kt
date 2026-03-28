package net.aieat.netswissknife.app.ui.screens

import androidx.compose.runtime.Composable
import net.aieat.netswissknife.app.ui.screens.lan.LanScreen as LanScanScreen

/**
 * Top-level navigation target for the LAN Scanner.
 * Delegates to the full [LanScanScreen] implementation.
 */
@Composable
fun LanScreen() {
    LanScanScreen()
}
