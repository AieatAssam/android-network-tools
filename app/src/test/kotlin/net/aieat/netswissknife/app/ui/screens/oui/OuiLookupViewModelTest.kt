package net.aieat.netswissknife.app.ui.screens.oui

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.OuiLookupReport
import net.aieat.netswissknife.core.domain.OuiLookupUseCase
import net.aieat.netswissknife.core.network.oui.MacAddressInfo
import net.aieat.netswissknife.core.network.oui.MacAddressKind
import net.aieat.netswissknife.core.network.oui.MacInputProblem
import net.aieat.netswissknife.core.network.oui.MacLookupEntry
import net.aieat.netswissknife.core.network.oui.VendorResult
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OuiLookupViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val useCase = mockk<OuiLookupUseCase>()
    private val recents = mockk<RecentHostsRepository>(relaxed = true)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { recents.getRecents(AppPreferenceKeys.RECENT_OUI_LOOKUPS) } returns flowOf(emptyList())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = OuiLookupViewModel(useCase, recents)

    private fun address(input: String) =
        MacLookupEntry.Address(
            input,
            MacAddressInfo(
                hex = "B827EB112233",
                kind = MacAddressKind.UNICAST,
                locallyAdministered = false,
                vendor = VendorResult.Unknown,
            ),
        )

    private fun rejected(input: String) = MacLookupEntry.Rejected(input, MacInputProblem.LENGTH)

    @Test
    fun `starts idle with an empty input and no invalid entries`() {
        val vm = viewModel()

        assertEquals("", vm.uiState.value.input)
        assertEquals(OuiLookupPhase.Idle, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.invalidEntryCount)
    }

    @Test
    fun `a blank lookup does nothing`() {
        val vm = viewModel()

        vm.onLookup()

        assertEquals(OuiLookupPhase.Idle, vm.uiState.value.phase)
        coVerify(exactly = 0) { useCase.invoke(any()) }
    }

    @Test
    fun `a lookup shows its results and remembers each valid entry`() {
        coEvery { useCase.invoke(any()) } returns OuiLookupReport(listOf(address("B8:27:EB:11:22:33")), false)
        val vm = viewModel()

        vm.onInputChange("B8:27:EB:11:22:33")
        vm.onLookup()

        val phase = vm.uiState.value.phase
        assertTrue(phase is OuiLookupPhase.Results, "got $phase")
        assertEquals(1, (phase as OuiLookupPhase.Results).entries.size)
        coVerify { recents.addRecent(AppPreferenceKeys.RECENT_OUI_LOOKUPS, "B8:27:EB:11:22:33") }
    }

    @Test
    fun `shows a loading state until the lookup completes`() {
        val gate = CompletableDeferred<OuiLookupReport>()
        coEvery { useCase.invoke(any()) } coAnswers { gate.await() }
        val vm = viewModel()
        vm.onInputChange("B8:27:EB")

        vm.onLookup()
        assertEquals(OuiLookupPhase.Loading, vm.uiState.value.phase)

        gate.complete(OuiLookupReport(listOf(address("B8:27:EB")), false))
        assertTrue(vm.uiState.value.phase is OuiLookupPhase.Results)
    }

    @Test
    fun `when every entry is rejected the lookup fails and nothing is remembered`() {
        coEvery { useCase.invoke(any()) } returns OuiLookupReport(listOf(rejected("nope")), false)
        val vm = viewModel()

        vm.onInputChange("nope")
        vm.onLookup()

        assertEquals(OuiLookupPhase.Failed(OuiLookupFailure.NO_VALID_ENTRY), vm.uiState.value.phase)
        coVerify(exactly = 0) { recents.addRecent(any(), any()) }
    }

    @Test
    fun `counts the entries that are not valid as the user types`() {
        val vm = viewModel()

        vm.onInputChange("B8:27:EB:11:22:33, nope\nzz")

        assertEquals(2, vm.uiState.value.invalidEntryCount)
    }

    @Test
    fun `pasting clipboard text fills the input and looks it up`() {
        coEvery { useCase.invoke(any()) } returns OuiLookupReport(listOf(address("02:00:00:00:00:01")), false)
        val vm = viewModel()

        vm.onClipboardText("02:00:00:00:00:01")

        assertEquals("02:00:00:00:00:01", vm.uiState.value.input)
        assertTrue(vm.uiState.value.phase is OuiLookupPhase.Results)
    }

    @Test
    fun `an empty clipboard is reported without a lookup`() {
        val vm = viewModel()

        vm.onClipboardText(null)
        assertEquals(OuiLookupPhase.Failed(OuiLookupFailure.CLIPBOARD_EMPTY), vm.uiState.value.phase)

        vm.onClipboardText("   ")
        assertEquals(OuiLookupPhase.Failed(OuiLookupFailure.CLIPBOARD_EMPTY), vm.uiState.value.phase)
        coVerify(exactly = 0) { useCase.invoke(any()) }
    }

    @Test
    fun `choosing a recent fills the input and looks it up`() {
        coEvery { useCase.invoke(any()) } returns OuiLookupReport(listOf(address("B8:27:EB")), false)
        val vm = viewModel()

        vm.onRecentSelected("B8:27:EB")

        assertEquals("B8:27:EB", vm.uiState.value.input)
        assertTrue(vm.uiState.value.phase is OuiLookupPhase.Results)
    }

    @Test
    fun `clear empties the input and drops the result`() {
        coEvery { useCase.invoke(any()) } returns OuiLookupReport(listOf(address("B8:27:EB")), false)
        val vm = viewModel()
        vm.onRecentSelected("B8:27:EB")

        vm.onClear()

        assertEquals("", vm.uiState.value.input)
        assertEquals(OuiLookupPhase.Idle, vm.uiState.value.phase)
    }

    @Test
    fun `exposes the saved recent lookups from the repository`() {
        every { recents.getRecents(AppPreferenceKeys.RECENT_OUI_LOOKUPS) } returns flowOf(listOf("B8:27:EB"))

        val vm = viewModel()

        assertEquals(listOf("B8:27:EB"), vm.recentLookups.value)
    }

    @Test
    fun `removing and clearing recents go to the repository`() {
        val vm = viewModel()

        vm.onRecentRemoved("B8:27:EB")
        vm.onRecentsCleared()

        coVerify { recents.removeRecent(AppPreferenceKeys.RECENT_OUI_LOOKUPS, "B8:27:EB") }
        coVerify { recents.clearAll(AppPreferenceKeys.RECENT_OUI_LOOKUPS) }
    }
}
