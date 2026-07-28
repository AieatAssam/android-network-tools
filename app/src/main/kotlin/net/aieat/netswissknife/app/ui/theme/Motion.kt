package net.aieat.netswissknife.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Centralized M3 motion tokens (see m3.material.io/styles/motion/easing-and-duration).
 * Screens should build [tween] specs from these instead of hardcoding durations/easings,
 * so entrance/exit/state-change animation feel stays consistent app-wide.
 */
object AppMotion {

    // ── Easing curves ──────────────────────────────────────────────────────────
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // ── Duration tokens (ms) ────────────────────────────────────────────────────
    const val DurationShort = 100
    const val DurationMedium = 300
    const val DurationLong = 500

    /** Entrance / expanding transitions — content appearing, growing, moving in. */
    fun <T> enter(durationMillis: Int = DurationMedium): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedDecelerate)

    /** Exit / collapsing transitions — content disappearing, shrinking, moving out. */
    fun <T> exit(durationMillis: Int = DurationShort): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = EmphasizedAccelerate)

    /** Non-spatial changes — color/alpha crossfades with no shape or position change. */
    fun <T> effect(durationMillis: Int = DurationMedium): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = Standard)
}
