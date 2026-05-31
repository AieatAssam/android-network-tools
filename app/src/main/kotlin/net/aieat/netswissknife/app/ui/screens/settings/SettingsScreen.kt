package net.aieat.netswissknife.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import net.aieat.netswissknife.app.BuildConfig
import net.aieat.netswissknife.app.R
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeOverride by viewModel.themeOverride.collectAsState()
    val defaultPingCount by viewModel.defaultPingCount.collectAsState()
    val defaultTimeoutMs by viewModel.defaultTimeoutMs.collectAsState()
    val defaultConcurrency by viewModel.defaultConcurrency.collectAsState()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsHeader()
            ThemeSection(
                themeOverride = themeOverride,
                onThemeChange = viewModel::setThemeOverride
            )
            DefaultsSection(
                pingCount = defaultPingCount,
                timeoutMs = defaultTimeoutMs,
                concurrency = defaultConcurrency,
                onPingCountChange = viewModel::setDefaultPingCount,
                onTimeoutChange = viewModel::setDefaultTimeoutMs,
                onConcurrencyChange = viewModel::setDefaultConcurrency
            )
            DataSection(onClearRecents = viewModel::clearAllRecentHosts)
            AboutSection()
            LicensesSection()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SettingsHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = stringResource(R.string.settings_screen_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = stringResource(R.string.settings_screen_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSection(
    themeOverride: String,
    onThemeChange: (String) -> Unit
) {
    SectionHeader(Icons.Default.DarkMode, stringResource(R.string.settings_theme_section))

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_theme_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val options = listOf("SYSTEM", "LIGHT", "DARK")
            val labels = listOf(
                stringResource(R.string.settings_theme_system),
                stringResource(R.string.settings_theme_light),
                stringResource(R.string.settings_theme_dark)
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = themeOverride == option,
                        onClick = { onThemeChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(labels[index])
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultsSection(
    pingCount: Int,
    timeoutMs: Int,
    concurrency: Int,
    onPingCountChange: (Int) -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onConcurrencyChange: (Int) -> Unit
) {
    SectionHeader(Icons.Default.Tune, stringResource(R.string.settings_defaults_section))

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SliderSetting(
                label = stringResource(R.string.settings_ping_count_label, pingCount),
                value = pingCount.toFloat(),
                valueRange = 1f..100f,
                steps = 98,
                onValueChange = { onPingCountChange(it.roundToInt()) }
            )
            SliderSetting(
                label = stringResource(R.string.settings_timeout_label, timeoutMs),
                value = timeoutMs.toFloat(),
                valueRange = 500f..10_000f,
                steps = 18,
                onValueChange = { onTimeoutChange((it / 500).roundToInt() * 500) }
            )
            SliderSetting(
                label = stringResource(R.string.settings_concurrency_label, concurrency),
                value = concurrency.toFloat(),
                valueRange = 10f..500f,
                steps = 48,
                onValueChange = { onConcurrencyChange((it / 10).roundToInt() * 10) }
            )
        }
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun DataSection(onClearRecents: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }

    SectionHeader(Icons.Default.Delete, stringResource(R.string.settings_data_section))

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.settings_clear_recents_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_clear_recents_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { showConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.settings_clear_recents_button))
            }
        }
    }

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.settings_clear_recents_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_recents_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearRecents()
                        showConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.settings_clear_recents_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current

    SectionHeader(Icons.Default.Info, stringResource(R.string.settings_about_section))

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_version_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { uriHandler.openUri("https://github.com/AieatAssam/android-network-tools") }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.settings_source_code_label),
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_source_code_url),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private data class LibraryInfo(
    val name: String,
    val version: String,
    val license: String,
    val copyright: String? = null
)

private val THIRD_PARTY_LIBRARIES = listOf(
    LibraryInfo("dnsjava", "3.6.2", "BSD 3-Clause",
        "Copyright (c) 1998-2024, Brian Wellington and the dnsjava contributors"),
    LibraryInfo("MapLibre Compose", "0.12.1", "BSD 3-Clause",
        "Copyright (c) 2021-2024, MapLibre contributors"),
    LibraryInfo("SNMP4J", "3.8.0", "Apache 2.0"),
    LibraryInfo("icmpenguin", "1.0.0-rc.3", "Apache 2.0"),
    LibraryInfo("Dagger Hilt", "2.59.2", "Apache 2.0"),
    LibraryInfo("Kotlin Coroutines", "1.9.0", "Apache 2.0"),
    LibraryInfo("AndroidX / Jetpack Compose", "—", "Apache 2.0"),
    LibraryInfo("AndroidX DataStore", "1.1.4", "Apache 2.0"),
)

@Composable
private fun LicensesSection() {
    SectionHeader(Icons.Default.Info, stringResource(R.string.settings_licenses_section))

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.settings_licenses_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
            THIRD_PARTY_LIBRARIES.forEachIndexed { index, lib ->
                LibraryRow(lib)
                if (index < THIRD_PARTY_LIBRARIES.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(lib: LibraryInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lib.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = lib.license,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        if (lib.version != "—") {
            Text(
                text = "v${lib.version}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (lib.copyright != null) {
            Text(
                text = lib.copyright,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
