package net.aieat.netswissknife.app.crash

import android.content.Context
import android.content.Intent
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val onBeforeHandle: ((thread: Thread, throwable: Throwable) -> Unit)? = null,
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        onBeforeHandle?.invoke(thread, throwable)
        try {
            val stackTrace = StringWriter().also {
                throwable.printStackTrace(PrintWriter(it))
            }.toString()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                .format(Date())

            val intent = Intent(context, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(CrashActivity.EXTRA_STACK_TRACE, stackTrace)
                putExtra(CrashActivity.EXTRA_EXCEPTION_CLASS, throwable.javaClass.name)
                putExtra(CrashActivity.EXTRA_EXCEPTION_MESSAGE, throwable.message ?: "No message")
                putExtra(CrashActivity.EXTRA_THREAD_NAME, thread.name)
                putExtra(CrashActivity.EXTRA_TIMESTAMP, timestamp)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Never block crash reporting – fall through to default handler
        }

        // Always invoke the default handler so the system records the crash
        defaultHandler?.uncaughtException(thread, throwable)
    }
}
