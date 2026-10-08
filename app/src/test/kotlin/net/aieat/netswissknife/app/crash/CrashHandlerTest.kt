package net.aieat.netswissknife.app.crash

import android.content.Context
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CrashHandlerTest {
    @Test
    fun delegatesToDefaultExactlyOnceWhenBeforeHookThrows() {
        val calls = countingDefaultHandler()
        var launched = false
        val handler = CrashHandler(
            context = mockk<Context>(relaxed = true),
            defaultHandler = calls.first,
            onBeforeHandle = { _, _ -> error("hook failed") },
            startCrashActivity = { launched = true },
        )

        handler.uncaughtException(Thread("worker"), IllegalStateException("boom"))

        assertEquals(1, calls.second())
        assertEquals(true, launched)
    }

    @Test
    fun delegatesToDefaultExactlyOnceWhenReportBuilderThrows() {
        val calls = countingDefaultHandler()
        val handler = CrashHandler(
            context = mockk<Context>(relaxed = true),
            defaultHandler = calls.first,
            buildReport = { _, _, _ -> error("builder failed") },
            startCrashActivity = { error("must not launch after builder failure") },
        )

        handler.uncaughtException(Thread("worker"), IllegalStateException("boom"))

        assertEquals(1, calls.second())
    }

    @Test
    fun delegatesToDefaultExactlyOnceWhenActivityLaunchThrows() {
        val calls = countingDefaultHandler()
        val handler = CrashHandler(
            context = mockk<Context>(relaxed = true),
            defaultHandler = calls.first,
            startCrashActivity = { error("activity launch failed") },
        )

        handler.uncaughtException(Thread("worker"), IllegalStateException("boom"))

        assertEquals(1, calls.second())
    }

    @Test
    fun buildsSanitizedReportBeforeLaunchingAndThenDelegatesOnce() {
        val calls = countingDefaultHandler()
        var launched: CrashReport? = null
        val handler = CrashHandler(
            context = mockk<Context>(relaxed = true),
            defaultHandler = calls.first,
            startCrashActivity = { launched = it },
        )

        handler.uncaughtException(
            Thread("worker"),
            IllegalArgumentException("password=secret 192.168.1.5"),
        )

        val launchedReport = requireNotNull(launched)
        assertEquals(1, calls.second())
        assertEquals(false, launchedReport.exceptionMessage.contains("secret"))
        assertEquals(false, launchedReport.exceptionMessage.contains("192.168.1.5"))
    }

    private fun countingDefaultHandler(): Pair<Thread.UncaughtExceptionHandler, () -> Int> {
        var calls = 0
        return Thread.UncaughtExceptionHandler { _, _ -> calls++ } to { calls }
    }
}
