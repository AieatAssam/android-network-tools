package net.aieat.netswissknife.app.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.aieat.netswissknife.app.R

/**
 * Shape of a duration shown to the user. [humanDuration] picks the shape; [humanDurationText]
 * renders it through localisable strings. Kept as data so the choice of unit is testable without
 * Android resources.
 */
sealed interface HumanDuration {
    /** Under one second: whole milliseconds. */
    data class Milliseconds(
        val ms: Long,
    ) : HumanDuration

    /** One second up to one minute: seconds with one decimal place, e.g. 30.6 s. */
    data class Seconds(
        val seconds: Double,
    ) : HumanDuration

    /** Whole minutes with no remainder, e.g. 2 min. */
    data class Minutes(
        val minutes: Long,
    ) : HumanDuration

    /** One minute or more with a seconds remainder, e.g. 1 min 12 s. */
    data class MinutesSeconds(
        val minutes: Long,
        val seconds: Long,
    ) : HumanDuration
}

private const val MS_PER_TENTH = 100L
private const val MS_PER_SECOND = 1_000L
private const val TENTHS_PER_SECOND = 10L
private const val TENTHS_PER_MINUTE = 600L
private const val SECONDS_PER_MINUTE = 60L

/**
 * Chooses how to show a duration of [ms] milliseconds: milliseconds below one second, seconds with
 * one decimal below one minute, then minutes and seconds (rounded to the nearest second).
 * Negative input is treated as zero. Only use this for durations of work, not for latency
 * measurements such as ping round-trip times, which stay in milliseconds.
 */
fun humanDuration(ms: Long): HumanDuration {
    val safe = ms.coerceAtLeast(0L)
    // Rounded half-up to tenths of a second, and to whole seconds for the minutes branch.
    val tenths = (safe + MS_PER_TENTH / 2L) / MS_PER_TENTH
    val totalSeconds = (safe + MS_PER_SECOND / 2L) / MS_PER_SECOND
    return when {
        safe < MS_PER_SECOND -> HumanDuration.Milliseconds(safe)
        tenths < TENTHS_PER_MINUTE -> HumanDuration.Seconds(tenths.toDouble() / TENTHS_PER_SECOND)
        else -> minutesAndSeconds(totalSeconds)
    }
}

private fun minutesAndSeconds(totalSeconds: Long): HumanDuration {
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (seconds == 0L) {
        HumanDuration.Minutes(minutes)
    } else {
        HumanDuration.MinutesSeconds(minutes, seconds)
    }
}

/** Localised text for [humanDuration]. */
@Composable
@Suppress("FunctionNaming", "ktlint:standard:function-naming")
fun humanDurationText(ms: Long): String =
    when (val duration = humanDuration(ms)) {
        is HumanDuration.Milliseconds -> {
            stringResource(R.string.duration_milliseconds_format, duration.ms)
        }

        is HumanDuration.Seconds -> {
            stringResource(R.string.duration_seconds_format, duration.seconds)
        }

        is HumanDuration.Minutes -> {
            stringResource(R.string.duration_minutes_format, duration.minutes)
        }

        is HumanDuration.MinutesSeconds -> {
            stringResource(R.string.duration_minutes_seconds_format, duration.minutes, duration.seconds)
        }
    }
