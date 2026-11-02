package net.aieat.netswissknife.app.ui.screens.lan

/** Which view of the same filtered device list is shown: the device rows or the network map. */
enum class LanResultView {
    LIST,
    MAP,
    ;

    companion object {
        /** A fresh result opens on the list, which carries every per-device detail. */
        val DEFAULT: LanResultView = LIST

        /** Parses a stored name; anything unknown or missing falls back to [DEFAULT]. */
        fun fromStoredName(name: String?): LanResultView = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/** A map tap reveals a device row, which exists only in the list, so the reveal always shows the list. */
internal fun LanResultView.afterReveal(): LanResultView = LanResultView.LIST
