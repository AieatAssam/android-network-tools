# Net Swiss Knife – Audit Report

**Originally audited:** 2026-09-12 · **Revised:** 2026-09-13
**Repository:** android-network-tools · **Branch:** audit/2026-09-12
**Target SDK:** 37 | **Min SDK:** 26

---

## How to read this report

This is a full rewrite of the original audit, not an addendum. The first pass contained a
meaningful number of claims that didn't survive contact with the actual code — wrong file
locations, wrong numeric specifics (byte limits, provider names), and outright false
"missing feature" claims for things that were already implemented. Every finding below was
re-verified directly against the current source before being kept, corrected, or dropped.
Findings carry one of these statuses:

| Status | Meaning |
|---|---|
| 🔴 **Open** | Verified real, still unaddressed. |
| 🟡 **Open, corrected** | Real gap, but the original claim's specifics (file, number, name) were wrong — corrected here. |
| ✅ **Fixed** | Was real; fixed during this audit cycle. |
| ⏸ **Deferred** | Real, but scoped as its own initiative rather than a drive-by fix (reason given). |
| — *(not listed)* | Original claim was checked and found **false**; removed rather than kept as a strikethrough. A one-line note explains why, folded into the relevant section, so nothing is silently disappeared without a trail. |

Claims removed entirely as false, with the evidence that disproved them, are noted inline
in each section rather than collected in a separate ledger — that made the previous
revision harder to scan than the report itself.

---

## Executive Summary

Net Swiss Knife is a **production-quality Android networking utilities app**: 14
fully-implemented networking tools, a clean 3-layer architecture
(`core-network` → `core-domain` → `app`) with zero Android imports below the `:app`
module, and Material 3 UI polish (animated entrances, gradient heroes, shimmer loading,
dark-mode-safe theming) that's consistent across every screen.

**Since the original pass, the following were completed:**
- Adaptive layouts (`NavigationSuiteScaffold` + content-width capping) for
  tablet/foldable/desktop.
- A live-refresh/state-integrity pass across the tools that stream or auto-refresh
  results: Network Topology no longer rotates its graph on every new node, and node
  inspection works mid-scan; Wi-Fi Scanner freezes list order while you're inspecting a
  network instead of reordering under your finger; Ping shows when packet history has
  been trimmed instead of dropping it silently; LAN Scanner is virtualized and IP-sorted.
- A full onboarding rewrite (4-page guided tour, was a single static card list).
- A help-content rewrite across all 14 tools: every help sheet now opens with a
  plain-language "what is this concept?" explanation before the parameter reference,
  and the help component itself gained bullet-list and callout support.
- HTTP Probe's security header analysis extended to 9 checks (added COOP/COEP).
- Instrumented UI test coverage expanded from 4 to 12 tests.

**Overall Grade: A** — architecture and UI execution are excellent; what's left is real
but well-understood: Material 3 Expressive is blocked on an upstream alpha dependency
(not actionable without accepting alpha risk), a resilience layer (retry/backoff, offline
detection) is absent app-wide, `ktlint`/`detekt` was never adopted, and a handful of
tool-specific gaps (DNSSEC, OCSP, RDAP, IPv6 in a few tools) are legitimate feature
opportunities rather than defects.

---

## 1. Architecture & Code Quality

### ✅ Strengths

| Area | Observation |
|------|-------------|
| **Layer separation** | `core-network`/`core-domain` have zero Android imports; only `:app` depends on Android SDK |
| **DI** | Hilt modules per-tool; `@HiltViewModel` + `@Inject` constructor pattern consistent |
| **Async** | `viewModelScope` + `StateFlow` for UI state; `collectAsStateWithLifecycle` everywhere |
| **Navigation** | Single `NavRoutes` sealed class + `ToolInfo` data class; routes declared once; adaptive nav shell (`NavigationSuiteScaffold`) since this cycle |
| **Testing** | JUnit 5 + MockK; repository/impl tests in `:core-network`, use case tests in `:core-domain`, Compose UI tests in `:app` |
| **Build** | Version catalog (`libs.versions.toml`); AGP 9.4; Gradle 9.7; Kotlin 2.4.10 |

### ⚠️ Gaps & Technical Debt

| ID | Issue | Severity | Location | Status |
|----|-------|----------|----------|--------|
| ARCH-04 | No `ktlint`/`detekt` in CI — formatting/style drift possible | Medium | `.github/workflows/ci.yml` | ⏸ Deferred — adopting a linter now means generating a style baseline across ~100+ existing files first; that's its own initiative, not a drive-by fix. |
| ARCH-05 | `icmpenguin` pinned at `1.0.0-rc.4` | Informational | `libs.versions.toml:6` | ✅ Documented — verified against upstream (github.com/impalex/icmpenguin/releases): no stable release exists, rc.4 is the latest tag. `libs.versions.toml` now carries a comment recording this so it stops looking like an oversight. |

**Not real** (checked, found false in an earlier pass, not reproduced here): `buildConfig = true`
being dead code — `BuildConfig.DEBUG`/`VERSION_NAME` are used in 5 files; `core-domain`
having no Kover config — it has a 70% `minBound` at `core-domain/build.gradle.kts:35-40`;
`app/debug.keystore` being a leaked secret — it's intentionally committed for consistent
local debug signing (documented in `.gitignore`), uses the well-known default
`androiddebugkey` credentials, and is never used for release signing.

---

## 2. UI/UX — Android 15/16 Readiness

### 2.1 Screen Transitions & Animations — all ✅

Animated entry, nav transitions, card-based layouts, loading states, shimmer, smooth
state transitions, ripple/haptics, gradient accents, full typography scale, and
dark-mode-safe theming are all implemented consistently across every screen. No changes
needed here.

### 2.2 Platform Features

| Feature | Status |
|---------|--------|
| **Predictive Back** | ✅ Already implemented — `android:enableOnBackInvokedCallback="true"` in the manifest, with Navigation Compose 2.10.x supplying predictive-back transitions automatically on top of it. |
| **Edge-to-Edge** | ✅ Already implemented, deliberately not via `enableEdgeToEdge()` — `MainActivity.kt` uses `WindowCompat.setDecorFitsSystemWindows(window, false)` instead, specifically to avoid APIs Play Console flags as deprecated on Android 15. IME insets are consumed via the bottom bar + `consumeWindowInsets`. |
| **Adaptive Layouts** | ✅ Done this cycle — `NavigationSuiteScaffold` switches bottom bar → rail at 600dp width; `AdaptiveContentBounds` caps content to 840dp on wide windows. Verified live on-device at both breakpoints. |
| **Material 3 Expressive** | ⏸ Blocked on upstream — `MaterialShapes` and `LoadingIndicator` (the actual Expressive components) only exist in `material3:1.5.0-alpha`; the stable `1.4.0` this project's BOM pins ships only internal design tokens for them, not the public composables. The adaptive nav shell above already picks up stable 1.4.0's current `NavigationBar`/`NavigationRail` styling for free. Re-evaluate once Expressive ships stable. |
| **Per-App Language Support** | 🔴 Open — no `LocaleManager` integration; follows system locale only. Low priority, no user complaint driving it. |
| **Dynamic Color** | ✅ Enabled — `dynamicColor = true` for SDK 31+ in `Theme.kt:52-54`. |
| Health Connect / Photo Picker | N/A — not applicable to this app's scope. |

**Also verified clean:** no legacy Android View usage anywhere — no layout XML, no
Fragments, no `AndroidView`/`ComposeView` interop, no `WebView`, no `AlertDialog.Builder`.
Both activities are 100% `setContent { }` Compose.

### 2.3 Accessibility

| Issue | Status | Evidence |
|-------|--------|----------|
| Screen-title heading semantics | ✅ Fixed | `HeroTitleText` (used by every screen via `ToolHeroHeader`) applies `Modifier.semantics { heading() }`. |
| Touch targets below 48dp | 🟡 Open, corrected | Original claim was vague ("some chips/buttons across the app"). Concretely: **4 `IconButton`s in `HttpProbeScreen.kt`** are sized 28-32dp (lines 387, 394, 876, 1050) — all below the 48dp minimum. M3's `IconButton`/`Button` default to 48dp already; these are explicit overrides. Chips are intentionally excluded — M3's chip spec uses a smaller, documented touch-target size by design, not a bug. |
| Live region announcements for async state changes | 🔴 Open | Confirmed: no `liveRegion`/`LiveRegionMode` usage anywhere in the codebase. Loading → success/error transitions aren't announced to TalkBack beyond whatever the new content's own text provides. |
| Content descriptions on decorative icons | Not real | A raw `Icon(...)` vs. `contentDescription` count showed a ~18-call gap, but a line-by-line check of `WifiScanScreen.kt`, `TracerouteScreen.kt`, and `HttpProbeScreen.kt` found zero genuine misses — every icon sets `contentDescription` either by name, by a string resource passed positionally, or explicitly `null` for decorative icons next to a label. The count mismatch is a grep-methodology artifact (positional args, multi-line calls), not a real gap in the files checked. |
| Color contrast on gradient text | 🔴 Open, unverified | Hero headers render `onPrimaryContainer` text over a gradient background. Contrast ratio wasn't computed (needs a rendered-pixel tool, not static analysis) — flagging as unverified rather than confirmed. |

---

## 3. Tool-Specific Findings

Each entry was re-read against current source. Claims that turned out false are noted and
dropped rather than kept as strikethrough noise.

### 3.1 Ping

| Issue | Status |
|-------|--------|
| No IPv6 support in host input (ICMP fallback only) | 🔴 Open — confirmed, no IPv6 handling in `core-network/ping`. |
| ~~RTT chart uses raw `drawText`, no font-scale support~~ | Not real — the chart uses `rememberTextMeasurer()` + Compose `drawText(textLayoutResult=...)` with `sp`-based label sizing, which already respects the system font-scale setting. There's no `nativeCanvas` call in this file at all. |
| ~~CSV export missing column headers~~ | Not real (headers are always emitted) — but verification found a **real** bug instead: unescaped commas/quotes/newlines in `host`/`errorMessage` CSV fields could produce malformed rows. ✅ Fixed with RFC4180-style field quoting. |

### 3.2 Traceroute

| Issue | Status |
|-------|--------|
| `key(hop.hopNumber)` + `animateFloatAsState` instead of `AnimatedVisibility` | 🔴 Open (by design) — still present, and the surrounding code comment explains it's a deliberate workaround for an `AnimatedVisibility`/`SubcomposeLayout` nesting `IllegalStateException`. Real tech debt, but documented, not accidental. |
| GeoIP lookup: single hardcoded provider, no configurable key | 🟡 Open, corrected — the provider is **ipinfo.io** (free tier, HTTPS, no key), not "IP-API" as originally claimed. In-memory result caching (`ConcurrentHashMap`) already exists — the original recommendation to "cache results" is already done. The remaining valid gap is narrower: one hardcoded provider with no fallback if it's down or rate-limited. |
| No map view for geographic hops | 🔴 Open — confirmed, no `MapView`/map integration anywhere. |
| ~~MTU discovery toggle uses `packetSize == 0` as an unclear sentinel~~ | Not real as a UX complaint — there's an explicit, clearly-labeled "MTU Discovery" `Switch`, and the manual packet-size slider only appears via `AnimatedVisibility` when discovery is off. The `== 0` sentinel is an internal implementation detail that never surfaces to the user. |

### 3.3 Port Scanner

| Issue | Status |
|-------|--------|
| `RangeSlider` thumb values not visible while dragging | 🔴 Open — confirmed, the `RangeSlider` call has no `startThumb`/`endThumb` label composables. |
| Banner grab may truncate, no indicator shown | 🟡 Open, corrected — the buffer is **256 bytes** (`ByteArray(256)` in `PortScanRepositoryImpl.kt`), not 1024 as originally claimed. No "truncated" indicator exists either way. |
| No service version detection beyond raw banner text | 🔴 Open — confirmed, `WellKnownPorts.kt` only does static port→name lookup, no active fingerprinting. |
| ~~Concurrency slider max 300, no safety warning~~ | ✅ Fixed — an inline warning now appears above 100 concurrent connections. |

### 3.4 DNS Lookup

| Issue | Status |
|-------|--------|
| No DNSSEC validation indicator | 🔴 Open — confirmed, no DNSSEC/AD-flag/RRSIG handling anywhere in `core-network/dns`. |
| Custom DNS server: only IP-format validated, no reachability test | 🔴 Open — confirmed, validation is `HostValidator.isValidIpv4/isValidIpv6` only, no "test connectivity" action. |
| ~~PTR reverse-lookup not documented in UI~~ | ✅ Fixed — the recent help-content rewrite added this explicitly, twice: "PTR reverse-resolves an IP back to a hostname" and "Use PTR lookups to reverse-resolve an IP address to a hostname" in the DNS help sheet. |
| ~~Keyboard doesn't dismiss on preset server selection~~ | ✅ Fixed — `keyboardController?.hide()` now called on selection. |

### 3.5 Wi-Fi Scanner

| Issue | Status |
|-------|--------|
| Spectrum/gauge charts use `nativeCanvas.drawText`, bypassing Compose's RTL-aware text layout | 🔴 Open — confirmed with 5 call sites (`WifiScanScreen.kt:648,671,713,1050,1055`). Genuinely different from Ping's chart, which correctly uses `TextMeasurer`. |
| No channel-overlap visualization for 5/6 GHz (2.4 GHz only) | 🔴 Open — confirmed, no band-specific heatmap beyond the existing spectrum curves. |
| Auto-refresh hardcoded to 10s, not configurable | 🔴 Open — confirmed, `AUTO_REFRESH_INTERVAL_MS = 10_000L` is a private constant. |
| ~~List reorders under your finger during auto-refresh~~ | ✅ Fixed — list order now freezes while a network is selected/expanded, resuming live sort once you deselect. Selected APs that drop out of range now show a message instead of the detail sheet silently closing. |
| Wi-Fi permission rationale | 🟡 Already adequate — the location requirement *is* explained in-app (`wifi_no_permission_body`); it's just shown after a denial rather than primed before the first system dialog. Low-priority polish, not a missing-rationale bug. |

### 3.6 TLS Inspector

| Issue | Status |
|-------|--------|
| No certificate pinning test | 🔴 Open — confirmed, no pinning/HPKP feature exists. |
| No OCSP/CRL revocation check | 🔴 Open — confirmed, no revocation network call anywhere in the TLS flow. |
| No Certificate Transparency log lookup | 🔴 Open — confirmed, no crt.sh/CT integration. |

### 3.7 Network Topology

| Issue | Status |
|-------|--------|
| No export (GraphML/DOT/PNG) | 🔴 Open — confirmed, no export/share action on the topology graph. |
| ~~SNMP v3 auth/priv labels show raw OIDs~~ | Not real — `V3AuthProtocol`/`V3PrivProtocol` are already friendly enums (`MD5`, `SHA`, `DES`, `AES128`), displayed via `.entries.map { it.name }`. There were never raw OIDs in this codebase. |
| ~~Graph rotates/redistributes on every new node discovered~~ | ✅ Fixed — node positions are now assigned once and cached forever; only new nodes get placed, existing ones never move. |
| ~~Tapping a node mid-scan is a silent no-op~~ | ✅ Fixed — node selection now works during `Discovering`, not just after `Done`. |

### 3.8 WHOIS

| Issue | Status |
|-------|--------|
| Relay-chain visualizer is custom Canvas drawing | 🟡 Open, less severe than framed — it's genuinely custom (`RelayChainGeometry.kt` is a dedicated file), but it already has its own pure-logic separation with dedicated unit test coverage (explicitly named in `:app`'s Kover include list), so "hard to maintain" is a smaller concern than originally framed. |
| Static TLD→server fallback map is small/incomplete | 🟡 Open, corrected — the specific example was wrong: **`.app` and `.dev` are both present** (`whois.nic.google`). The map genuinely is small (11 entries: com/net/org/io/uk/de/fr/edu/app/dev), missing most ccTLDs referenced elsewhere in the same file's `COMPOUND_TLDS` set (e.g. `.au`, `.nz`, `.jp`, `.br`, `.cn` have no fallback server entry). Likely low real-world impact since standard WHOIS resolution normally goes through IANA referral first and this map is a fallback path, not the primary one. |
| No RDAP support | 🔴 Open — confirmed, only legacy text-based WHOIS, no RDAP (HTTP/JSON) client. |

### 3.9 HTTP Probe

| Issue | Status |
|-------|--------|
| No syntax highlighting/JSON validation in the request body editor | 🔴 Open — confirmed, plain text field only. |
| No cURL command export | 🔴 Open — confirmed. |
| Redirect chain doesn't capture intermediate response bodies | 🔴 Open — confirmed, `redirectChain: List<String>` stores only URLs; `responseBody` is populated for the final response only. |
| ~~Security header analysis stops at HSTS/CSP/X-Frame-Options~~ | ✅ Fixed — extended to 9 checks (added `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`, `Cross-Origin-Opener-Policy`, `Cross-Origin-Embedder-Policy`, `Server`), each now explained in plain language in the help sheet. README updated to match. |

### 3.10 Subnet Calculator

| Issue | Status |
|-------|--------|
| Binary breakdown grid may cram on narrow screens | 🟡 Open, unverified visually — `BinaryBitsRow` uses `Modifier.weight(1f)` (proportional shrink) with no horizontal scroll fallback, so 32 bit-boxes could get very narrow on small screens. Plausible from code; not confirmed with an actual rendered screenshot at a small width. |
| No IPv6 subnet mode | 🔴 Open — confirmed, `core-network/subnet` is IPv4-only. |
| ~~IP Range mode has no min ≤ max validation~~ | Not real — `SubnetCalculatorRepositoryImpl.calculateRange()` already does `require(minLong <= maxLong)` and surfaces it as an error. |

### 3.11 Speed Test

| Issue | Status |
|-------|--------|
| Single hardcoded provider (Cloudflare), no fallback | 🔴 Open — confirmed, `BASE_URL = "https://speed.cloudflare.com"` with no alternative. |
| No nearest-PoP/server selection | 🔴 Open — confirmed, no server-selection logic exists. |
| No bufferbloat grading | 🔴 Open — confirmed, no RFC 8290/AQM-related code anywhere. |

### 3.12 Wake-on-LAN

| Issue | Status |
|-------|--------|
| No target IP/hostname resolution, MAC only | 🔴 Open — confirmed, no resolution helper in the WoL screen/ViewModel. |
| No scheduled/recurring wake | 🔴 Open — confirmed, no `AlarmManager`/`WorkManager` usage. |
| Help content | ✅ Already excellent — this tool's help sheet was the model the rest of the app's help content was rewritten toward this cycle; only its string-key naming (`wol_help_*` → `help_wol_*`) was normalized for consistency. |

### 3.13 mDNS Browser

| Issue | Status |
|-------|--------|
| Scan duration hardcoded to 8s | 🟡 Open, worth a code-quality note — confirmed still fixed at `8_000L`, and it's a duplicated magic literal at two call sites in `MdnsDiscoveryScreen.kt`, not even a named constant. |
| No service-type filter chips | 🔴 Open — confirmed, no `FilterChip` for service type anywhere in the screen. |
| TXT records shown raw, no known-key parsing | 🔴 Open — confirmed, `parseTxtPairs` returns raw key-value pairs with no semantic interpretation (model/firmware/etc.). |

### 3.14 LAN Scanner

| Issue | Status |
|-------|--------|
| No vendor OUI database update mechanism | 🔴 Open — confirmed. |
| Port scan on a discovered host requires switching tools manually | 🔴 Open — confirmed, no "Scan Ports" quick action on a host row. |
| No network map visualization (could reuse Topology's graph) | 🟡 Open, narrower than claimed — `NetworkTopologyCard` (`LanScanScreen.kt:814`) already renders a gateway-centered device gallery (icons in a capped `FlowRow`, "+N more" overflow). It's a flat icon grid with no connectivity edges/lines, not an actual graph like the standalone Network Topology tool — so the specific gap is "no *connectivity-graph* view," not "no visualization at all." |
| ~~Host list not virtualized, no default sort~~ | ✅ Fixed — converted to `LazyColumn` with stable IP keys; default sort is now ascending numeric IP. |

### 3.15 Settings (app configuration, not a network tool)

| Issue | Status |
|-------|--------|
| No per-tool default persistence beyond ping/timeout/concurrency | 🔴 Open — confirmed. |
| No settings backup/restore | 🔴 Open — confirmed, no export/import anywhere in `SettingsScreen.kt`. |
| No privacy/no-telemetry statement | 🟡 Partially addressed — the new onboarding tour states "There's no analytics and no accounts — everything runs locally on your device," but that's shown once at first run. `SettingsScreen.kt` itself still has no persistently-visible privacy statement for someone who looks later. |
| Onboarding re-viewable after first dismissal | ✅ Already present — Settings → "Welcome guide" → Reset flips the completion flag back to false; not something this audit needed to add. |

---

## 4. Cross-Cutting Concerns

### 4.1 Permissions & Privacy

All manifest/runtime permissions match their tool's actual need (`INTERNET` for
network tools, `ACCESS_FINE_LOCATION` + `NEARBY_WIFI_DEVICES` for Wi-Fi Scanner,
`CHANGE_WIFI_MULTICAST_STATE` for mDNS). Wi-Fi's location requirement is explained
in-app; `CHANGE_WIFI_MULTICAST_STATE` is a normal (non-dangerous) permission with no
runtime consent flow to design for. No open items here beyond the onboarding
permissions-priming page added this cycle, which now explains both up front.

### 4.2 Data Persistence

| Data | Store | Encryption | Retention |
|------|-------|------------|-----------|
| Recent hosts, tool defaults, theme, onboarding flag | DataStore Preferences | Plaintext | Persistent (except recent hosts, session-scoped) |

🟡 **Open, corrected**: no `EncryptedSharedPreferences`/`EncryptedFile` exists anywhere in
the app, so the gap is real in principle — but the original claim's examples (SNMP
community strings, custom DNS servers) were wrong. Both are plain Compose
`remember { mutableStateOf(...) }` state (`TopologyDiscoveryScreen.kt:101,104,106`) and are
never written to DataStore at all — not "plaintext-persisted," simply never persisted.
Only the plaintext table above is actually written to disk. Low real risk either way:
nothing currently persisted is a credential, and `public` is SNMP's own well-known
insecure default, not a secret this app introduced.

### 4.3 Error Handling & Resilience

`NetworkResult<T>` (Success/Error) and `UiState` (Idle/Loading/Success/Error) sealed
classes are used universally, with retry actions on every error panel — this part is
solid. 🔴 **Open, unchanged**: no exponential backoff/retry logic in repositories, no
circuit breaker for external APIs (WHOIS, GeoIP, Speed Test), no offline detection with
cached/stale-data fallback. All three are real, but represent a resilience-layer design
spanning 4+ repositories — appropriately scoped as a dedicated initiative, not something
to bolt on inline.

### 4.4 Performance

Recomposition scoping (`key()` in `LazyColumn`s), background work placement
(`viewModelScope` + `Dispatchers.IO`), and lifecycle cleanup (`DisposableEffect`) are all
solid — no leaks found. 🔴 **Open, unchanged**: no baseline profile
(`BaselineProfileRule`/macrobenchmark) for startup time measurement — deferred, as it's a
measurement initiative rather than a code fix.

---

## 5. Testing & Quality Gates

| Metric | Target | Actual | Status |
|--------|--------|--------|--------|
| `:core-network` unit tests | 70% coverage | `koverVerify` passes | ✅ |
| `:core-domain` unit tests | 70% coverage | `koverVerify` passes | ✅ |
| `:app` pure logic coverage | 90% | `koverVerify` passes | ✅ |
| Instrumented/UI tests | — | 12 tests (`UiSmokeTest` + Ports/HttpProbe/Subnet/Ping), run against the `dev36` AVD | 🟡 Present, still thin against 14 tool screens |
| Lint errors | 0 | 0 | ✅ |
| CodeQL | Enabled in CI | ✅ Verified — `.github/workflows/ci.yml` has a dedicated `codeql:` job (`github/codeql-action/init@v4` + `analyze@v4`, `languages: java-kotlin`) | ✅ |

The original "no instrumented tests, `androidTest` empty" claim from the first audit pass
was false even at the time — the real blocker was this sandbox's Android SDK missing
`platforms;android-37`/`build-tools;36.0.0`, which made every `:app` Gradle task fail
before a single test could run. That's been fixed via `/etc/nixos/configuration.nix`.

---

## 6. Missing Tool Opportunities (unchanged, still valid)

None of these exist in the current 14-tool lineup; re-confirmed by grep against the
current codebase.

| Tool | Category | Priority | Notes |
|------|----------|----------|-------|
| IP Calculator (IPv6) | Utilities | High | Extend Subnet Calculator |
| Bandwidth Monitor (per-app/per-UID) | Diagnostics | High | `TrafficStats`/`NETWORK_STATS`; per-app needs root/ADB |
| Network Quality / Bufferbloat Test | Diagnostics | High | RFC 8290; pairs naturally with Speed Test |
| Certificate Transparency Monitor | Security | Medium | Query CT logs; alert on new certs for a domain |
| DoH/DoT Tester | Security | Medium | Compare resolver encryption support |
| BGP Looking Glass | Advanced | Medium | Query RIPE RIS/RouteViews for prefix origins |
| WireGuard Config Generator | Utilities | Medium | QR-code config generation |
| IP Geolocation Bulk Lookup | Utilities | Medium | Batch WHOIS/GeoIP for a list of IPs |
| IPv6 Subnet Scanner (NDP) | Diagnostics | Medium | Neighbor Discovery Protocol scan |
| NTP Offset | Diagnostics | Low | Clock offset vs. NTP pool |
| SSH Key Scanner | Security | Low | Port 22 banner + key fingerprint |
| MQTT / CoAP Discovery | IoT | Low | Scan 1883/8883 or UDP 5683 |
| Packet Capture (PCAP) | Advanced | Low | Needs `VpnService` or system capture permission |

---

## 7. README Sync Status

✅ Fully synced across all 14 tools, including the HTTP Probe security-header list
(verified `README.md:104` now lists all 9 checks including COOP/COEP).

---

## 8. Prioritized Action Plan

### Fixed this cycle
1. ✅ Network Topology graph stability + mid-scan node selection.
2. ✅ Wi-Fi Scanner order-freezing during auto-refresh + AP-disappearance messaging.
3. ✅ Ping continuous-mode trimmed-history indicator + CSV field escaping.
4. ✅ LAN Scanner virtualization + IP sort.
5. ✅ Port Scanner concurrency safety warning.
6. ✅ DNS keyboard dismiss on preset selection.
7. ✅ HTTP Probe COOP/COEP checks (9 total now).
8. ✅ Accessibility heading semantics on every screen (`ToolHeroHeader`).
9. ✅ Adaptive layouts (nav shell + content-width capping).
10. ✅ Onboarding rewritten as a 4-page guided tour.
11. ✅ Help content rewritten for all 14 tools (concept-first, bulleted).
12. ✅ Instrumented test suite expanded 4 → 12.

### Open — concrete, scoped fixes worth doing next
13. 🔴 Fix the 4 sub-48dp `IconButton`s in `HttpProbeScreen.kt` (lines 387, 394, 876, 1050).
14. 🔴 Replace `nativeCanvas.drawText` with `TextMeasurer` in `WifiScanScreen.kt` (5 call sites) for RTL correctness.
15. 🔴 Add live-region semantics to loading/error/success transitions.
16. 🔴 Add a persistent privacy statement to `SettingsScreen.kt` (onboarding-only today).
17. 🔴 Name the mDNS scan-duration magic literal (`8_000L`, two call sites) as a constant.

### Deferred — real, but sized as their own initiative
18. ⏸ `ktlint`/`detekt` adoption (needs a style baseline first).
19. ⏸ Material 3 Expressive (blocked on `material3:1.5.0` reaching stable).
20. ⏸ Resilience layer: retry/backoff, circuit breakers, offline detection across 4+ repositories.
21. ⏸ Baseline profile / macrobenchmark for startup time.

### Feature opportunities (Low priority, net-new work, not defects)
22. DNSSEC indicator (DNS), OCSP/CRL + CT log (TLS), RDAP (WHOIS), cURL export + syntax
    highlighting (HTTP Probe), IPv6 subnet mode, bufferbloat grading (Speed Test),
    scheduled wake (WoL), service-type filters + TXT parsing (mDNS), OUI updates + port-scan
    integration (LAN), settings backup/restore, and the tools listed in §6.

---

## 9. Conclusion

Net Swiss Knife remains exceptionally well-built for its category. This revision found
that roughly a third of the original audit's findings didn't hold up under direct code
inspection — wrong byte counts, wrong provider names, and several "missing" features that
were already correctly implemented (predictive back, edge-to-edge, SNMP protocol labels,
PTR documentation, min≤max validation). That's now corrected throughout rather than
carried forward.

What's genuinely left breaks into two buckets: a short list of concrete, small-scope bugs
(§8 items 13-17) that are worth fixing directly, and a set of larger initiatives
(resilience layer, linting, Material 3 Expressive, baseline profiling) that are real but
deliberately not rushed into a fix pass, plus a long tail of net-new feature opportunities
that were never defects to begin with. The codebase is, and remains, ready for production
release.
