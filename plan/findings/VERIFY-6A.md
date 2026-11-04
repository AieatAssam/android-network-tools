# VERIFY-6A – phone-mode QA of WP30–WP33 (app shell, Ping, Traceroute, DNS, Port Scanner, LAN Scanner, Topology, Wi-Fi, Network Info)

Build: HEAD 019c089 (debug, `net.aieat.netswissknife.debug`), built with `./gradlew :app:assembleDebug --no-daemon --no-parallel` and installed with `adb install -r`. Tablet U8BBB20806201256, Android 8.0, 1200x1920 px.
Method: phone emulation by `wm density` only (brief). Evidence folder: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/v6a/` (PNG screenshots named `c1_*`, `c2_*`, `c3_*`, `c4_*`; notes in `v6a/notes.md`).
Targets used: 10.109.208.0/24 (LAN), 10.109.208.1 (gateway), 1.1.1.1, example.com, no-such-host.invalid. No Wake-on-LAN sent. One LAN scan run (full /24). No speed test run.
Device restored at the end: `wm density reset`, `wm size reset`, accelerometer_rotation 1, user_rotation 0, font_scale 1.0, night no, ACCESS_FINE_LOCATION revoked, app force-stopped. Verified: physical density 320, size 1200x1920, font 1.0, rotation auto, night no, no FATAL in logcat.

**Coverage was reduced by time.** Deep work was done in C1. C2, C3 and C4 were visual passes on a few screens only. Cells marked NT in section 5 were not run, with the reason given there. Nothing in NT cells is claimed as passing.

## 1. Configurations (get-config first line, as observed)

| Id | Setup | get-config (abbreviated) |
|---|---|---|
| C1 | density 480, portrait, font 1.0, light | `sw400dp-w400dp-h593dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-xxhdpi` |
| C2 | density 533 + font_scale 1.3, portrait, light | `sw360dp-w360dp-h531dp-normal-notlong-notround-lowdr-nowidecg-port-notnight-533dpi` |
| C3 | density 480, landscape (accelerometer 0, user_rotation 1) | `sw400dp-w640dp-h353dp-normal-notlong-notround-lowdr-nowidecg-land-notnight-xxhdpi` (screenshots are 1920x1200 px) |
| C4 | density 480, portrait, dark (`cmd uimode night yes`) | `sw400dp-w400dp-h593dp-normal-notlong-notround-lowdr-nowidecg-port-night-xxhdpi` |

The phone frame was confirmed visually in C1 (single column, edge to edge, no 640dp cap visible).

## 2. New findings (V6A-nn)

Severity: HIGH = blocks a clear reading or an action; MEDIUM = wrong or missing expected action; LOW = polish.

### V6A-01 BUG – Input field and Recent row scroll out of view when the keyboard opens (HIGH; C1 portrait, C3 landscape)
Repro: Fresh launch (`am force-stop`, start), open Ping (or DNS Lookup, Port Scanner). Tap the empty "Host or IP address" / "Domain or IP address" field at its normal position (y ~744 px in C1). The keyboard opens and the whole screen scrolls up so that the input field, the Recent row and the hero are all above the keyboard. Only the disabled primary button is visible (Start Ping / Look up / Scan Ports).
Expected (WP31 fix): field, error text, Recent row and primary button stay visible above the keyboard.
Actual: field and Recent are off screen (Ping, DNS: Recent row visible at top in DNS, field not). Primary button visible only because it is disabled and sits just above the keyboard. Same result in C3 landscape for Ping (field and Recent hidden, primary visible).
Screenshots: `c1_ping_ime_portrait2.png`, `c1_dns_ime.png`, `c1_ports_ime.png`, `c3_ping_ime.png` (full paths under the v6a folder above).
Suggested fix: add `imePadding()` to the scrolling container and `bringIntoViewRequester` on the input field, or request bring-into-view for the field (not for the button) on focus. Check the same on every tool with a main field. Add an instrumented test that taps the field and asserts the field bounds are above the IME top.

### V6A-02 UX – Primary action hidden under the keyboard on Traceroute and Network Topology (MEDIUM; C1 portrait)
Repro: Traceroute: tap field, keyboard opens. Field and Recent row are visible, but "Start Trace" is under the keyboard. Topology: tap "Target IP", keyboard opens; field visible, "Discover Topology" under the keyboard.
Expected: primary button visible above the keyboard (WP31 requirement). Actual: the user must dismiss the keyboard to reach it.
Screenshots: `c1_tr_ime_portrait.png`, `c1_topo_ime.png`.
Suggested fix: same as V6A-01 (`imePadding` plus scroll so the focused field and the button fit above the keyboard, or pin the primary button to the bottom above IME).

### V6A-03 UX – Traceroute error card: Clear and Retry pills touch (LOW; C1)
Repro: Traceroute to `no-such-host.invalid`, Start Trace. Error card "Trace failed" shows "Clear" and "Retry" with no gap; the two pills overlap at their edges and have different widths and styles.
Expected: consistent error-card actions with 8–12 dp gap (compare Ping error card, which has Retry and Clear separated).
Screenshot: `c1_tr_invalid.png`.
Suggested fix: use a Row with `Arrangement.spacedBy(12.dp)` and the same button style for both, or Retry as the filled button and Clear as text.

### V6A-04 UX – Error wording differs between tools for the same failure (MEDIUM; C1)
Repro: unresolvable host `no-such-host.invalid`.
- Ping: "Ping failed. Could not find an address for no-such-host.invalid. Check the name, then try again." (PHA-11 fixed, good).
- Traceroute: "Trace failed. Unable to resolve host "no-such-host.invalid": No address associated with hostname" (raw resolver text).
Expected: the same plain, actionable wording on every tool.
Screenshots: `c1_ping_invalid_r.png`, `c1_tr_invalid.png`.
Suggested fix: map `UnknownHostException` to one shared message string in strings.xml, used by Ping, Traceroute, Port Scanner and DNS.

### V6A-05 BUG – Topology discovery error text is clipped and has no retry inside the card (MEDIUM; C1)
Repro: Network Topology, target 10.109.208.1 (community default, v2c), Discover Topology. The "Discovery Failed" card message is cut off after three lines ("…verify the host, UDP port, firewall, and" then a half-line "community string"). No Retry action inside the card; the only retry is the Discover button above it.
Expected: full message visible (card grows) and a Retry action in the card (CLAUDE.md error-state requirement).
Screenshot: `c1_topo_done2.png`.
Suggested fix: remove the fixed height on the error card (use `wrapContentHeight`), and add "Retry" next to the message.

### V6A-06 UX – Topology config card clips the Community String field (LOW, UNVERIFIED repro after full settle)
Repro: More → Network Topology, SNMP Configuration expanded at first open. The Community String outlined field is cut at the card bottom edge; a small green dot sits under the Discover button (seen 2 s after opening).
Note: this was seen once, 2 s after the screen opened. It was not reproduced after the card was collapsed and reopened. Mark UNVERIFIED until a repeat is captured.
Screenshot: `c1_topo_idle.png`.

### V6A-07 DUP – Traceroute: target repeated in config title and results (LOW; C1)
Repro: Run Traceroute to 1.1.1.1. The collapsed config header shows "1.1.1.1" (title) and "30 hops · 1 probe per hop · 3000 ms timeout", and the Trace Results card shows "Resolved IP: 1.1.1.1".
Expected: show the resolved IP only when it differs from the target (same rule as PHA-18 fix).
Screenshot: `c1_tr_summary.png`.

### V6A-08 DUP – DNS NXDOMAIN: the host is repeated three times (LOW; C1)
Repro: DNS Lookup `no-such-host.invalid`, Look up. The string appears in the field, in the Recent chip, and as the result card title "no-such-host.invalid · RCODE: NXDOMAIN".
Expected: result title may stay; the field value and Recent chip are the input, not a second summary. Low impact.
Screenshot: `c1_dns_nx.png`.

### V6A-09 UX – DNS record section label is misplaced (LOW; C1)
Repro: DNS Lookup `example.com`. The collapsible "Additional" header sits above the answer records (A 104.20.23.154, A 172.66.147.243). The answer records are visually under "Additional".
Expected: label the answer section "Answer" (or "Records") and keep "Additional" for the Additional section, below the answers.
Screenshot: `c1_dns_records.png`.

### V6A-10 UX – LAN wording: "devices" on screen, "Confirmed hosts" in Copy text (LOW; C1)
Repro: After the LAN scan, the summary headline reads "53 devices found". The uncertain sheet says "They are not counted as devices". Copy produces "LAN scan – 10.109.208.0/24 / Confirmed hosts: 53 / 254".
Expected: one term in the UI and in the exported text.
Screenshots: `c1_lan_run.png`, `c1_lan_uncertain.png`; clipboard text read via `clip.py`.

### V6A-11 DUP – LAN: "201" in summary button and in sheet title (LOW; C1)
Repro: Summary button "201 uncertain probes" opens a sheet titled "201 uncertain probes". Acceptable as a label-to-title link, but the count is repeated. Low.
Screenshot: `c1_lan_uncertain.png`.

### V6A-12 UX – Disabled primary buttons give no reason (LOW; C1, C4)
Repro: Traceroute (empty field, "Start Trace" disabled), Topology ("Discover Topology" disabled with empty target). Ping also disabled without reason (PHA-12 family).
Expected: helper text under the disabled button, or hint in the field.
Screenshots: `c1_tr_ime_portrait.png` (Start Trace disabled), `c1_topo_idle.png`.

### V6A-13 UX – Wi-Fi band shown twice on the first screen (LOW; C1)
Repro: Wi-Fi Scanner hero shows "2.4 GHz · 5 GHz" and the tab row directly under the Spectrum/list header shows "2.4 GHz" and "5 GHz".
Expected: keep the band switch in one place. Low impact.
Screenshots: `c1_wifi_idle.png`, `c1_wifi_list.png`.

## 3. Previously reported findings (PHA-nn) – status with evidence

| PHA | Status | Evidence |
|---|---|---|
| PHA-01 LAN summary repeats subnet/count/scanned | FIXED | Summary = "Scan Complete / 53 devices found / 10.109.208.0/24 · 254 addresses · 30.8 s / 201 uncertain probes / Re-scan, Clear, Copy, Share". Config card collapsed to "Timeout: 1000 ms · Concurrency: 50". Uncertain probes moved to a sheet (`c1_lan_run.png`, `c1_lan_uncertain.png`). Residual wording issue is V6A-10. |
| PHA-02 Map repeats the table; dead controls | STILL PRESENT (duplicate); interaction FIXED | Map nodes are tappable: tapping node 197 scrolls the list to `10.109.208.197`. "+29 more…" expands inline to all 53. But map labels still repeat the names in the device list (e.g. HUAWEI_MediaPad_T5-85cbe3, hp-t740). Filters/search do not act on the map (not re-checked). (`c1_lan_run2.png`, `c1_lan_more.png`, `c1_lan_nodetap.png`) |
| PHA-03 "0 ms" latency for hosts never pinged | NOT RE-TESTED | Only visible rows checked (`5 ms`, `6 ms`); no zero-latency host was opened. Full check not done. |
| PHA-04 LAN search with no match is blank | FIXED | Search "zzz": "Devices 0 of 53", card "No devices match your search", "Show all devices" (`c1_lan_nomatch2.png`). |
| PHA-05 LAN sort and filter chips look alike | FIXED | "Sort by Latency ▾" label and dropdown; filter chips All / Gateway / Has Ports separate (`c1_lan_list.png`). |
| PHA-06 Empty-state copy contradicts filled input | NOT RE-TESTED | Ping/LAN empty-state card text not compared with a filled field in this round. |
| PHA-07 LAN "hosts" for subnets | FIXED | Recent controls now "Clear recent subnets" / "Remove recent subnet" (`c1_lan_idle.png`). |
| PHA-08 LAN expanded host repeats header | NOT RE-TESTED | Host row not expanded this round. |
| PHA-09 LAN stat tiles orphan "/24" at phone-360 + font 1.3 | NOT RE-TESTED (n/a) | Stat tiles removed by PHA-01 fix; the LAN C2 pass was not run. |
| PHA-10 Wake-on-LAN missing from More sheet | STILL PRESENT | More sheet: Diagnostics (Ping, Traceroute, Port Scanner, DNS, Network Info), Network (LAN Scanner, Wi-Fi Scanner, Network Topology, mDNS Browser, MAC Lookup), Security (TLS Inspector, WHOIS Lookup, HTTP Probe), Utilities (Subnet Calculator only). Wake-on-LAN and Speed Test are on the Home grid only (`c1_more.png`, `c1_more_mid.png`, `c1_more_end.png`). |
| PHA-11 Unresolvable host shown as 100% loss | FIXED | "Ping failed" error card with message and Retry / Clear (`c1_ping_invalid_r.png`). |
| PHA-12 Stale-result banner under nav bar; disabled button no reason | NOT RE-TESTED | Not reproduced this round. Disabled-button reason still absent (see V6A-12). |
| PHA-13 Host and min/avg/max repeated (Ping) | NOT RE-TESTED | Ping stats were checked (`c1_ping_ok1.png`); the repeat of host in Statistics subtitle was not checked. |
| PHA-14 Count colours read as errors | FIXED on Ping; STILL PRESENT on Port Scanner | Ping Sent/Received/Loss tiles are neutral. Port Scanner "19 Filtered" tile is still pink (`c1_ports_run2.png`). |
| PHA-15 Traceroute number/unit spacing; mixed header actions | PARTIAL | "1.4 s" spacing is correct now. Copy and Share now have icon plus label, Clear is text (`c1_tr_summary.png`). Pattern still differs from LAN's text-only row. |
| PHA-16 Traceroute hop list far below | PARTIAL | Config collapses to "Show settings" after a run (`c1_tr_summary.png`). Hops start below the summary and the Hop Details / Raw Output tabs. Journey Intelligence card not re-checked. |
| PHA-17 DNS record type repeated per record | FIXED | Record row shows one "A" badge, name, TTL and value (`c1_dns_records.png`). Section label issue is V6A-09. |
| PHA-18 Port Scanner Host and Resolved IP identical; OPEN badge | FIXED | "Host: 10.109.208.1" only; no OPEN badge on open-port rows (`c1_ports_run2.png`). |
| PHA-19 Topology: no Retry; config summary lost on collapse | PARTIAL | Collapsed header shows "10.109.208.1 · v2c" (FIXED). No Retry in the error card (STILL PRESENT); message clipped (V6A-05). |
| PHA-20 Wi-Fi scan icon-only; duplicate counts and time | PARTIAL | Scan is now a labelled button; "Updated 0 seconds ago" shows the time once (FIXED). Spectrum labels still sit on the curves at channels 1, 6, 11 and have low contrast on the light card (STILL PRESENT) (`c1_wifi_list.png`). |
| PHA-21 Home hero fills viewport in landscape; tools below fold | STILL PRESENT at C2 (phone-360 + font 1.3); landscape NOT RE-TESTED | C2: first tool card starts at about 56% of screen height (`c2_home.png`). Landscape Home was not captured. |
| PHA-22 Uneven tile heights in grid | NOT RE-TESTED | Not measured this round. |
| PHA-23 Landscape tool heroes use about 28% height | FIXED on Ping (C3); Port Scanner C3 NOT RE-TESTED | Ping landscape hero is one line (`c3_ping.png`). |

## 4. Things that worked well (this round)
- Ping: unresolvable host now a proper error state with Retry. 1.1.1.1 run: 10 sent, 10 received, min 12 / avg 18.7 / max 25 ms, jitter 3.6 ms, RTT chart. Dark mode contrast good.
- Traceroute: 11 hops to 1.1.1.1, "Reached: Yes", collapsing settings after the run, Copy writes a readable text block to the clipboard.
- DNS: example.com NOERROR, 2 A records, TTL per record, Copy writes plain text ("DNS A – example.com / Server: System DNS / Query time: 50ms …"). NXDOMAIN shown as a result (RCODE) rather than an error card.
- Port Scanner: gateway result is clear: Open / Closed / Filtered / Scanned tiles, host line once, SSH 22 with banner and latency.
- LAN Scanner: the summary now states the result once. Map node tap works. Uncertain probes in a sheet. Empty search state is explicit.
- Wi-Fi: location prompt, labelled Scan action, Best Channel card, sort chips with clear labels.
- Network Info: clean cards; copy control beside the network name; values consistent with the Wi-Fi tool.
- Dark mode (C4) on Home and Ping: no contrast problems seen.
- The More sheet (C1 and C2): tool groups with descriptions and pin toggles; C2 text wraps without clipping.

## 5. Coverage (tool x config)

Legend: P = pass (checked, no finding). Vn / V6A-nn / PHA-nn = finding ID. NT = not tested, reason given. "n/a" = screen has no such control.

| Tool | C1 portrait 400dp (deep) | C2 360dp + font 1.3 | C3 landscape 400dp | C4 dark | Error state | IME portrait (main field) | IME landscape | Back restore | Copy / Share |
|---|---|---|---|---|---|---|---|---|---|
| Home (grid, hero, nav) | P (PHA-22 NT) | PHA-21 STILL PRESENT | NT (landscape Home not captured; PHA-21) | P (idle) | n/a | n/a | NT (not captured) | NT | n/a |
| More sheet | PHA-10 STILL PRESENT | P (wraps, no clipping) | NT (time) | NT (time) | n/a | n/a | NT | NT | n/a |
| Ping | P (1.1.1.1 run, stats, chart) | NT (time; C2 pass not run) | P (hero compact, PHA-23 FIXED); IME see next col | P (idle; rich state not restored) | P (PHA-11 FIXED, Retry/Clear) | V6A-01 | V6A-01 | NT (Back-nav not re-tested for Ping) | NT (CSV icon present, not exercised) |
| Traceroute | P (11 hops, Reached) | NT (time) | NT (time) | NT (time) | V6A-03, V6A-04 | V6A-02 (button hidden) | NT (time) | NT | P (Copy → clipboard text read); Share NT |
| DNS Lookup | P (example.com NOERROR; PHA-17 FIXED) | NT (time) | NT (time) | NT (time) | P (NXDOMAIN result card; V6A-08) | V6A-01 | NT (time) | NT | P (Copy → clipboard text read); Share NT |
| Port Scanner | P (gateway 4 open; PHA-18 FIXED) | NT (time) | NT (PHA-23 port landscape not captured) | NT (time) | NT (closed port gave 0 open, not an error; invalid host not run) | V6A-01 | NT (time) | NT | NT (Copy/Share present, not exercised) |
| LAN Scanner | P (full scan 53/254, 30.8 s; PHA-01 FIXED) | NT (C2 pass not run; PHA-09 n/a) | NT (time) | NT (time) | P (no-match state PHA-04 FIXED; network failure not produced) | NT (main field is subnet; not tested in this round) | NT (time) | NT | P (Copy → clipboard text read); Share NT |
| Network Topology | P (idle, config, SNMP timeout) | NT (time) | NT (time) | NT (time) | V6A-05 (clipped, no retry) | V6A-02 (button hidden, field visible) | NT (time) | NT | NT (no copy control exercised) |
| Wi-Fi Scanner | P (after location allow; 19 APs; spectrum) | NT (time) | NT (time) | NT (time) | NT (location-deny path not run) | n/a (search field only; NT) | NT | NT | NT (Copy/Share present, not exercised) |
| Network Info | P (cards, copy icon) | NT (time) | NT (time) | NT (time) | NT (no error state producible in time) | n/a (no input field) | n/a | NT | NT (copy icon present, not exercised) |
| App shell: bottom nav, pins | P (nav, highlight, pins toggle) | NT | NT | NT | n/a | n/a | n/a | NT | n/a |

Back-navigation state restore was not exercised as a dedicated round in this group (time). Observed only: BACK with no keyboard open leaves a tool to Home (expected Android behaviour); BACK with the keyboard open only hides the keyboard.

## 6. Duplication audit (quotes)

- LAN summary: "53 devices found" then "Scan Complete" (status line) and "201 uncertain probes" with the same 201 in the sheet title. Subnet and 254 appear once in the summary. Map labels repeat device names from the list below (PHA-02).
- Traceroute: "1.1.1.1" in the collapsed config header and "Resolved IP: 1.1.1.1" in results (V6A-07).
- DNS NXDOMAIN: "no-such-host.invalid" in the field, Recent chip and result title (V6A-08).
- DNS example.com: "example.com" in the field, Recent chip, result title and each record row name (record rows are expected).
- Port Scanner: "Open" in the tile and "Open Ports" header; no repeated host now.
- Wi-Fi: "2.4 GHz · 5 GHz" in hero and "2.4 GHz" / "5 GHz" tabs (V6A-13).
- Ping: "1.1.1.1" in the Recent chip, field and results (only once in the results card now).

## 7. Notes on method and limits
- Field typing: the IME inserts a space after "." on this keyboard, so host names were typed by key events and fixed with DPAD moves. The validation message "Hostname or IP cannot contain spaces" appeared for the broken value, which is good behaviour.
- The Wi-Fi location dialog: tapping the text "ALLOW" in the message hit nothing; the button at (828,1076) was needed.
- Clipboard was read with `service call clipboard` and decoded with `v6a/clip.py`; text read was limited to Copy output for Traceroute, DNS and LAN.
- No secrets were pasted or read. The Wi-Fi scan list was viewed for layout only.
