package net.aieat.netswissknife.app.ui.theme

import androidx.compose.ui.graphics.Color

// Semantic status colours used for latency, signal strength, and certificate validity.
val StatusGood     = Color(0xFF4CAF50)
val StatusOk       = Color(0xFF8BC34A)
val StatusLime     = Color(0xFFCDDC39)
val StatusWarn     = Color(0xFFFFC107)
val StatusBad      = Color(0xFFFF9800)
val StatusCritical = Color(0xFFF44336)
val StatusUnknown  = Color(0xFF9E9E9E)
val StatusBlue     = Color(0xFF2196F3)

// Deeper variants with enough contrast for text/icons on light surfaces.
val StatusGoodDeep = Color(0xFF2E7D32)
val StatusWarnDeep = Color(0xFFF57F17)
val StatusBlueDeep = Color(0xFF1565C0)

// Categorical accents (HTTP method chips, status-code gradients).
val AccentGreenLight  = Color(0xFF66BB6A)
val AccentBlueLight   = Color(0xFF42A5F5)
val AccentOrangeDeep  = Color(0xFFE65100)
val AccentOrangeLight = Color(0xFFFFB74D)
val AccentRedDeep     = Color(0xFFB71C1C)
val AccentRedLight    = Color(0xFFEF9A9A)
val AccentGreyDeep    = Color(0xFF616161)
val AccentGreyLight   = Color(0xFFBDBDBD)
val AccentPurple      = Color(0xFF6A1B9A)
val AccentTeal        = Color(0xFF00695C)
val AccentBrown       = Color(0xFF4E342E)

// Categorical palette for the Wi-Fi spectrum analyser SSID curves.
val SpectrumPalette = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF5722), Color(0xFF9C27B0),
    Color(0xFFFF9800), Color(0xFF00BCD4), Color(0xFFE91E63), Color(0xFF8BC34A),
    Color(0xFF3F51B5), Color(0xFFFFC107), Color(0xFF009688), Color(0xFFFF5252),
)
