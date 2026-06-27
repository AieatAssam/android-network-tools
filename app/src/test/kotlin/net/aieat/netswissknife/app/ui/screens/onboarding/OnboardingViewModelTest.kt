package net.aieat.netswissknife.app.ui.screens.onboarding

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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("OnboardingViewModel – first-run onboarding logic")
class OnboardingViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var viewModel: OnboardingViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        testScope = TestScope(testDispatcher + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "onboarding_test_prefs.preferences_pb") }
        )
        viewModel = OnboardingViewModel(dataStore)
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("shouldShowOnboarding")
    inner class ShouldShowOnboarding {

        @Test
        fun `emits true on first run when no preference is stored`() = testScope.runTest {
            advanceUntilIdle()
            assertTrue(viewModel.shouldShowOnboarding.value)
        }

        @Test
        fun `emits false when onboarding was already completed`() = testScope.runTest {
            dataStore.edit { it[AppPreferenceKeys.ONBOARDING_COMPLETED] = true }
            val vm = OnboardingViewModel(dataStore)
            advanceUntilIdle()
            assertFalse(vm.shouldShowOnboarding.value)
        }
    }

    @Nested
    @DisplayName("completeOnboarding")
    inner class CompleteOnboarding {

        @Test
        fun `sets shouldShowOnboarding to false`() = testScope.runTest {
            advanceUntilIdle()
            assertTrue(viewModel.shouldShowOnboarding.value)

            viewModel.completeOnboarding()
            advanceUntilIdle()

            assertFalse(viewModel.shouldShowOnboarding.value)
        }

        @Test
        fun `persists completion to DataStore`() = testScope.runTest {
            advanceUntilIdle()
            viewModel.completeOnboarding()
            advanceUntilIdle()

            val stored = dataStore.data.first()[AppPreferenceKeys.ONBOARDING_COMPLETED]
            assertTrue(stored == true)
        }

        @Test
        fun `survives ViewModel recreation`() = testScope.runTest {
            advanceUntilIdle()
            viewModel.completeOnboarding()
            advanceUntilIdle()

            val newVm = OnboardingViewModel(dataStore)
            advanceUntilIdle()
            assertFalse(newVm.shouldShowOnboarding.value)
        }
    }
}
