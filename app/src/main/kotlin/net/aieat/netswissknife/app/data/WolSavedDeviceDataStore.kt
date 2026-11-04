package net.aieat.netswissknife.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.aieat.netswissknife.app.ui.screens.wol.WolSavedDevice
import net.aieat.netswissknife.app.ui.screens.wol.WolSavedDeviceStore
import net.aieat.netswissknife.app.ui.screens.wol.WolSavedDevices

/** DataStore-backed Wake-on-LAN saved devices. Reads and writes go through one key. */
class WolSavedDeviceDataStore(
    private val dataStore: DataStore<Preferences>,
) : WolSavedDeviceStore {
    override val devices: Flow<List<WolSavedDevice>> =
        dataStore.data.map { prefs -> WolSavedDevices.decode(prefs[AppPreferenceKeys.WOL_SAVED_DEVICES]) }

    override suspend fun update(transform: (List<WolSavedDevice>) -> List<WolSavedDevice>) {
        dataStore.edit { prefs ->
            val current = WolSavedDevices.decode(prefs[AppPreferenceKeys.WOL_SAVED_DEVICES])
            val updated = transform(current)
            if (updated.isEmpty()) {
                prefs.remove(AppPreferenceKeys.WOL_SAVED_DEVICES)
            } else {
                prefs[AppPreferenceKeys.WOL_SAVED_DEVICES] = WolSavedDevices.encode(updated)
            }
        }
    }
}
