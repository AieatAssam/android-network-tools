package net.aieat.netswissknife.app.ui.i18n

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/** A piece of UI copy that can be resolved using the current Android locale. */
sealed interface UiText {
    data class Res(
        @StringRes val id: Int,
        val args: List<Any?> = emptyList(),
        /** Diagnostic copy kept for logs and legacy state consumers. */
        val developerFallback: String? = null,
    ) : UiText

    data class Plain(val text: String) : UiText
}

/** Resolve resource-backed UI copy at the Compose boundary. */
@Composable
fun UiText.asString(): String = when (this) {
    is UiText.Res -> stringResource(id, *args.map { it ?: "" }.toTypedArray())
    is UiText.Plain -> text
}
