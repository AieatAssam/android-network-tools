package net.aieat.netswissknife.app.ui.screens.wol

/** Explains a save that did not simply add a new device, so the user can see what happened to the name. */
sealed interface WolSaveNotice {
    /** The MAC was already saved and its name was kept (no new name was typed, or it matched). */
    data class KeptName(
        val name: String,
    ) : WolSaveNotice

    /** The MAC was already saved under [previousName] and is now called [name]. */
    data class Renamed(
        val previousName: String,
        val name: String,
    ) : WolSaveNotice
}
