package net.aieat.netswissknife.app.util

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import net.aieat.netswissknife.app.R
import java.io.File

fun Context.shareText(text: String, subject: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, boundShareText(text))
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(Intent.createChooser(intent, null))
}

/** Keep text shares below Binder's transaction limit, which includes parcel overhead. */
internal const val MAX_SHARE_TEXT_CHARS = 50 * 1024
private const val SHARE_TRUNCATION_NOTE = "\n\n[Report truncated to fit in a share message.]"

internal fun boundShareText(text: String): String {
    if (text.length <= MAX_SHARE_TEXT_CHARS) return text

    var end = MAX_SHARE_TEXT_CHARS
    // Avoid cutting a UTF-16 surrogate pair in half.
    if (end > 0 && Character.isHighSurrogate(text[end - 1]) && Character.isLowSurrogate(text[end])) end--
    return text.substring(0, end) + SHARE_TRUNCATION_NOTE
}

internal fun Context.createCsvShareIntent(file: File, subject: String, summary: String): Intent {
    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
    return Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, boundShareText(summary))
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(contentResolver, file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

fun Context.shareCsvFile(file: File, subject: String, summary: String) {
    try {
        startActivity(Intent.createChooser(createCsvShareIntent(file, subject, summary), null))
    } catch (_: Exception) {
        Toast.makeText(this, R.string.share_file_unavailable, Toast.LENGTH_SHORT).show()
    }
}
