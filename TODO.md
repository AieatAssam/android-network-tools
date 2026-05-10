# Net Swiss Knife – Implementation TODO

Items are ordered by logical dependency: infrastructure first, then features that build on it,
then independent polish. Each item is self-contained and can be implemented, tested, and PR'd
separately.

---

## ~~1. DataStore infrastructure~~ ✓ done (bb44d1d)

**Why first:** Items 2, 8, and 9 all need persistent key-value storage. Establishing a single
shared DataStore module first avoids duplication and ensures consistent serialization.

**What to do:**

1. Add the DataStore Preferences dependency to `gradle/libs.versions.toml` and
   `app/build.gradle.kts`:
   ```
   androidx.datastore:datastore-preferences:1.1.x
   ```

2. Create `app/src/main/kotlin/.../app/data/AppPreferencesDataStore.kt`:
   - Provide a single `DataStore<Preferences>` instance via a `Context.appPreferences`
     property-delegate (the standard `preferencesDataStore(name = "app_prefs")` pattern).
   - Define all `Preferences.Key<*>` constants in one companion object so keys are
     never duplicated across features.

3. Create a Hilt module `app/src/main/kotlin/.../app/di/DataStoreModule.kt` that binds the
   `DataStore<Preferences>` singleton into the Hilt graph, injecting `@ApplicationContext`.

4. Write unit tests for the key definitions (verify key names match expected strings so a
   future rename doesn't silently corrupt stored data).

**Files to create/touch:**
- `gradle/libs.versions.toml`
- `app/build.gradle.kts`
- `app/.../app/data/AppPreferencesDataStore.kt` (new)
- `app/.../app/di/DataStoreModule.kt` (new)

---

## ~~2. Persist pinned navigation routes~~ ✓ done (e6192d6)

**Depends on:** Item 1 (DataStore)

**Problem:** `AppNavigationViewModel` stores pinned routes in a plain in-memory
`MutableStateFlow` (see `AppNavigationViewModel.kt:13`). The user's customisations are
discarded on every process death or app restart, silently reverting to the hard-coded
default `["ping", "dns", "ports"]`.

**What to do:**

1. Add a `PINNED_ROUTES_KEY = stringSetPreferencesKey("pinned_routes")` constant to the
   keys object created in item 1.

2. Rewrite `AppNavigationViewModel` to:
   - Inject the `DataStore<Preferences>` via constructor.
   - Expose `pinnedRoutes` as a `StateFlow` derived from `dataStore.data.map { ... }`.
   - Replace `togglePin` with a `suspend` write to DataStore inside `viewModelScope.launch`.
   - Remove the `MutableStateFlow` and `NavRoutes.defaultPinnedRoutes` fallback (put the
     default inside the DataStore read: `it[PINNED_ROUTES_KEY] ?: setOf("ping","dns","ports")`).

3. Write unit tests for `AppNavigationViewModel` using `TestCoroutineScope` and an
   in-memory fake `DataStore<Preferences>` (use `PreferenceDataStoreFactory.create` with a
   `testCoroutineScope` and a temp file, or provide a `FakeDataStore` wrapper).
   Test: default routes load on first launch, toggling persists, toggling a pinned route
   removes it, unpinned route is added up to MAX_PINNED, exceeding MAX_PINNED is ignored.

**Files to touch:**
- `app/.../app/ui/navigation/AppNavigationViewModel.kt`
- `app/.../app/ui/navigation/AppNavigationViewModelTest.kt` (new)

---

## ~~3. Gate Debug Logs behind BuildConfig.DEBUG~~ ✓ done (2c99564)

**Depends on:** nothing

**Problem:** The "Debug Logs" entry is visible to all users in the "More" sheet (styled with
`errorContainer` colours). It is developer tooling that should not be accessible in release
builds. Touch points:
- `MoreToolsSheet.kt:48` — `onDebugLogsClick` parameter and the rendered row
- `MainActivity.kt:84-89` — wires `onDebugLogsClick` to navigate to the debug route
- `AppNavigation.kt:100` — registers the route unconditionally

**What to do:**

1. Enable `BuildConfig` generation in `app/build.gradle.kts`:
   ```kotlin
   buildFeatures {
       compose = true
       buildConfig = true   // add this
   }
   ```

2. In `MoreToolsSheet.kt`, wrap the entire debug row (the `Spacer`, `HorizontalDivider`,
   and `Row` block from line ~105 to ~142) in:
   ```kotlin
   if (BuildConfig.DEBUG) { ... }
   ```

3. In `MainActivity.kt`, wrap the `onDebugLogsClick` lambda body in `if (BuildConfig.DEBUG)`.

4. In `AppNavigation.kt`, wrap `composable(NavRoutes.DebugLogs.route) { DebugLogScreen() }`
   in `if (BuildConfig.DEBUG)`.

5. `NavRoutes.DebugLogs` object can stay — it is harmless and used only from the above
   guarded call sites. Do not remove it; it may be useful in future tooling.

**Testing:** Build a release APK locally (`./gradlew :app:assembleRelease`) and confirm the
Debug Logs entry is absent. Build a debug APK and confirm it is present.

**Files to touch:**
- `app/build.gradle.kts`
- `app/.../app/ui/navigation/MoreToolsSheet.kt`
- `app/.../app/MainActivity.kt`
- `app/.../app/ui/navigation/AppNavigation.kt`

---

## 4. Network security config (cleartext traffic)

**Depends on:** nothing

**Problem:** `AndroidManifest.xml` has no `android:networkSecurityConfig` attribute and no
`android:usesCleartextTraffic` setting. Android 9+ blocks cleartext by default for most
cases, but the HTTP Probe tool intentionally makes cleartext HTTP requests (the whole point
is to probe arbitrary URLs). Without an explicit security config, the behaviour is
undocumented and varies with API level.

**What to do:**

1. Create `app/src/main/res/xml/network_security_config.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <network-security-config>
       <base-config cleartextTrafficPermitted="false">
           <trust-anchors>
               <certificates src="system" />
           </trust-anchors>
       </base-config>
       <!-- HTTP Probe explicitly allows cleartext to any host the user enters -->
       <domain-config cleartextTrafficPermitted="true">
           <domain includeSubdomains="true">*</domain>
       </domain-config>
   </network-security-config>
   ```
   Note: a wildcard `<domain>` entry is not valid in production security configs. The
   correct approach is to set `cleartextTrafficPermitted="true"` only on the
   `<base-config>` specifically for the HTTP Probe use case, or (better) document clearly
   that cleartext is permitted globally so HTTP Probe works, and the user is aware they
   are sending unencrypted probes. If stricter isolation is desired later, HTTP Probe
   could use a separate `OkHttpClient` that bypasses the config — but do not over-engineer
   this now.

   Simplest correct config that documents intent:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <network-security-config>
       <!--
         Cleartext permitted globally: HTTP Probe intentionally sends cleartext requests
         to user-supplied URLs. All other tools use TLS or raw sockets that are
         unaffected by this setting.
       -->
       <base-config cleartextTrafficPermitted="true">
           <trust-anchors>
               <certificates src="system" />
           </trust-anchors>
       </base-config>
       <debug-overrides>
           <trust-anchors>
               <certificates src="system" />
               <certificates src="user" />
           </trust-anchors>
       </debug-overrides>
   </network-security-config>
   ```
   The `<debug-overrides>` block allows user-installed CA certificates in debug builds,
   which is useful for testing with a proxy (e.g. Charles, mitmproxy) without affecting
   release behaviour.

2. Reference the config in `AndroidManifest.xml`:
   ```xml
   <application
       ...
       android:networkSecurityConfig="@xml/network_security_config"
       ...>
   ```

**Testing:** Build and install a debug APK. Use HTTP Probe to hit `http://example.com` (no
HTTPS). Confirm the request succeeds. Confirm `https://` requests still work. Use an
Android 9+ device or emulator.

**Files to create/touch:**
- `app/src/main/res/xml/network_security_config.xml` (new)
- `app/src/main/AndroidManifest.xml`

---

## 5. Fix hardcoded strings (localisation compliance)

**Depends on:** nothing

**Problem:** `WifiScanScreen.kt` and `TopologyDiscoveryScreen.kt` contain hardcoded English
strings directly in composables, violating the CLAUDE.md rule "All strings in `strings.xml`
(no hardcoded strings in composables)". Known violations in `WifiScanScreen.kt`:

| Line | Hardcoded string |
|------|-----------------|
| 215  | `"Grant Permission"` |
| 234  | `"Retry"` |
| 244  | `"Retry"` |
| 375  | `"Wi-Fi Scanner"` |
| 414  | `"All"` |
| 429  | `"Sort:"` |
| 453  | `"Channel Utilisation"` |
| 587  | `"Connected"` |
| 592  | `"Hidden"` |
| 615  | `"·"` (separator — can stay if purely decorative, but prefer a resource) |
| 625  | `"${ap.rssi} dBm"` (unit suffix `" dBm"` should be a format string) |
| 707  | `"${ap.rssi} dBm"` (same) |
| 725  | `"Currently Connected"` |

Also run a full audit:
```bash
grep -rn 'Text("' app/src/main/kotlin/ | grep -v stringResource
```
and fix every hit in composables. Data-only strings (e.g. `ClipData.newPlainText("", ...)`)
are fine and do not need resource entries.

**What to do:**

1. Add string resources to `app/src/main/res/values/strings.xml` for every hardcoded label
   found. Use the existing naming conventions already in the file (screen prefix + descriptive
   suffix, e.g. `wifi_grant_permission`, `wifi_sort_label`, `wifi_band_filter_all`).

2. Replace each hardcoded `Text("…")` with `Text(stringResource(R.string.…))` and each
   hardcoded string argument with `stringResource(R.string.…)`.

3. Format strings with a unit suffix should use `stringResource(R.string.wifi_rssi_dbm, ap.rssi)`
   with `<string name="wifi_rssi_dbm">%1$d dBm</string>`.

**Testing:** `./gradlew :app:assembleDebug` must pass with no lint warnings about hardcoded
strings (`./gradlew :app:lintDebug` and inspect the report). Visually verify the Wi-Fi
Scanner screen still renders correctly.

**Files to touch:**
- `app/src/main/res/values/strings.xml`
- `app/.../app/ui/screens/WifiScanScreen.kt`
- Any other screen files flagged by the grep audit

---

## 6. Fix hardcoded semantic colours (dark-mode correctness)

**Depends on:** nothing (but do after item 5 so string and colour fixes are separate PRs)

**Problem:** Four screens use raw `Color(0xFF…)` hex literals for traffic-light indicators
that convey meaning (latency, signal strength, certificate health). These values do not
adapt to dark mode and are scattered across unrelated files. Specific functions:

| File | Function | Colours used |
|------|----------|-------------|
| `TracerouteScreen.kt:988` | `rttColor()` | grey, green, lime, orange, red |
| `WifiScanScreen.kt:669` | `signalLevelColor()` | green, lime-green, amber, orange, red |
| `LanScanScreen.kt:1182` | `pingColor()` | (same latency bands as rttColor) |
| `TlsInspectorScreen.kt:458` | inline cert validity | green parsed from hex string |
| `WhoisScreen.kt:841` | `expiryColor()` | (expiry date warning colours) |

**What to do:**

1. In `app/.../app/ui/theme/Color.kt`, add semantic colour constants for the five traffic
   levels used across these screens. Use names that describe intent, not value:
   ```kotlin
   // Semantic status colours – used for latency, signal strength, certificate validity
   val StatusGood      = Color(0xFF4CAF50)
   val StatusOk        = Color(0xFF8BC34A)
   val StatusWarn      = Color(0xFFFFC107)
   val StatusBad       = Color(0xFFFF9800)
   val StatusCritical  = Color(0xFFF44336)
   val StatusUnknown   = Color(0xFF9E9E9E)
   ```

2. In `app/.../app/ui/theme/Theme.kt`, expose these as a custom `CompositionLocal` or as
   extension properties on `ColorScheme` so screens access them via `MaterialTheme` rather
   than importing raw constants. The simplest approach without a custom theme extension is to
   import them directly from `Color.kt` — acceptable as a first pass.

3. Replace every `Color(0xFF…)` literal inside `rttColor`, `signalLevelColor`, `pingColor`,
   `expiryColor`, and the inline TLS cert colour with the named constants from step 1.

4. Remove any duplicated colour logic: `rttColor` and `pingColor` implement the same latency
   thresholds. Extract to a single `latencyColor(ms: Long?): Color` function in a shared
   `ui/theme/StatusColors.kt` file and call it from both screens.

**Testing:** Run the app in both light and dark themes (or use the Compose Preview dark mode
toggle). Verify that signal strength badges, latency RTT colours, and certificate validity
indicators render with the correct semantic colour in both modes. `./gradlew :app:assembleDebug`
must pass.

**Files to touch:**
- `app/.../app/ui/theme/Color.kt`
- `app/.../app/ui/theme/StatusColors.kt` (new shared file for `latencyColor`)
- `app/.../app/ui/screens/TracerouteScreen.kt`
- `app/.../app/ui/screens/WifiScanScreen.kt`
- `app/.../app/ui/screens/lan/LanScanScreen.kt`
- `app/.../app/ui/screens/tls/TlsInspectorScreen.kt`
- `app/.../app/ui/screens/whois/WhoisScreen.kt`

---

## 7. Fix accessibility gaps (contentDescription)

**Depends on:** nothing

**Problem:** 108 `contentDescription = null` calls exist across the app. Many are on purely
decorative icons (correct), but meaningful action icons — Clear, Stop, Refresh, Copy, Pin,
Unpin, Expand, Collapse — have null descriptions, making them inaccessible to TalkBack users.

Run this to get the full list:
```bash
grep -rn "contentDescription = null" app/src/main/kotlin/
```

**What to do:**

1. Audit each `contentDescription = null`. Apply this rule:
   - **Decorative icon next to labelled text** (e.g. the hub icon in `HomeScreen` next to
     the app name): `null` is correct — the adjacent text already labels it.
   - **Standalone clickable icon with no visible text label** (e.g. the Clear `X` button,
     the Stop button, the Copy icon, the Refresh icon, Pin/Unpin): **must have a description**.

2. For action icons, add string resources and set `contentDescription`:
   ```kotlin
   Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
   Icon(Icons.Default.Stop,  contentDescription = stringResource(R.string.action_stop))
   Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.action_refresh))
   ```
   Suggested resource names: `action_clear`, `action_stop`, `action_refresh`, `action_copy`,
   `action_share`, `action_expand`, `action_collapse`, `action_pin`, `action_unpin`.

3. Add these to `strings.xml`.

4. Known high-priority gaps (confirm and fix these first):
   - `PingScreen.kt:281` — NetworkCheck icon on start button (has a text label nearby, likely OK)
   - `PingScreen.kt:333` — Stop icon on stop button (no visible text — needs description)
   - `PingScreen.kt:590` — Clear icon (needs description)
   - `PingScreen.kt:633` — Refresh icon (needs description)
   - `MoreToolsSheet.kt` — Pin/Unpin `IconButton` (already has `contentDescription` set
     via string resource — verify it is correct)

**Testing:** Enable TalkBack on a device or emulator. Navigate through Ping, DNS, and LAN
Scanner screens and activate each icon button. Verify TalkBack announces a meaningful label
for every interactive element. `./gradlew :app:lintDebug` should produce zero
"Missing contentDescription" warnings.

**Files to touch:**
- `app/src/main/res/values/strings.xml`
- Every screen file with offending `contentDescription = null` on interactive icons

---

## 8. Settings screen

**Depends on:** Item 1 (DataStore)

**Problem:** There is no Settings screen. Users cannot configure app-wide defaults
(preferred DNS server, default ping count, default timeout), toggle the theme explicitly,
or see app version info. There is also nowhere to clear stored recent hosts (item 9) once
that feature lands.

**What to do:**

1. Add `NavRoutes.Settings` to `NavRoutes.kt`:
   ```kotlin
   object Settings : NavRoutes("settings", "Settings", Icons.Default.Settings)
   ```

2. Register the route in `AppNavigation.kt`.

3. Add a Settings entry to `MoreToolsSheet.kt` above the Debug Logs entry (always visible,
   not behind a `BuildConfig.DEBUG` guard). Use `Icons.Default.Settings` with
   `MaterialTheme.colorScheme.secondaryContainer` background (matching the other tool rows).

4. Create `app/.../app/ui/screens/settings/SettingsScreen.kt` and
   `SettingsViewModel.kt`.

5. Initial settings to expose (all persisted via DataStore with keys defined in item 1):

   | Setting | Key | Type | Default |
   |---------|-----|------|---------|
   | Theme override | `theme_override` | String enum (`SYSTEM`/`LIGHT`/`DARK`) | `SYSTEM` |
   | Default ping count | `default_ping_count` | Int | 10 |
   | Default timeout (ms) | `default_timeout_ms` | Int | 2000 |
   | Default concurrent probes | `default_concurrency` | Int | 50 |
   | Clear all recent hosts | (action, no key) | — | — |

6. The Theme override key must be read in `MainActivity` / `NetSwissKnifeApp` before
   `NetSwissKnifeTheme` is applied. Collect it as a `StateFlow` from a
   `SettingsViewModel` or a dedicated `ThemeViewModel` and pass `darkTheme` to
   `NetSwissKnifeTheme` accordingly.

7. `SettingsScreen` UI:
   - Animated entrance per CLAUDE.md requirements.
   - Section headers using `titleMedium` typography.
   - `ElevatedCard` per settings group.
   - Theme selection using a `SegmentedButton` row (System / Light / Dark).
   - Numeric defaults using `Slider` or `OutlinedTextField` with validation.
   - "Clear all recent hosts" as a destructive action button styled with
     `MaterialTheme.colorScheme.error`, gated behind a `AlertDialog` confirmation.
   - App version info (`BuildConfig.VERSION_NAME`) at the bottom.

8. Wire default values: when `PingViewModel`, `LanScanViewModel`, etc. are constructed,
   read the relevant defaults from DataStore (or from a `SettingsRepository`) and use them
   to initialise their `MutableStateFlow` fields for count/timeout/concurrency.

**Files to create/touch:**
- `app/.../app/ui/navigation/NavRoutes.kt`
- `app/.../app/ui/navigation/AppNavigation.kt`
- `app/.../app/ui/navigation/MoreToolsSheet.kt`
- `app/.../app/ui/screens/settings/SettingsScreen.kt` (new)
- `app/.../app/ui/screens/settings/SettingsViewModel.kt` (new)
- `app/.../app/MainActivity.kt` (theme wiring)
- Individual ViewModels (read defaults from DataStore)

---

## 9. Recent hosts per tool

**Depends on:** Item 1 (DataStore)

**Problem:** Every tool starts with a blank host/IP input on every launch. Power users
repeatedly retype the same targets. No tool retains any memory of previous inputs.

**What to do:**

1. Add DataStore keys (in the keys object from item 1) for recent hosts per tool:
   ```kotlin
   val RECENT_PING_HOSTS      = stringSetPreferencesKey("recent_ping_hosts")
   val RECENT_DNS_HOSTS       = stringSetPreferencesKey("recent_dns_hosts")
   val RECENT_PORTS_HOSTS     = stringSetPreferencesKey("recent_ports_hosts")
   val RECENT_TRACEROUTE_HOSTS = stringSetPreferencesKey("recent_traceroute_hosts")
   val RECENT_TLS_HOSTS       = stringSetPreferencesKey("recent_tls_hosts")
   val RECENT_WHOIS_HOSTS     = stringSetPreferencesKey("recent_whois_hosts")
   val RECENT_HTTP_HOSTS      = stringSetPreferencesKey("recent_http_hosts")
   // LAN Scanner stores subnets, not hosts:
   val RECENT_LAN_SUBNETS     = stringSetPreferencesKey("recent_lan_subnets")
   ```
   Note: `Preferences.stringSetPreferencesKey` does not preserve insertion order.
   Store recents as a JSON array string under a single `stringPreferencesKey` to preserve
   order, or use a pipe-delimited ordered list. Cap at 5 entries per tool.

2. Create a `RecentHostsRepository` in `app/.../app/data/RecentHostsRepository.kt`:
   - Inject `DataStore<Preferences>`.
   - Expose `getRecents(key): Flow<List<String>>` and `addRecent(key, host)`.
   - `addRecent` prepends to the list, deduplicates, and trims to 5 entries.
   - Provide it as a Hilt `@Singleton`.

3. In each affected ViewModel, inject `RecentHostsRepository` and:
   - Expose `val recentHosts: StateFlow<List<String>>` from the repository flow.
   - Call `recentHostsRepository.addRecent(key, host)` immediately before launching the
     use case (so the host is saved even if the user cancels mid-scan).

4. In each affected screen's input card, below the `OutlinedTextField`:
   - Show the recent hosts as a horizontal `LazyRow` of `SuggestionChip`s.
   - Each chip: label = the host string, trailing icon = `Icons.Default.Close` (to remove
     just that entry).
   - To the right of the chip row (or above it), a single "Clear all" `IconButton` using
     `Icons.Default.DeleteSweep` with `contentDescription = stringResource(R.string.action_clear_recents)`.
   - Tapping a chip populates the input field (does not immediately start the scan — the
     user still presses the action button).
   - Only show the row when `recentHosts.isNotEmpty()`.
   - Animate the row in with `AnimatedVisibility`.

5. Add string resources: `recent_hosts_label`, `action_clear_recents`,
   `action_remove_recent` (for individual chip close button content description).

6. Write unit tests for `RecentHostsRepository`: add first entry, add duplicate (moves to
   front), add 6th entry (drops oldest), clear all, clear one.

7. Write unit tests for each updated ViewModel verifying that `addRecent` is called on scan
   start and that `recentHosts` StateFlow reflects repository changes.

**Tools to update:** Ping, DNS, Port Scanner, Traceroute, TLS Inspector, WHOIS Lookup,
HTTP Probe, LAN Scanner (subnets).
**Tools that do not need recents:** Wi-Fi Scanner (no user input), Subnet Calculator
(input is computed, not a host), Network Topology (SNMP seed IPs — could be added later).

**Files to create/touch:**
- `app/.../app/data/RecentHostsRepository.kt` (new)
- `app/.../app/di/DataStoreModule.kt` (bind RecentHostsRepository)
- Each affected ViewModel
- Each affected screen's input composable
- `app/src/main/res/values/strings.xml`
- `app/.../app/data/RecentHostsRepositoryTest.kt` (new)

---

## 10. Share results

**Depends on:** nothing (independent of DataStore)

**Problem:** Users have no way to share tool output with others. The only clipboard copy
exists in Ping (CSV), DNS (individual record value, raw response), and Port Scanner (scan
report). Traceroute, TLS Inspector, WHOIS Lookup, LAN Scanner, and HTTP Probe have no
export mechanism at all. No tool has an `Intent.ACTION_SEND` share sheet.

**What to do:**

1. Create a utility function in `app/.../app/util/ShareUtils.kt`:
   ```kotlin
   fun Context.shareText(text: String, subject: String) {
       val intent = Intent(Intent.ACTION_SEND).apply {
           type = "text/plain"
           putExtra(Intent.EXTRA_SUBJECT, subject)
           putExtra(Intent.EXTRA_TEXT, text)
       }
       startActivity(Intent.createChooser(intent, null))
   }
   ```

2. For each tool below, add a Share icon button (`Icons.Default.Share`) to the result
   card's top-right action area (alongside any existing Copy button). Tapping it calls
   `context.shareText(formattedResult, subject)`.

   Define a `buildShareText()` function per tool that formats the result as readable
   plain text. Use the same format as any existing `buildCsvOutput` / `buildScanReport`
   functions where they exist. Tool-specific formats:

   | Tool | Subject string | Content |
   |------|---------------|---------|
   | Ping | `"Ping – {host}"` | Stats block + per-packet table (existing `buildCsvOutput`) |
   | Traceroute | `"Traceroute – {host}"` | One line per hop: `{n}. {ip} ({hostname}) {rtt}ms [{city}, {country}]` |
   | Port Scanner | `"Port scan – {host}"` | Existing `buildScanReport` |
   | TLS Inspector | `"TLS – {host}:{port}"` | Chain summary + per-cert: subject, issuer, validity, SANs, fingerprint |
   | WHOIS Lookup | `"WHOIS – {query}"` | Registrar, dates, name servers, status codes |
   | LAN Scanner | `"LAN scan – {subnet}"` | One line per host: `{ip} ({hostname}) {vendor} {rtt}ms` |
   | HTTP Probe | `"HTTP – {url}"` | Status, time, redirect chain, security header grades |
   | DNS Lookup | `"DNS {type} – {domain}"` | All records + query time (per-record, not raw) |

3. Add string resources for each subject string and for `action_share`
   (`contentDescription` on the icon button).

4. The Share button should only be visible when the tool is in a `Success`/`Finished`
   state, not during `Idle`, `Loading`, or `Error` states. Use `AnimatedVisibility`.

**Files to create/touch:**
- `app/.../app/util/ShareUtils.kt` (new)
- `app/.../app/ui/screens/PingScreen.kt`
- `app/.../app/ui/screens/TracerouteScreen.kt`
- `app/.../app/ui/screens/PortsScreen.kt`
- `app/.../app/ui/screens/tls/TlsInspectorScreen.kt`
- `app/.../app/ui/screens/whois/WhoisScreen.kt`
- `app/.../app/ui/screens/lan/LanScanScreen.kt`
- `app/.../app/ui/screens/httprobe/HttpProbeScreen.kt`
- `app/.../app/ui/screens/DnsScreen.kt`
- `app/src/main/res/values/strings.xml`

---

## 11. ViewModel unit tests

**Depends on:** Items 9 is complete (ViewModels are stable post-recent-hosts changes)

**Problem:** The `:app` module has effectively zero ViewModel tests (one auto-generated
placeholder). All business logic in ViewModels — state transitions, cancellation, error
paths, parameter validation — is untested.

**What to do:**

Add `app/src/test/kotlin/.../` test sources. For each ViewModel listed below, write tests
that cover:

- Initial state is `Idle` (or equivalent)
- Starting a scan with a valid host transitions to `Running`/`Loading`
- A successful use-case emission transitions to `Finished`/`Success`
- A use-case exception transitions to `Error` with the message text
- Calling stop/cancel during a running scan transitions back to the appropriate state
- `recentHosts` is updated when a scan is started (after item 9)

Use MockK to mock use cases and `kotlinx.coroutines.test.runTest` with
`StandardTestDispatcher` for coroutine control. Use `Turbine` (or `collectAsState` with
`runCurrent`) to assert on `StateFlow` emissions.

**ViewModels to test (in priority order):**

1. `PingViewModel` — most complex; has `Running` intermediate state with packet list,
   cancel via `Job`, CSV export helper.
2. `LanScanViewModel` — streaming results, progress tracking, search/filter logic.
3. `DnsViewModel` — single-shot, record type switching, custom server selection.
4. `PortScanViewModel` — preset group switching, progress streaming, copy report.
5. `TracerouteViewModel` — hop streaming, geolocation enrichment per hop.
6. `TlsInspectorViewModel` — single-shot, certificate chain state.
7. `WhoisViewModel` — relay chain animation state transitions.
8. `HttpProbeViewModel` — tab state, redirect chain, header security grading.

**Dependencies to add** (if not already present) to `app/build.gradle.kts` test block:
```kotlin
testImplementation(libs.turbine)                 // add to libs.versions.toml too
testImplementation(libs.coroutines.test)         // already present
testImplementation(libs.mockk)                   // already present
```

**Files to create:**
- `app/src/test/kotlin/.../ui/screens/ping/PingViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/lan/LanScanViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/dns/DnsViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/portscan/PortScanViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/traceroute/TracerouteViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/tls/TlsInspectorViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/whois/WhoisViewModelTest.kt`
- `app/src/test/kotlin/.../ui/screens/httprobe/HttpProbeViewModelTest.kt`
