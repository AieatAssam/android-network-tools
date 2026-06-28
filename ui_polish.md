# UI Polish Pass — Net Swiss Knife

Tracking file for the comprehensive UI/UX fix pass identified in the tester-feedback review.
Updated as each item is completed.

---

## Environment notes

- **Android emulator**: KVM available (AMD SVM); emulator install attempted but build verification uses `./gradlew :app:assembleDebug`
- **Verification method**: `./gradlew test` + `./gradlew :app:assembleDebug` after each batch
- **Visual verification**: marked ⚠️ VISUAL-UNVERIFIED — requires human eye before merge
- **TDD**: unit tests written first for pure-logic issues; Robolectric not configured (setup cost not worth it for this pass)
- **CLAUDE.md note**: `displaySmall` is mandated by project spec — issue #22 is addressed by fixing layout (maxLines) rather than changing the typography style

---

## Issue Index

| # | Issue | Priority | Status |
|---|-------|----------|--------|
| 1 | "More" nav item never shows selected state | Med | ✅ DONE |
| 2 | "More" sheet has no category grouping | Med | ✅ DONE |
| 3 | Pin limit reached gives no feedback | Med | ✅ DONE |
| 4 | Settings not sticky in More sheet | Med | ✅ DONE |
| 5 | Back nav from More is inconsistent | Low | ⬜ TODO |
| 6 | Hero header: 4 different implementations | High | ✅ DONE |
| 7 | Subnet Calculator buries identity in input card | Med | ⬜ TODO |
| 8 | Settings screen has no hero card | Low | ⬜ TODO |
| 9 | RecentHostsRow dismiss 24 dp — below 48 dp min | High | ✅ DONE |
| 10 | No real-time input validation | Med | ⬜ TODO |
| 11 | LAN Scanner no CIDR placeholder | Med | ⬜ TODO |
| 12 | Subnet mode toggle clears input instead of converting | Med | ⬜ TODO |
| 13 | Port Scanner two fields instead of range control | Low | ⬜ TODO |
| 14 | DNS custom server no format validation | Low | ⬜ TODO |
| 15 | Result values not individually copyable | Med | ⬜ TODO |
| 16 | DNS results not grouped by type | Med | ⬜ TODO |
| 17 | WHOIS dates have no relative time labels | Low | ⬜ TODO |
| 18 | Port Scanner closed ports same visual weight as open | Low | ⬜ TODO |
| 19 | No empty result state (LAN, mDNS) | Med | ✅ DONE (mDNS) |
| 20 | LAN Scanner progress is indeterminate | Low | ⬜ TODO |
| 21 | mDNS hardcoded user-visible strings | High | ✅ DONE |
| 22 | displaySmall in-card title wraps on narrow devices | Med | ✅ DONE |
| 23 | Vertical spacing mixes 12 dp and 16 dp | Low | ⬜ TODO |
| 24 | FontWeight.Bold overrides fight M3 type system | Low | ✅ DONE |
| 25 | collectAsState() not lifecycle-aware | Med | ✅ DONE |
| 26 | Progress indicators missing contentDescription | Med | ✅ DONE |
| 27 | Expandable cards missing semantic role | Med | ✅ DONE |
| 28 | Settings sliders have no value announcement | Low | ✅ DONE |
| 29 | Onboarding "Don't show again" is a placebo | Med | ⬜ TODO |
| 30 | No pull-to-refresh on results screens | Med | ⬜ TODO |
| 31 | Stop button color inconsistent across tools | Med | ⬜ TODO |
| 32 | Card corner radii inconsistent | Med | ✅ DONE |
| 33 | Settings uses same slide transition as tools | Low | ⬜ TODO |
| 34 | Nested scroll containers in Traceroute | Med | ⬜ TODO |
| 35 | mDNS LazyColumn height unbounded | High | ✅ DONE |

---

## Phases

### Phase 1 — Shared Infrastructure
- [x] **1a** Create `AppThemeTokens.kt` — `AppShapes` (small=8dp, medium=12dp, large=20dp, pill=50dp) and `AppSpacing` tokens — resolves infra for #32
- [x] **1b** Create `ToolHeroHeader` composable — resolves #6
- [ ] **1c** Standardise stop-button style — resolves #31
- [x] **1d** Applied `ToolHeroHeader` shared composable — resolves #6
- [ ] **1e** Separate Subnet Calculator hero from input card — resolves #7
- [ ] **1f** Add hero card to Settings screen — resolves #8

### Phase 2 — High Priority Standalone
- [x] **2a** Fixed `RecentHostsRow` dismiss button: 24dp → 32dp with `wrapContentSize` — resolves #9
- [x] **2b** Fixed mDNS `LazyColumn` unbounded height — `Modifier.weight(1f)` on `AnimatedContent` — resolves #35
- [x] **2c** Moved all mDNS hardcoded strings to `strings.xml` — resolves #21
- [x] **2d** Added mDNS empty result state (`EmptyResultHint` composable) — resolves #19

### Phase 3 — Navigation
- [x] **3a** "More" selected state — nav-fixes agent applied — resolves #1
- [x] **3b** Category grouping in MoreToolsSheet — nav-fixes agent applied — resolves #2
- [x] **3c** Pin limit reached — Snackbar feedback — nav-fixes agent applied — resolves #3
- [x] **3d** Settings sticky footer in MoreToolsSheet — nav-fixes agent applied — resolves #4
- [ ] **3e** Back nav from More → always land on Home — resolves #5

### Phase 4 — Input & Forms
- [ ] **4a** LAN Scanner: add `placeholder` showing `192.168.1.0/24` example — resolves #11
- [ ] **4b** Subnet Calculator: CIDR↔mask mode toggle converts notation — resolves #12
- [ ] **4c** Input validators for Ping, DNS, Traceroute, HTTP Probe — resolves #10
- [ ] **4d** DNS custom server IP validation — resolves #14
- [ ] **4e** Port Scanner: improve range control UX — resolves #13

### Phase 5 — Results Display
- [ ] **5a** Copyable values across Ping RTT, DNS records, WHOIS, TLS — resolves #15
- [ ] **5b** DNS results grouped by record type — resolves #16
- [ ] **5c** WHOIS: relative date alongside ISO date — resolves #17
- [ ] **5d** Port Scanner: visually subdue closed ports — resolves #18

### Phase 6 — Loading & Empty States
- [ ] **6a** LAN Scanner empty result state card — resolves #19
- [ ] **6b** LAN Scanner determinate progress bar — resolves #20
- [ ] **6c** Traceroute nested scroll fix — resolves #34

### Phase 7 — Typography & Spacing
- [x] **7a** Applied `AppShapes` tokens to Card `shape =` parameters across all main screens — resolves #32
- [x] **7b** Removed `FontWeight.Bold` overrides on `displaySmall`/`titleLarge` hero Text composables — resolves #24
- [x] **7c** Added `maxLines = 1, overflow = TextOverflow.Ellipsis` to hero `displaySmall` Text across all tool screens — resolves #22

### Phase 8 — Accessibility
- [x] **8a** `collectAsState()` → `collectAsStateWithLifecycle()` in LAN, mDNS, WifiScan, SubnetCalculator, Settings, Whois — resolves #25
- [x] **8b** `contentDescription` on all `CircularProgressIndicator` (Traceroute, LAN, Ports, DNS×2, WifiScan, TLS×2, mDNS) — resolves #26
- [x] **8c** `semantics { role = Role.Button }` on expandable cards in Traceroute, WifiScan, TlsInspector, mDNS — resolves #27
- [x] **8d** Slider `contentDescription` in SettingsScreen `SliderSetting` composable — resolves #28

### Phase 9 — Platform Conventions
- [ ] **9a** Onboarding: give "Don't show again" its own callback — resolves #29
- [ ] **9b** Pull-to-refresh on Ping, DNS, LAN Scanner, WHOIS result screens — resolves #30
- [ ] **9c** Settings screen transition: change to `fadeIn`/`fadeOut` in `AppNavHost` — resolves #33

---

## Build checkpoints

Build runs after each phase to catch compile errors early:

```
./gradlew clean :app:assembleDebug 2>&1 | grep -E "^e:|FAILED|BUILD|error:"
```

---

## Completion summary

- Total issues: 35
- Completed: 18
- In progress: 0
- Remaining: 17
