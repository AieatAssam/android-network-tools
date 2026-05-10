package net.aieat.netswissknife.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
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
@DisplayName("RecentHostsRepository")
class RecentHostsRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: RecentHostsRepository

    @BeforeEach
    fun setUp() {
        testScope = TestScope(testDispatcher + Job())
        dataStore = PreferenceDataStoreFactory.create(
            scope = testScope,
            produceFile = { File(tempDir, "test_prefs.preferences_pb") }
        )
        repository = RecentHostsRepository(dataStore)
    }

    @AfterEach
    fun tearDown() {
        testScope.cancel()
    }

    @Nested
    @DisplayName("getRecents")
    inner class GetRecents {

        @Test
        fun `returns empty list when no entries stored`() = testScope.runTest {
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(emptyList<String>(), recents)
        }

        @Test
        fun `returns stored entries in order`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host2")
            advanceUntilIdle()
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            // most recent first
            assertEquals(listOf("host2", "host1"), recents)
        }
    }

    @Nested
    @DisplayName("addRecent")
    inner class AddRecent {

        @Test
        fun `adds first entry`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(listOf("host1"), recents)
        }

        @Test
        fun `prepends new entry to front of list`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host2")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(listOf("host2", "host1"), recents)
        }

        @Test
        fun `deduplicates and moves duplicate to front`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host2")
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(listOf("host1", "host2"), recents)
        }

        @Test
        fun `trims to maximum of 5 entries`() = testScope.runTest {
            for (i in 1..6) {
                repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host$i")
            }
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(5, recents.size)
            assertEquals("host6", recents.first())
            assertFalse(recents.contains("host1"), "oldest entry should be dropped")
        }

        @Test
        fun `ignores blank entries`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "  ")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertTrue(recents.isEmpty())
        }

        @Test
        fun `trims whitespace from entry`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "  example.com  ")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(listOf("example.com"), recents)
        }

        @Test
        fun `different keys are independent`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "ping-host")
            repository.addRecent(AppPreferenceKeys.RECENT_DNS_HOSTS, "dns-host")
            val pingRecents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            val dnsRecents = repository.getRecents(AppPreferenceKeys.RECENT_DNS_HOSTS).first()
            assertEquals(listOf("ping-host"), pingRecents)
            assertEquals(listOf("dns-host"), dnsRecents)
        }
    }

    @Nested
    @DisplayName("removeRecent")
    inner class RemoveRecent {

        @Test
        fun `removes specific entry from list`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host2")
            repository.removeRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(listOf("host2"), recents)
        }

        @Test
        fun `removing non-existent entry is a no-op`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.removeRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "nonexistent")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertEquals(listOf("host1"), recents)
        }

        @Test
        fun `removing last entry clears the key`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.removeRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertTrue(recents.isEmpty())
        }
    }

    @Nested
    @DisplayName("clearAll")
    inner class ClearAll {

        @Test
        fun `clears all entries for a key`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host1")
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "host2")
            repository.clearAll(AppPreferenceKeys.RECENT_PING_HOSTS)
            val recents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            assertTrue(recents.isEmpty())
        }

        @Test
        fun `clearing one key does not affect other keys`() = testScope.runTest {
            repository.addRecent(AppPreferenceKeys.RECENT_PING_HOSTS, "ping-host")
            repository.addRecent(AppPreferenceKeys.RECENT_DNS_HOSTS, "dns-host")
            repository.clearAll(AppPreferenceKeys.RECENT_PING_HOSTS)
            val pingRecents = repository.getRecents(AppPreferenceKeys.RECENT_PING_HOSTS).first()
            val dnsRecents = repository.getRecents(AppPreferenceKeys.RECENT_DNS_HOSTS).first()
            assertTrue(pingRecents.isEmpty())
            assertEquals(listOf("dns-host"), dnsRecents)
        }
    }
}
