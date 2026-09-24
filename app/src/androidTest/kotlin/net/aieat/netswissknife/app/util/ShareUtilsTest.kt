package net.aieat.netswissknife.app.util

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareUtilsTest {
    @Test
    fun largeCsvShare_usesReadableContentUriAndKeepsIntentTextShort() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val logFile = File.createTempFile("large_ping_session_", ".csv", context.cacheDir)

        try {
            logFile.outputStream().buffered().use { output ->
                val row = "1234567890,192.0.2.1,success,12ms\n".toByteArray()
                repeat((2 * 1024 * 1024) / row.size) { output.write(row) }
            }

            val intent = context.createCsvShareIntent(
                file = logFile,
                subject = "Ping results",
                summary = "Continuous ping CSV for example.com: 50000 packets.",
            )
            val uri = intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

            assertEquals(Intent.ACTION_SEND, intent.action)
            assertEquals("text/csv", intent.type)
            assertEquals("Ping results", intent.getStringExtra(Intent.EXTRA_SUBJECT))
            assertTrue(requireNotNull(intent.getStringExtra(Intent.EXTRA_TEXT)).length < 200)
            assertNotNull(uri)
            assertEquals("content", uri!!.scheme)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)

            val streamedBytes = requireNotNull(context.contentResolver.openInputStream(uri)).use { input ->
                val buffer = ByteArray(16 * 1024)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                }
                total
            }
            assertTrue("Expected a multi-megabyte CSV to remain readable", streamedBytes > 1_000_000)
        } finally {
            logFile.delete()
        }
    }
}
