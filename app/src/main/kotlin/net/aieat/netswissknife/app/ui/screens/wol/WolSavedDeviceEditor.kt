package net.aieat.netswissknife.app.ui.screens.wol

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.aieat.netswissknife.core.network.wol.WolMagicPacket

/**
 * Saved Wake-on-LAN devices and the add, edit, use and delete actions on them.
 *
 * [currentMac] reads the MAC field and [fillMac] writes a device's MAC back into it, so the send
 * form stays the one owner of the MAC that is sent. Split from WakeOnLanViewModel to keep that
 * class focused on the send flow.
 */
class WolSavedDeviceEditor(
    private val store: WolSavedDeviceStore,
    private val scope: CoroutineScope,
    private val currentMac: () -> String,
    private val fillMac: (String) -> Unit,
) {
    val savedDevices: StateFlow<List<WolSavedDevice>> =
        store.devices.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    /** Canonical MAC of the saved device being edited, or null when saving a new one. */
    private val _editingMac = MutableStateFlow<String?>(null)
    val editingMac: StateFlow<String?> = _editingMac.asStateFlow()

    /** Explains what happened to the name on the last save of an already saved MAC. */
    private val _saveNotice = MutableStateFlow<WolSaveNotice?>(null)
    val saveNotice: StateFlow<WolSaveNotice?> = _saveNotice.asStateFlow()

    /** Set when an edit would make two saved devices share a MAC; nothing is changed. */
    private val _saveConflict = MutableStateFlow(false)
    val saveConflict: StateFlow<Boolean> = _saveConflict.asStateFlow()

    /** Editing the MAC field clears a previous duplicate-MAC refusal. */
    fun clearSaveConflict() {
        _saveConflict.value = false
    }

    fun onDeviceNameChange(value: String) {
        _deviceName.value = value.take(WolSavedDevices.MAX_NAME_LENGTH)
        _saveNotice.value = null
    }

    /** Saves the current MAC under the entered name, or updates the saved device being edited. */
    fun saveDevice() {
        val canonical = WolMagicPacket.canonicalMacOrNull(currentMac()) ?: return
        val name = _deviceName.value
        val original = _editingMac.value
        if (original != null) {
            _saveNotice.value = null
            val outcome = WolSavedDevices.edit(savedDevices.value, original, canonical, name)
            if (outcome is WolSaveOutcome.Duplicate) {
                _saveConflict.value = true
                return
            }
            scope.launch {
                store.update { list ->
                    when (val result = WolSavedDevices.edit(list, original, canonical, name)) {
                        is WolSaveOutcome.Saved -> result.devices
                        WolSaveOutcome.Duplicate -> list
                    }
                }
            }
            _editingMac.value = null
        } else {
            _saveNotice.value = WolSavedDevices.saveNotice(savedDevices.value, canonical, name)
            scope.launch {
                store.update { list -> WolSavedDevices.upsert(list, canonical, name) }
            }
        }
        _saveConflict.value = false
        _deviceName.value = ""
    }

    fun deleteDevice(mac: String) {
        scope.launch {
            store.update { list -> WolSavedDevices.remove(list, mac) }
        }
        if (_editingMac.value == mac) cancelEdit()
    }

    /** Fills the MAC and name from a saved device. Does not send. */
    fun useDevice(device: WolSavedDevice) {
        _saveNotice.value = null
        _editingMac.value = null
        _deviceName.value = device.name
        fillMac(device.mac)
    }

    fun startEditDevice(device: WolSavedDevice) {
        _saveNotice.value = null
        _editingMac.value = device.mac
        _deviceName.value = device.name
        fillMac(device.mac)
    }

    fun cancelEdit() {
        _editingMac.value = null
        _deviceName.value = ""
        _saveConflict.value = false
        _saveNotice.value = null
    }
}
