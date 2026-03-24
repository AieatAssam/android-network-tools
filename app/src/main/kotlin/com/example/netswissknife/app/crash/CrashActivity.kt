package com.example.netswissknife.app.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.netswissknife.app.MainActivity
import com.example.netswissknife.app.R
import com.example.netswissknife.app.ui.theme.NetSwissKnifeTheme
import kotlinx.coroutines.launch

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: "No stack trace"
        val exceptionClass = intent.getStringExtra(EXTRA_EXCEPTION_CLASS) ?: "UnknownException"
        val exceptionMessage = intent.getStringExtra(EXTRA_EXCEPTION_MESSAGE) ?: ""
        val threadName = intent.getStringExtra(EXTRA_THREAD_NAME) ?: "unknown"
        val timestamp = intent.getStringExtra(EXTRA_TIMESTAMP) ?: ""

        setContent {
            NetSwissKnifeTheme {
                CrashScreen(
                    exceptionClass = exceptionClass,
                    exceptionMessage = exceptionMessage,
                    threadName = threadName,
                    timestamp = timestamp,
                    stackTrace = stackTrace,
                    onRestart = ::restartApp,
                    onCopy = { copyToClipboard(buildFullReport(exceptionClass, exceptionMessage, threadName, timestamp, stackTrace)) },
                    onShare = { shareReport(buildFullReport(exceptionClass, exceptionMessage, threadName, timestamp, stackTrace)) },
                )
            }
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Crash Report", text))
    }

    private fun shareReport(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crash_share_subject))
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.crash_share_title)))
    }

    private fun buildFullReport(
        exceptionClass: String,
        exceptionMessage: String,
        threadName: String,
        timestamp: String,
        stackTrace: String,
    ): String = buildString {
        appendLine("=== Net Swiss Knife Crash Report ===")
        appendLine("Time:      $timestamp")
        appendLine("Thread:    $threadName")
        appendLine("Exception: $exceptionClass")
        appendLine("Message:   $exceptionMessage")
        appendLine()
        appendLine("--- Stack Trace ---")
        appendLine(stackTrace)
    }

    companion object {
        const val EXTRA_STACK_TRACE = "extra_stack_trace"
        const val EXTRA_EXCEPTION_CLASS = "extra_exception_class"
        const val EXTRA_EXCEPTION_MESSAGE = "extra_exception_message"
        const val EXTRA_THREAD_NAME = "extra_thread_name"
        const val EXTRA_TIMESTAMP = "extra_timestamp"
    }
}

@Composable
private fun CrashScreen(
    exceptionClass: String,
    exceptionMessage: String,
    threadName: String,
    timestamp: String,
    stackTrace: String,
    onRestart: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.crash_copied)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Hero icon with gradient background
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                Text(
                    text = stringResource(R.string.crash_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(R.string.crash_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // Summary card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.crash_summary_header),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        CrashDetailRow(label = stringResource(R.string.crash_label_time), value = timestamp)
                        CrashDetailRow(label = stringResource(R.string.crash_label_thread), value = threadName)
                        CrashDetailRow(label = stringResource(R.string.crash_label_exception), value = exceptionClass.substringAfterLast('.'))
                        if (exceptionMessage.isNotBlank()) {
                            CrashDetailRow(label = stringResource(R.string.crash_label_message), value = exceptionMessage)
                        }
                    }
                }

                // Stack trace card
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.crash_stack_trace_header),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stackTrace,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        )
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            onCopy()
                            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.crash_copy))
                    }
                    OutlinedButton(
                        onClick = onShare,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(R.string.crash_share))
                    }
                }

                FilledTonalButton(
                    onClick = onRestart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.crash_restart))
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun CrashDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.65f),
        )
    }
}
