package net.aieat.netswissknife.app.data

import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.aieat.netswissknife.core.network.HostValidator

/**
 * A stored recent host is kept only when the shared host rule accepts it. Entries saved before
 * the rule existed (for example a garbled concatenation of two addresses) are dropped on load.
 */
fun isValidHostRecent(entry: String): Boolean = HostValidator.problemOf(entry) == null

/** Recents for a host-based tool, with entries the host rule rejects removed from the list shown. */
fun Flow<List<String>>.validHostRecents(): Flow<List<String>> = map { entries -> entries.filter(::isValidHostRecent) }

/** Drops stored entries the host rule rejects, in one write. Used once when a host tool opens. */
suspend fun RecentHostsRepository.purgeInvalidHostRecents(key: Preferences.Key<String>) {
    sanitizeRecents(key) { entry -> entry.takeIf(::isValidHostRecent) }
}
