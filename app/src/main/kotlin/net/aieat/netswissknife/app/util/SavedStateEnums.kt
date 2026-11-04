package net.aieat.netswissknife.app.util

/**
 * The constant of [entries] whose name was saved in a SavedStateHandle, or null when nothing was
 * saved or the saved name no longer matches a constant.
 */
internal fun <E : Enum<E>> enumFromSavedName(
    savedName: String?,
    entries: List<E>,
): E? = entries.firstOrNull { it.name == savedName }
