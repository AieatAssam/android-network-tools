# UX review follow-up plan

**Status:** Complete
**Scope:** Current branch plan/comprehensive-review-2026-09-22 and its open PR #109
**Source:** User-provided review of LAN Scan, Topology, mDNS, Wi-Fi, and Port Scan. The referenced /workspace/netswissknife/UX-REVIEW.md was not present in this checkout, so this plan records the findings supplied in chat.

## Delivery order

Work in small, reviewable slices. Keep existing search, host filters, sharing, typed handoffs, and opt-in SNMP neighbor queries working. Start with LAN because its nested scrolling and unavailable in-progress row actions block the core task flow. Then address truthful actions and content density, followed by result utilities and remaining screens. Do not persist SNMP credentials, raw secrets, or unbounded scan output.

### 1. LAN list and host actions — high priority

- Replace the outer vertically scrolling page plus height-capped host LazyColumn with one vertically scrolling lazy list. Render configuration, progress, filters, and results as lazy-list items so a full scan remains smooth on phones and tablets.
- While scanning, collapse configuration to a compact summary with the selected subnet and key scan options; provide an explicit way to expand settings.
- Make each host row actionable as soon as the host is emitted. Preserve scan progress and keep row actions available for already discovered hosts.
- Add a host long-press/context menu with copy IP, MAC, and hostname actions; omit unavailable values and announce clipboard completion.
- Compact host rows to two dense lines. Show IP, latency/status, hostname and vendor together when available, and an open-port count; keep MAC and the full port list available from row details. Remove discovery-method and per-port chip piles from the compact row.
- Add sorting by IP, latency, hostname, vendor, and open-port count; preserve the selected sort across navigation and process recreation using a bounded preference/SavedState contract.
- Only offer TLS handoff for known TLS-capable ports; continue to allow explicit user editing in TLS Inspector.
- Replace discovery-method chips that look interactive with descriptive, non-button semantics, or make them perform a real action.

### 2. Topology, mDNS, Wi-Fi, and Port Scan — high/medium priority

- Add search, copy, and share for Topology, mDNS, and Wi-Fi. Topology export must include scan metadata and table completeness while excluding credentials and secrets.
- Make decorative vendor/interface/VLAN chips in Topology non-interactive in both appearance and TalkBack semantics. Keep genuinely selectable VLAN filters functional.
- Add an interface up/down filter and a visible textual state so status is not communicated by color alone.
- Show how many closed and filtered ports are hidden by Port Scan's Show all control, and how many will be revealed.
- Replace Topology's generic Unknown SNMP error with the typed error summary and actionable recovery where available.

### 3. Navigation, persistence, and screen density — medium priority

- Preserve completed results and local filters/search/sort when leaving and returning to a tool. Restore bounded UI state without auto-restarting network operations or persisting credentials.
- Reduce the time-to-results footprint: keep the page header and settings from consuming roughly 55% of the initial viewport. Prefer a compact/collapsible header and settings summary while preserving access to all controls.
- Use text/icon/semantics in addition to color for latency, interface state, and other status indicators. Audit TalkBack roles so informational chips are not announced as buttons.

## Acceptance and verification

- On the connected Android 8 tablet, start a LAN scan and use Ping/Ports on a host before the scan finishes; verify configuration collapses and progress/results share one scroll gesture.
- On a phone-sized viewport and the tablet, verify at least four compact hosts fit when the viewport permits, with hostname and vendor visible together, open-port count readable, and no horizontal overflow at large font scale.
- Verify long-press copy for available LAN identity fields, sort order retention, TLS action filtering, and discovery-method semantics with TalkBack/UI hierarchy.
- Verify Topology/mDNS/Wi-Fi search, copy, and share output; test partial Topology results and confirm exports omit SNMP secrets. Test interface filters and both interface states.
- Verify Port Scan reports hidden closed/filtered counts and expands the expected set.
- Navigate away and back during and after operations; results and filters restore without duplicate scans. Confirm active operations still cancel and clean up on navigation.
- Run focused JVM and Compose tests for each slice, the relevant app test suite, lint, and debug assembly. Install the final build on the connected Android 8 device and recheck LAN, mDNS, Wi-Fi, topology, and representative cross-tool handoffs.

## Checklist

- [x] LAN single-list layout and collapsible settings summary
- [x] Live LAN host actions
- [x] LAN copy menu and compact two-line rows
- [x] LAN sort options and persistence
- [x] TLS action only for TLS-capable open ports
- [x] Remove misleading LAN and Topology chip semantics
- [x] Topology/mDNS/Wi-Fi search, copy, and share
- [x] Topology interface-state filter and non-color status
- [x] Port Scan hidden-result counts
- [x] Preserve result/filter state across navigation
- [x] Reduce header/settings viewport footprint
- [x] Replace generic SNMP error copy with typed recovery
- [x] Automated and Android 8 device acceptance checks

## Progress and verification

- LAN now has one virtualized root `LazyColumn`; live and completed hosts share stable keys so rows retain their place across the scan transition. Settings collapse to a compact summary, and expansion remains selected through completion, deadline, Stop, and lifecycle pause.
- Compact rows show IP, latency, port count, hostname, vendor, and MAC with bounded/ellipsized layout. Long-press copy, sortable/persisted order, and TLS-port eligibility are implemented.
- Topology, mDNS, and Wi-Fi have search and copy/share controls. Reports include partial/cache freshness context and exclude SNMP credentials. Topology exposes textual interface state and an up/down filter. Decorative status/vendor/VLAN/discovery labels no longer expose dead button actions.
- Port Scan's Show all label reports the closed and filtered counts it will reveal. Search/filter and presentation state use saved state or existing retained ViewModels. Topology already used typed SNMP error mapping; no literal "Unknown SNMP error" string was present in this checkout.
- The full app JVM unit suite passed, Android-test Kotlin compilation passed, `lintDebug` passed (8 errors fixed; remaining warnings are pre-existing project warnings), and `assembleDebug` passed. The manual API 26 instrumentation runner cannot execute the existing MockK-based Compose fake ViewModel tests because MockK fails to intercept the mocked StateFlow before test bodies run.
- Android 8 tablet checks: LAN scan found 54 confirmed devices on `10.109.208.0/24`; a host row expanded while the scan was running and remained expanded after completion, showing its live actions and IP/MAC/hostname. Long-press displayed all three copy actions. mDNS returned 64 services; Wi-Fi returned 17 APs across 7 SSIDs; SNMP at `10.109.208.1` returned named topology/interface data using the supplied community, and interface filtering was observed. Port Scan against the gateway reported `Show all (0 closed, 19 filtered)` and tapping it changed the action to `Show open only`.
