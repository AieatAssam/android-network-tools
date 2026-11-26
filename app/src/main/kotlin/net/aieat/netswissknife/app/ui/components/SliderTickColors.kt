package net.aieat.netswissknife.app.ui.components

import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Above this many stops a slider's tick marks are noise rather than real stops, so they are hidden. */
internal const val MANY_SLIDER_STEPS = 10

/** Default slider colours for [steps] of 10 or fewer; colours with the tick marks made transparent above that. */
@Composable
internal fun sliderColorsFor(steps: Int): SliderColors =
    if (steps > MANY_SLIDER_STEPS) {
        SliderDefaults.colors(
            activeTickColor = Color.Transparent,
            inactiveTickColor = Color.Transparent,
            disabledActiveTickColor = Color.Transparent,
            disabledInactiveTickColor = Color.Transparent,
        )
    } else {
        SliderDefaults.colors()
    }
