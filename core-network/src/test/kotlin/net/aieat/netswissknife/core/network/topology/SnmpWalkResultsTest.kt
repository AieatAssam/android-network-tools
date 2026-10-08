package net.aieat.netswissknife.core.network.topology

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.VariableBinding
import org.snmp4j.util.TreeEvent

class SnmpWalkResultsTest {
    @Test
    fun `walk listener stops on the first row beyond its entry budget`() {
        val budget = SnmpWalkBudget(
            TopologyResourceLimits(maxEntriesPerWalk = 2, maxBytesPerWalk = 1_000)
        )
        val collector = BoundedSnmpWalkCollector(budget)

        assertFalse(collector.next(event(
            binding("1.3.6.1.2.1.1", "one"),
            binding("1.3.6.1.2.1.2", "two"),
            binding("1.3.6.1.2.1.3", "three")
        )))

        val result = collector.result()
        assertEquals(2, result.entries.size)
        assertEquals(setOf(TopologyTruncationReason.WALK_ENTRY_LIMIT), result.truncationReasons)
    }

    @Test
    fun `walk listener bounds sparse multi-page responses and retains earlier pages`() {
        val budget = SnmpWalkBudget(
            TopologyResourceLimits(maxPagesPerWalk = 2, maxEntriesPerWalk = 100)
        )
        val collector = BoundedSnmpWalkCollector(budget)

        assertTrue(collector.next(event(binding("1.3.6.1.2.1.1", "one"))))
        assertTrue(collector.next(event(binding("1.3.6.1.2.1.2", "two"))))
        assertFalse(collector.next(event(binding("1.3.6.1.2.1.3", "three"))))

        assertEquals(
            mapOf("1.3.6.1.2.1.1" to "one", "1.3.6.1.2.1.2" to "two"),
            collector.result().entries,
        )
        assertEquals(setOf(TopologyTruncationReason.WALK_PAGE_LIMIT), collector.result().truncationReasons)
    }

    @Test
    fun `walk listener accepts data exactly at the cap without claiming truncation`() {
        val budget = SnmpWalkBudget(
            TopologyResourceLimits(maxEntriesPerWalk = 2, maxBytesPerWalk = 1_000)
        )
        val collector = BoundedSnmpWalkCollector(budget)

        assertTrue(collector.next(event(
            binding("1.3.6.1.2.1.1", "one"),
            binding("1.3.6.1.2.1.2", "two")
        )))
        collector.finished(event())

        assertEquals(2, collector.result().entries.size)
        assertTrue(collector.result().truncationReasons.isEmpty())
    }

    @Test
    fun `walk listener applies a shared per-device byte budget across concurrent walks`() {
        val limits = TopologyResourceLimits(
            maxEntriesPerWalk = 10,
            maxBytesPerWalk = 1_000,
            maxBytesPerDevice = 10
        )
        val sharedBudget = SnmpWalkBudget(limits)
        val firstWalk = BoundedSnmpWalkCollector(sharedBudget)
        val secondWalk = BoundedSnmpWalkCollector(sharedBudget)

        assertTrue(firstWalk.next(event(binding("1", "a"))))
        assertFalse(secondWalk.next(event(binding("2", "b"))))
        assertEquals(setOf(TopologyTruncationReason.DEVICE_BYTE_LIMIT), secondWalk.result().truncationReasons)
        assertEquals(0, secondWalk.result().entries.size)
    }

    @Test
    fun `walk listener stops at the shared per-device entry limit`() {
        val budget = SnmpWalkBudget(
            TopologyResourceLimits(
                maxEntriesPerWalk = 3,
                maxEntriesPerDevice = 2,
                maxBytesPerWalk = 1_000,
                maxBytesPerDevice = 1_000
            )
        )
        val firstWalk = BoundedSnmpWalkCollector(budget)
        assertTrue(firstWalk.next(event(binding("1", "one"), binding("2", "two"))))
        firstWalk.finished(event())
        assertEquals(2, firstWalk.result().entries.size)
        assertTrue(firstWalk.result().truncationReasons.isEmpty())

        val nextWalk = BoundedSnmpWalkCollector(budget)
        assertFalse(nextWalk.next(event(binding("3", "three"))))
        assertEquals(0, nextWalk.result().entries.size)
        assertEquals(setOf(TopologyTruncationReason.DEVICE_ENTRY_LIMIT), nextWalk.result().truncationReasons)
    }

    @Test
    fun `walk byte budget accepts exact cap and rejects the first row over cap`() {
        val exact = BoundedSnmpWalkCollector(
            SnmpWalkBudget(TopologyResourceLimits(maxBytesPerWalk = 6))
        )
        assertTrue(exact.next(event(binding("1", "a"))))
        exact.finished(event())
        assertEquals(1, exact.result().entries.size)
        assertTrue(exact.result().truncationReasons.isEmpty())

        val over = BoundedSnmpWalkCollector(
            SnmpWalkBudget(TopologyResourceLimits(maxBytesPerWalk = 6))
        )
        assertFalse(over.next(event(binding("1", "a"), binding("2", "b"))))
        assertEquals(1, over.result().entries.size)
        assertEquals(setOf(TopologyTruncationReason.WALK_BYTE_LIMIT), over.result().truncationReasons)
    }

    @Test
    fun `walk listener retains rows supplied only on the terminal callback`() {
        val collector = BoundedSnmpWalkCollector(
            SnmpWalkBudget(TopologyResourceLimits(maxEntriesPerWalk = 2)),
            rootOidPrefix = "1.3.6.1.2.1"
        )
        collector.finished(event(
            binding("1.3.6.1.2.1.1", "inside"),
            binding("1.3.6.1.2.2.1", "outside")
        ))

        assertEquals(mapOf("1.3.6.1.2.1.1" to "inside"), collector.result().entries)
        assertTrue(collector.result().truncationReasons.isEmpty())
    }

    @Test
    fun `walk terminal error remains visible with earlier rows`() {
        val collector = BoundedSnmpWalkCollector(SnmpWalkBudget(TopologyResourceLimits()))
        collector.next(event(binding("1.3.6.1.2.1.1", "one")))
        val errorEvent = mockk<TreeEvent> {
            every { isError } returns true
            every { variableBindings } returns null
        }
        collector.finished(errorEvent)

        assertEquals(mapOf("1.3.6.1.2.1.1" to "one"), collector.result().entries)
        assertTrue(collector.result().truncationReasons.isEmpty())
        assertTrue(collector.result().hadError)
    }

    @Test
    fun `resource limits reject an unbounded GETBULK page size`() {
        assertThrows(IllegalArgumentException::class.java) {
            TopologyResourceLimits(maxRepetitions = 26)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TopologyResourceLimits(maxPagesPerWalk = 65)
        }
    }

    @Test
    fun `walk listener rejects oversized values without retaining them`() {
        val budget = SnmpWalkBudget(
            TopologyResourceLimits(maxValueChars = 4, maxBytesPerWalk = 1_000)
        )
        val collector = BoundedSnmpWalkCollector(budget)

        assertFalse(collector.next(event(binding("1.3.6.1", "oversized"))))

        assertTrue(collector.result().entries.isEmpty())
        assertEquals(setOf(TopologyTruncationReason.WALK_VALUE_LIMIT), collector.result().truncationReasons)
    }

    private fun event(vararg bindings: VariableBinding): TreeEvent = mockk {
        every { isError } returns false
        every { variableBindings } returns bindings
    }

    private fun binding(oid: String, value: String) = VariableBinding(OID(oid), OctetString(value))
}
