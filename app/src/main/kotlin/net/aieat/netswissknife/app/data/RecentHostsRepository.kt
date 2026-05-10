package net.aieat.netswissknife.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_RECENTS = 5
private const val SEPARATOR = "|"

@Singleton
class RecentHostsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    fun getRecents(key: Preferences.Key<String>): Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[key]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
        }

    suspend fun addRecent(key: Preferences.Key<String>, host: String) {
        val trimmed = host.trim()
        if (trimmed.isBlank()) return
        dataStore.edit { prefs ->
            val existing = prefs[key]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = (listOf(trimmed) + existing.filter { it != trimmed }).take(MAX_RECENTS)
            prefs[key] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun removeRecent(key: Preferences.Key<String>, host: String) {
        dataStore.edit { prefs ->
            val existing = prefs[key]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()
            val updated = existing.filter { it != host }
            if (updated.isEmpty()) prefs.remove(key) else prefs[key] = updated.joinToString(SEPARATOR)
        }
    }

    suspend fun clearAll(key: Preferences.Key<String>) {
        dataStore.edit { it.remove(key) }
    }
}
