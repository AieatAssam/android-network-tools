package net.aieat.netswissknife.app.crash

import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler internal constructor(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val onBeforeHandle: ((thread: Thread, throwable: Throwable) -> Unit)? = null,
    private val buildReport: (Thread, Throwable, String) -> CrashReport = CrashReportBuilder::build,
    private val startCrashActivity: ((CrashReport) -> Unit)? = null,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            try {
                onBeforeHandle?.invoke(thread, throwable)
            } catch (_: Throwable) {
                // A diagnostic hook must never prevent system crash delivery.
            }

            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
                val report = buildReport(thread, throwable, timestamp)
                val launch = startCrashActivity ?: { safeReport ->
                    val intent = Intent(context, CrashActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        putExtra(CrashActivity.EXTRA_STACK_TRACE, safeReport.stackTrace)
                        putExtra(CrashActivity.EXTRA_EXCEPTION_CLASS, safeReport.exceptionClass)
                        putExtra(CrashActivity.EXTRA_EXCEPTION_MESSAGE, safeReport.exceptionMessage)
                        putExtra(CrashActivity.EXTRA_THREAD_NAME, safeReport.threadName)
                        putExtra(CrashActivity.EXTRA_TIMESTAMP, safeReport.timestamp)
                    }
                    context.startActivity(intent)
                }
                launch(report)
            } catch (_: Throwable) {
                // Report construction or UI launch is best-effort only.
            }
        } finally {
            // One call site in finally guarantees one delegation even if any hook above fails.
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
