package net.aieat.netswissknife.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object AppShapes {
    val small: RoundedCornerShape = RoundedCornerShape(8.dp)
    val medium: RoundedCornerShape = RoundedCornerShape(12.dp)
    val large: RoundedCornerShape = RoundedCornerShape(20.dp)
    val pill: RoundedCornerShape = RoundedCornerShape(50.dp)
}

object AppSpacing {
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 16.dp
    val l: Dp = 24.dp
    val xl: Dp = 32.dp
    /** Standard gap between cards in a tool screen. */
    val cardGap: Dp = 16.dp
    /** Horizontal screen padding. */
    val screenPadding: Dp = 16.dp
}
