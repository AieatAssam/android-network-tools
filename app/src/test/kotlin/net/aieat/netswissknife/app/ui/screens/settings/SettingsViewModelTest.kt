package net.aieat.netswissknife.app.ui.screens.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SettingsViewModel")
class SettingsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefsFlow: MutableStateFlow<Preferences>
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        prefsFlow = MutableStateFlow(emptyPreferences())
        dataStore = mockk {
            every { data } answers { prefsFlow }
            coEvery { updateData(any()) } coAnswers {
                val transform = firstArg<suspend (Preferences) -> Preferences>()
                val updated = transform(prefsFlow.value)
                prefsFlow.value = updated
                updated
            }
        }
        viewModel = SettingsViewModel(dataStore)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("defaults")
    inner class Defaults {

        @Test
        fun `themeOverride defaults to SYSTEM`() {
            assertEquals("SYSTEM", viewModel.themeOverride.value)
        }

        @Test
        fun `dynamicColor defaults to true`() {
            assertEquals(true, viewModel.dynamicColor.value)
        }

        @Test
        fun `defaultPingCount defaults to 10`() {
            assertEquals(10, viewModel.defaultPingCount.value)
        }

        @Test
        fun `defaultTimeoutMs defaults to 2000`() {
            assertEquals(2000, viewModel.defaultTimeoutMs.value)
        }

        @Test
        fun `defaultConcurrency defaults to 50`() {
            assertEquals(50, viewModel.defaultConcurrency.value)
        }
    }

    @Nested
    @DisplayName("setters")
    inner class Setters {

        @Test
        fun `setThemeOverride writes value`() = runTest {
            viewModel.setThemeOverride("DARK")
            assertEquals("DARK", prefsFlow.value[AppPreferenceKeys.THEME_OVERRIDE])
        }

        @Test
        fun `setDynamicColor writes value`() = runTest {
            viewModel.setDynamicColor(false)
            assertEquals(false, prefsFlow.value[AppPreferenceKeys.DYNAMIC_COLOR])
        }

        @Test
        fun `setDefaultPingCount coerces into 1 to 100`() = runTest {
            viewModel.setDefaultPingCount(500)
            assertEquals(100, prefsFlow.value[AppPreferenceKeys.DEFAULT_PING_COUNT])

            viewModel.setDefaultPingCount(-5)
            assertEquals(1, prefsFlow.value[AppPreferenceKeys.DEFAULT_PING_COUNT])

            viewModel.setDefaultPingCount(25)
            assertEquals(25, prefsFlow.value[AppPreferenceKeys.DEFAULT_PING_COUNT])
        }

        @Test
        fun `setDefaultTimeoutMs coerces into 100 to 30000`() = runTest {
            viewModel.setDefaultTimeoutMs(999_999)
            assertEquals(30_000, prefsFlow.value[AppPreferenceKeys.DEFAULT_TIMEOUT_MS])

            viewModel.setDefaultTimeoutMs(1)
            assertEquals(100, prefsFlow.value[AppPreferenceKeys.DEFAULT_TIMEOUT_MS])
        }

        @Test
        fun `setDefaultConcurrency coerces into 1 to 500`() = runTest {
            viewModel.setDefaultConcurrency(999)
            assertEquals(500, prefsFlow.value[AppPreferenceKeys.DEFAULT_CONCURRENCY])

            viewModel.setDefaultConcurrency(0)
            assertEquals(1, prefsFlow.value[AppPreferenceKeys.DEFAULT_CONCURRENCY])
        }

        @Test
        fun `resetOnboarding clears ONBOARDING_COMPLETED`() = runTest {
            viewModel.resetOnboarding()
            assertEquals(false, prefsFlow.value[AppPreferenceKeys.ONBOARDING_COMPLETED])
        }

        @Test
        fun `clearAllRecentHosts removes all recent-host keys`() = runTest {
            val seeded = mutablePreferencesOf()
            AppPreferenceKeys.ALL_RECENT_HOST_KEYS.forEach { key -> seeded[key] = "seed" }
            prefsFlow.value = seeded

            viewModel.clearAllRecentHosts()

            AppPreferenceKeys.ALL_RECENT_HOST_KEYS.forEach { key ->
                assertFalse(prefsFlow.value.contains(key), "$key should have been cleared")
            }
        }
    }
}
