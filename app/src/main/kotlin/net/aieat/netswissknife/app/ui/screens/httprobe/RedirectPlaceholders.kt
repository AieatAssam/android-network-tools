package net.aieat.netswissknife.app.ui.screens.httprobe

/** Text standing in for the parts of a redirect address that shared output leaves out. */
internal data class RedirectPlaceholders(
    val address: String,
    val path: String,
    val query: String,
) {
    companion object {
        val English =
            RedirectPlaceholders(
                address = "[redirect address omitted]",
                path = "[path omitted]",
                query = "[query omitted]",
            )
    }
}
