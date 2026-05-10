package net.aieat.netswissknife.app.ui.navigation

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("AppNavigationViewModel – pinned route persistence")
class AppNavigationViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var viewModel: AppNavigationViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "test_prefs.preferences_pb") }
        )
        viewModel = AppNavigationViewModel(dataStore)
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `defaults to default pinned routes when no value is stored`() = testScope.runTest {
            advanceUntilIdle()
            assertEquals(AppNavigationViewModel.DEFAULT_PINNED_ROUTES, viewModel.pinnedRoutes.value)
        }

        @Test
        fun `reads stored routes from DataStore on start`() = testScope.runTest {
            dataStore.edit { it[AppPreferenceKeys.PINNED_ROUTES] = "traceroute|wifi_scan" }
            val vm = AppNavigationViewModel(dataStore)
            advanceUntilIdle()
            assertEquals(listOf("traceroute", "wifi_scan"), vm.pinnedRoutes.value)
        }
    }

    @Nested
    @DisplayName("togglePin")
    inner class TogglePin {

        @Test
        fun `pinning a new route appends it`() = testScope.runTest {
            advanceUntilIdle()
            // Default fills MAX_PINNED; free a slot first then pin a new route
            viewModel.togglePin("ping")
            advanceUntilIdle()
            viewModel.togglePin("traceroute")
            advanceUntilIdle()
            assertTrue(viewModel.pinnedRoutes.value.contains("traceroute"))
        }

        @Test
        fun `unpinning a route removes it`() = testScope.runTest {
            advanceUntilIdle()
            // "ping" is in default list; unpin it
            viewModel.togglePin("ping")
            advanceUntilIdle()
            assertFalse(viewModel.pinnedRoutes.value.contains("ping"))
        }

        @Test
        fun `pinning beyond MAX_PINNED is ignored`() = testScope.runTest {
            advanceUntilIdle()
            // default already has 3 routes (MAX_PINNED); adding a 4th should be ignored
            assertEquals(AppNavigationViewModel.MAX_PINNED, viewModel.pinnedRoutes.value.size)
            viewModel.togglePin("traceroute")
            advanceUntilIdle()
            assertEquals(AppNavigationViewModel.MAX_PINNED, viewModel.pinnedRoutes.value.size)
        }

        @Test
        fun `toggled routes are written to DataStore`() = testScope.runTest {
            advanceUntilIdle()
            viewModel.togglePin("ping") // remove ping from defaults
            advanceUntilIdle()

            val stored = dataStore.data.first()[AppPreferenceKeys.PINNED_ROUTES]
            assertFalse(stored?.contains("ping") ?: false)
        }

        @Test
        fun `pinned routes survive a ViewModel recreation`() = testScope.runTest {
            advanceUntilIdle()
            viewModel.togglePin("ping") // unpin ping
            viewModel.togglePin("traceroute") // pin traceroute (slot freed)
            advanceUntilIdle()

            val newVm = AppNavigationViewModel(dataStore)
            advanceUntilIdle()

            assertFalse(newVm.pinnedRoutes.value.contains("ping"))
            assertTrue(newVm.pinnedRoutes.value.contains("traceroute"))
        }
    }
}
