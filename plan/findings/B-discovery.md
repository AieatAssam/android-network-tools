# QA findings: B-discovery (Port Scanner, LAN Scanner, Wi-Fi Scanner, Network Topology, Settings, process death)

- Device: Android 8.0 tablet, 1200x1920 px, 320 dpi (2 px per dp), serial U8BBB20806201256, Wi-Fi 10.109.208.146/24, `net.aieat.netswissknife.debug` (1.0.0-debug) from main.
- Screenshots: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/qaB/` (file names below are relative to that directory).
- Method: adb only (screencap, uiautomator dump, input). Port results checked with a host-side `python3 -I` TCP connect to 1.1.1.1 (independent of the device). LAN results checked against the device's `ip neigh` table. Wi-Fi SSID checked against `dumpsys wifi`.
- Network touched: 1.1.1.1, 8.8.8.8, example.com, the local /24 (10.109.208.0/24, including the gateway 10.109.208.1). No speed tests.
- Device changes: rotation set back (`accelerometer_rotation` set to 1, `user_rotation` 0). The original `accelerometer_rotation` value was not recorded before I changed it, so 1 is an assumption. `font_scale` 1.0. Night mode off. Location permission revoked for ACCESS_FINE and ACCESS_COARSE after the Wi-Fi test. `am kill-all` was used once to kill the app (PD-01), which also stopped other background apps. A stray tap opened the Weather app's permission dialog; I dismissed it with Back and enabled nothing.

## Summary

- Four tools plus Settings and process death. No AndroidRuntime errors in any run.
- Port Scanner results are correct: 1.1.1.1 gave 5 open (53, 80, 443, 8080, 8443) and 18 filtered. The host-side connect check matched on all 16 ports tested.
- Highest-impact defects: the Topology "Discover Topology" button is inside an internally scrolling card and nothing points to it (TOPO-01). Custom port range accepts 20,000 ports and only fails on tap (PORTS-01). A user-stopped or rotation-interrupted port scan shows as a complete scan (PORTS-03, PORTS-04). Wi-Fi "best channel" can recommend a channel with no AP of its own (WIFI-06).
- Generic validation message "Hostname or IP cannot contain spaces" for every invalid host (PORTS-02, TOPO-03 note).
- Layout: no tool uses the tablet width beyond stretched single-column forms (GAP-01).

## Findings

### Port Scanner

**PORTS-01** | BUG | high
- Repro: Ports > preset Custom range > End port: clear, type `20000`, close the keyboard. Scan Ports stays enabled. Tap it.
- Expected: over-limit range is validated inline (README: Custom range "up to 10,000 ports") and Scan is disabled, as the inverted range already is.
- Actual: "20000 ports selected", no warning, button enabled. Tapping shows a "Scan failed" card: "Custom range must not exceed 10 000 ports (got 20000)". The range slider also reaches 26825 by touch.
- Contrast: an inverted range (start 500 > end 100) shows "Start port must be ≤ end port" inline and the Scan button node is `enabled=false`. Verified in 145-inverted.png and 146.xml.
- Screenshots: 21-range-20000.png, 22-scanning-20000.png, 23-error-scrolled.png, 31-landscape-scrolled.png, 145-inverted.png
- Fix: extend `customRangeValid` (PortsScreen.kt ~777) to check the 10,000 cap and show supportingText on End port. Cap the slider. Use the field error, not the "Scan failed" card, for validation.

**PORTS-02** | BUG | medium
- Repro: type `bad!!` (no spaces) in Host or IP address; press Enter or try Scan.
- Expected: a message that matches the problem, e.g. "Enter a valid hostname or IP address".
- Actual: "Hostname or IP cannot contain spaces" for any invalid input. The string is `error_invalid_host` (strings.xml line 1515), reused by Ping, Traceroute, TLS, Topology and Ports. Topology shows the same text for `garbage!` (101-topo-invalid.png).
- Also: Scan Ports is disabled with no reason, and the previous 8.8.8.8 results stay visible below the invalid `bad!!` input.
- Screenshots: 12-ports-badchars.png, 14-ports-badchars-scan.png, 101-topo-invalid.png
- Fix: split the validator's failure into reasons (spaces, illegal characters, empty label, too long) and map each to a string. Clear or dim old results when the input changes.

**PORTS-03** | BUG | medium
- Repro: Custom range 1-1100, Scan Ports, after about 5 s tap Stop Scan.
- Expected: results labelled as stopped or partial, e.g. "Stopped at 353 of 1100 ports", as LAN and Traceroute do.
- Actual: "Scan Results" shows Open 3 / Closed 0 / Filtered 350 / Scanned 353 and nothing says the scan stopped. Only the DEADLINE completion renders a banner (PortsScreen.kt ~334). USER_STOPPED is not rendered.
- Screenshots: 25-scanning.png, 26-stopped.png, 27-stopped-scrolled.png
- Fix: render a banner for `Completion.USER_STOPPED` in the same place as the DEADLINE banner.

**PORTS-04** | BUG | medium
- Repro: Custom range 1-1100 on 10.109.208.1, Scan Ports, rotate to landscape at about 300/1100 (`settings put system user_rotation 1`).
- Expected: the scan continues across rotation, or it stops with a visible notice.
- Actual: the scan ends at 303/1100 with no "Scanning" card and no notice, and the partial counts show as a normal result. Host and range survive. Cause not confirmed: the activity was recreated; UNVERIFIED whether the ViewModel cancels the scan on recreate.
- Screenshots: 29-landscape-scanning.png, 33-landscape-results.png
- Fix: keep the scan job in a scope that survives configuration change. If it is cancelled, show the PORTS-03 banner.

**PORTS-05** | BUG | low
- Repro: scan 8.8.8.8, then look at the Recent row.
- Expected: the newest host is visible first.
- Actual: the row keeps the horizontal scroll offset from before. The newest chip is hidden under the "Recent" label, leaving only its X. There is no overflow indicator and the swipe is unreliable inside the vertical scroll. Stored order is correct: `recent_ports_hosts = 8.8.8.8|1.1.1.1|example.com|10.109.208.1`.
- Screenshots: 09b-recents-start.png, 10-recents.png
- Fix: `scrollToItem(0)` when the list changes, and add an edge fade.

**PORTS-06** | UX | low
- Repro: scan 1.1.1.1 (Common), then Show all (18 filtered), read row 1.
- Actual: unknown ports show generic labels from `WellKnownPorts.getServiceName` ("Well-known" for 1-1023, "Registered", "Dynamic/Private") as service names. Port 1 reads "Well-known".
- Screenshot: 37-all-results.png
- Fix: return null for unknown ports and show "Unknown" or an em dash.

**PORTS-07** | UX | low
- Repro: info icon > Key parameters.
- Actual: help text names presets "(Common, Web, All)". There is no "All" preset. Actual list: Common services, Well-known (1-1024), Web services, Databases, Mail services, Remote access, Custom range.
- Screenshot: 42-help.png

**PORTS-08** | GAP (cross-tool) | medium
- Observed: Back from a tool discards its state. In Ports, Back cleared the host field and results. Rail navigation kept them. After process death (PD-01) the host and recents return, but the results do not. The result is a filled field with an empty state ("Enter a host to scan") and no hint why.
- Also: on LAN Scanner, Back with an expanded host row open returns to Home, and the scan results are lost.
- Fix: persist the last result summary (SavedStateHandle or DataStore) per tool, and offer "Re-run last scan". Keep state on Back as well as rail navigation.

**PORTS-09** | UX | low
- Repro: portrait, focus host field, keyboard up.
- Actual: the IME covers Scan Ports and most of the form. Enter works but is not discoverable.
- Screenshots: 02-ports-typed.png, 03-ports-imeaction.png
- Fix: `imePadding()` or scroll the focused field and button into view. Set the IME action label to "Scan".

**PORTS-10** | UX | low
- Actual: scrolled content (toggle, Scan Ports) draws under the status bar clock with no scrim.
- Screenshot: 27-stopped-scrolled.png

**PORTS-11** | UX | low (UNVERIFIED)
- Observed: after tapping Copy report, a screenshot one second later showed no confirmation. Clipboard content not verified.
- Screenshot: 39-copied.png
- Fix: snackbar "Report copied".

**PORTS-12** | UX | low (UNVERIFIED contrast)
- Observed: All Results filtered rows use light grey text and a pale FILTERED tag. Not measured; it looks below AA.
- Screenshot: 37-all-results.png

**PD-01** (process death) | GAP | low: see the process death section.

### LAN Scanner

**LAN-01** | UX | low (reframed; was BUG)
- Correction: the host detail is an inline expansion in the list, not a bottom sheet. 58-sheet.png shows the row expanded in place with "Device Details", "Wake device", "Ping host", "Open Ports". There is no drag handle. The earlier claim that Back "dismisses a sheet" was wrong.
- Observed: pressing Back while a row is expanded leaves LAN Scanner for Home and discards the results. This is normal navigation Back, so the real issue is the results being lost (PORTS-08).
- Screenshots: 58-sheet.png (expanded row), 124.png (Home after Back)

**LAN-02** | UX | low
- Observed: the green "Gateway" label on 10.109.208.1 runs into "Latency 23 ms" on the same row.
- Screenshots: 62-lan-hosts.png, 63-lan-header.png

**LAN-03** | UX | low
- Repro: scan a /24, tap "Show uncertain probes".
- Actual: rows show raw Java exception text, e.g. "10.109.208.2: TCP probe timed out (SocketTimeoutException: failed to connect to /10.109.208.2 (port 80) from /10.109.208.146 (port 34636) after 400ms)".
- Screenshots: 52-uncertain-on.png, 53-uncertain-list.png (raw text), 47-uncertain.png (inline row layout)
- Fix: map to readable text, e.g. "No answer on TCP 80 within 400 ms".

**LAN-04** | BUG (accuracy, UNVERIFIED) | medium
- Observed: the device ARP table (`ip neigh`, STALE entries) listed 63 IPs. Seven of them were not in the confirmed list of the same run: 10.109.208.3, .111, .116, .134, .186, .187, .205. Confirmed counts across three runs on the same network were 57, 56 and 55.
- Not confirmed: whether those seven are in the 198 uncertain probes. Only 100 rows were captured.
- Note: 10.109.208.146 (the tablet) is listed by the app and is not in its own ARP table, which is expected.
- Fix: include the readable ARP entries (`/proc/net/arp` works on Android 8) in the presence evidence, or state in the UI that STALE ARP is not counted.

**LAN-05** | UX | low
- Repro: at the top of the results, swipe down with the finger (pull gesture).
- Actual: a scan restarts (50/254 and counting), the previous results are cleared, and no confirmation is shown. A spinner sits over the header title during the restart.
- Screenshots: 62-lan-hosts.png, 63-lan-header.png, 65-lan-mid.png
- Fix: disable pull-to-refresh, or confirm before it discards results.

**LAN-06** | UX | low
- Observed: a "+35" / "+36 more" chip sits over the Android navigation bar at the bottom of the Network Map.
- Screenshots: 51-uncertain-on.png, 136.png

**LAN-07** | VERIFIED (closed; was UNVERIFIED) | pass
- Search, filters and sort exist and work. They sit below the Network Map and appear after scrolling up with upward swipes.
- Checked: search "proxmox" with Gateway filter gives "Results (0 of 57)" with "No devices match your search and filter" (correct, gateway is Intel). Search "proxmox" with All gives "Results (6 of 57)".
- Filter chips: All, Gateway, Has Ports. Sort chips: IP, Latency, Hostname, Vendor, Port count.
- Screenshots: 134.png (controls), 136.png (search + Gateway, empty state)
- Not checked: Has Ports and each sort order.

Observed as correct: /24 scan (254 hosts) finished in 29-31 s at 1000 ms and concurrency 50. Subnet pre-filled to 10.109.208.0/24 from the device's Wi-Fi address. Vendor names matched the OUI (Raspberry Pi, Espressif, Proxmox, Belkin, Google). Host list sorted by latency ascending. Host detail shows MAC, vendor, latency, role, open ports and actions.

Not tested: Share results, Clear, Re-scan, Edit config, Wake device, Ping host, Probe HTTP, Scan ports hand-off, rotation, dark mode, timeout and concurrency sliders, invalid subnet input.

### Wi-Fi Scanner

**WIFI-01** | UX | medium
- Repro: 2.4 GHz tab with 5 or more SSIDs.
- Actual: SSID labels on the spectrum overlap at channels 1 and 11 ("FiftyShades", "Brilliant", "<Hidden Ne" printed over each other). Several networks cannot be read.
- Screenshots: 77-wifi-cards.png, 78-expanded.png, 84-search-nomatch.png, 88-nomatch-list.png
- Fix: stagger labels by RSSI, show labels for the strongest N only, and let the list act as the legend.

**WIFI-02** | BUG | low
- Repro: type `ZZZnomatch` in "Search SSID, BSSID or vendor".
- Expected: an empty-state message.
- Actual: header "0 networks", empty list, no message. The spectrum still shows all networks; search does not filter it.
- Screenshot: 88-nomatch-list.png

**WIFI-03** | UX | low
- Observed: the location-permission gate is a card floating mid-screen with no tool header or title.
- Screenshot: 71-wifi-denied.png

**WIFI-04** | UX | low
- Observed: the 30 s auto-refresh re-sorts the list and changes the count (5, then 4) while the user is tapping. My tap expanded a different card than the one I aimed at.
- Fix: key expansion by SSID+security; hold reorder while a card is touched, or animate reorders.

**WIFI-05** | VERIFIED (corrected) | pass
- The connected network "Brilliant Radiator!" matches `dumpsys wifi` (mWifiInfo SSID "Brilliant Radiator!", 2417 MHz), and its card shows the Connected chip.
- The detail sheet I opened in 79-bssid-detail.png was **FiftyShadesOfPing** (BSSID ee:9f:80:77:61:60), not the connected network. It showed 2.4 GHz, channel 2 (2417 MHz), 20 MHz, Wi-Fi 4 (802.11n), max speed 600 Mbps. The sheet has a drag handle, and Back closes it while staying on Wi-Fi Scanner (80-after-back-wifi.png).
- Not checked: the connected network's own detail sheet.

**WIFI-06** | BUG (accuracy) | medium
- Code path: `WifiScanResult.bestChannel24GHz` takes the minimum over {1, 6, 11} of `channels.find { it.channel == ch }?.congestionScore ?: 0f` (WifiScanResult.kt ~52). `channels` is built only from channels that have an AP of their own (WifiChannelAnalyzer groups by channel). A channel with no own AP is absent and scores 0, so it is recommended even when strong APs overlap it. The overlap sum (`overlapping24GHzChannels`, ±4 channels) is only applied to channels that already have an entry.
- Observed: the app recommended "Channel 1" (78-expanded.png) while an AP at -50 dBm sat on channel 2, which overlaps channel 1 under the app's own ±4 rule. The recommendation also flipped between 11 and 1 across refreshes (75.png, 78-expanded.png).
- Not verified: whether channel 1 had its own AP in that scan. The code path explains the observation.
- Fix: compute the score for all of {1, 6, 11} (missing = its overlap sum), not `?: 0f`.

**WIFI-07** | BUG (accuracy) | low
- Observed: the BSSID detail shows "Max Speed 600 Mbps" for a 20 MHz Wi-Fi 4 AP on channel 2. The code returns the fixed label for the standard (`WifiStandard.WIFI_4.maxSpeedLabel = "600 Mbps"`, WifiStandard.kt ~18). 600 Mbps needs 40 MHz and four streams. For 20 MHz, four streams with short GI top out near 289 Mbps.
- Screenshot: 79-bssid-detail.png
- Fix: derive max speed from width and stream count reported by the scan, or label it "Standard max" and show the 20 MHz figure.

Not tested: 5 GHz tab, Refresh button and throttling, Sort chips, Copy and Share, rotation, dark mode, Help, Off / 15 s / 60 s auto-refresh.

### Network Topology

**TOPO-01** | BUG (UX-critical) | high
- Repro: More > Network Topology > type a valid seed IP (10.109.208.1). Press Enter, or look for a start button.
- Expected: one obvious primary action.
- Actual: the empty state reads "Discover your network topology / Enter a seed IP and configure SNMP settings above" and has no action. The "Discover Topology" button and the "Query advertised LLDP/CDP neighbors" toggle are inside the SNMP Configuration card, which is capped near 40% of screen height and scrolls internally, with no visible hint. Enter does not start discovery. When the keyboard closes the card scrolls back and hides the button again.
- Screenshots: 100-topology-idle.png, 104-topo-ip.png, 105-topo-after-enter.png, 106-topo-card-scroll.png, 109.png
- Fix: move the primary action out of the scrolling card (sticky bottom bar). Run discovery on IME Done. Remove the 40% cap or keep the button visible.

**TOPO-02** | UX | low
- Observed: Target IP has a Clear (X) action but no leading icon. CLAUDE.md's per-tool checklist asks for an icon plus clear button. Port Scanner and LAN Scanner have a leading icon.
- Screenshot: 100-topology-idle.png

**TOPO-03** | VERIFIED | pass
- Failed discovery shows a clear error card: "SNMP v2c GET 1.3.6.1.2.1.1.1.0 to 10.109.208.1:161 failed: no response within 3000 ms; verify the host, UDP port, firewall, and community string", with a Retry button.
- While running: "Scanning… 0 nodes found", "Cancel discovery", "No nodes discovered yet".
- Screenshots: 111-discovering.png, 112-discover-40s.png
- Note: the invalid-host text here repeats PORTS-02.

**TOPO-04** | UNVERIFIED | low
- The community string is pre-filled (6 masked characters) on first launch. Its value was not revealed.

Not tested (no SNMP device on this LAN): positive discovery, topology canvas (pan, zoom, node sheet), LLDP/CDP neighbours, cancel mid-discovery, v1 and v3, Max Hops beyond default, show-password, rotation, dark mode.

### Settings and About

- Reached via More > All Tools sheet > Settings.
- Items: Theme (System / Light / Dark), default ping count (10), timeout (2000 ms), concurrency (50), Wi-Fi auto-refresh (30 s), "Clear all recent hosts", "Reset welcome guide", Version `1.0.0-debug`, Speed Test attribution (Cloudflare), third-party licences (dnsjava, SNMP4J, icmpenguin, Dagger Hilt, Kotlin Coroutines, AndroidX Compose, DataStore).
- Screenshots: 91-settings.png, 92.png
- No separate About screen; version and attribution sit at the bottom of Settings. Acceptable for a debug build.
- Dark mode (system night mode) switches the app to dark. Port Scanner checked: readable, tokens applied, warnings legible. Screenshot: 94-dark-ports.png.
- Font scale 1.3 on Port Scanner: the subtitle wraps to two lines, Recent chips clip, and the layout still works. Screenshot: 95-font13-ports.png.
- SETTINGS-01 | UX | low: "Debug Logs" appears in the More sheet. Confirm it is debug-only in release.

### Process death and restore (PD-01)

- `adb shell am kill` returned 0 but did not kill the app while its task was in Recents or Home (pid unchanged for about 30 s). `adb shell am kill-all` did kill it.
- After relaunch from Recents: host text restored (1.1.1.1), Recent chips restored (DataStore), preset restored (Common services). Concurrency reset from 40 to 50 (UI slider value not persisted). Scan results gone: the empty state "Enter a host to scan" next to a filled field.
- Screenshots: 98-recents-after-kill.png, 99-restored.png
- Recents also shows an `androidx.test...EmptyActivity` task from `net.aieat.netswissknife.debug.test`. That is the test harness, not a user-facing bug.

### Cross-tool and layout (GAP-01)

- **GAP-01** | GAP | medium: the app uses phone-style single columns on a 600 dp-wide tablet. In landscape (1920 px, 960 dp) Port Scanner fields stretch to about 775 dp, and results stack below the form. No tool uses master/detail or a two-pane layout. Screenshots: 29-landscape-scanning.png, 30-landscape-after-ime.png, 31-landscape-scrolled.png.
- **Touch targets**: density is 320 dpi (2 px per dp). The clickable recents chip, Help and LAN row regions are 96 px (48 dp), which meets the minimum. The inner X icon is 28 px (14 dp) but sits inside a 48 dp target.
- **Rotation**: the keyboard reappeared after rotation with the host field focused (29-landscape-scanning.png).

## Per-tool checklist

| Tool | Tested | Not tested / UNVERIFIED |
|---|---|---|
| Port Scanner | idle; happy path (1.1.1.1, 8.8.8.8, example.com) and accuracy; empty input (Scan disabled); invalid host (`bad!!`, `bad host!!`); custom range over-limit (20000) and inverted (start > end); Stop mid-run; rerun; rotate mid-run; Back and rail navigation; Recent X (removed 1.1.1.1); preset dropdown; concurrency slider to 405 (warning shown); IME Enter; keyboard covering button; Show all; Copy (no visible feedback); Share (chooser); Help; banners; dark mode; font 1.3; landscape | Protocol toggle OFF; clear-all recents; timeout slider drag; accuracy for ports beyond the 16 checked; a full 1100-port run to completion; Clear results (X) |
| LAN Scanner | idle; /24 happy path (4 runs); progress; summary; network map; host list sort; expanded host row; uncertain-probe toggle; ARP cross-check; Back from expanded row; search, filter chips, sort chips (present; search+filter checked) | Share; Clear; Re-scan; Edit; Wake, Ping, Probe HTTP, Scan ports actions; Has Ports and sort orders; rotation; dark mode; invalid subnet; the Network Map "+N more" overflow |
| Wi-Fi Scanner | location denied (gate); granted; connected card; 2.4 GHz tab; spectrum; cards; expand; BSSID detail sheet; sheet Back; search match and no-match | 5 GHz tab; Refresh and throttle; Sort chips; Copy and Share; Help; rotation; dark mode; auto-refresh Off/15 s/60 s |
| Network Topology | idle; invalid seed IP; valid seed IP; CTA discoverability; IME Enter; failed discovery and Retry; discovering state and Cancel button (visible, not pressed) | positive path; cancel pressed; v1/v3; LLDP toggle; Max Hops beyond default; canvas; node sheet; rotation; dark mode |
| Settings / About | open; all items; system night mode theme; font scale | in-app theme selector; Clear all recent hosts; Reset welcome guide |
| Process death | kill attempt on Recents task (ignored); kill-all; relaunch from Recents; state check | kill with app on screen (not done) |

## Things that worked well

- Port Scanner live progress ("15 / 23 ports", "3 open found so far"), the Stop Scan button, and the results header with Copy/Share/Clear icons.
- Port results are accurate against an independent socket check (1.1.1.1). HTTP and TLS banners are captured (e.g. "cloudflare-dns.com").
- Inverted range validation is inline and disables Scan.
- Error cards use the error colour with Retry (Port Scanner over-limit, Topology failure).
- Enter on the Port Scanner host field starts the scan.
- Recent chips persist across navigation and process death. Chip removal works.
- LAN host rows show latency, port count, vendor and MAC. Search, filter, sort and the empty state are present. The network map and expanded row are rich.
- Wi-Fi: signal gauge, channel and width, Wi-Fi standard in the detail sheet. Sheet Back works. Connected SSID matches the system.
- Settings: system theme follows device night mode; theme selector, defaults, attributions and licences present.
- No crashes in any run.

## Product owner: what a network engineer would expect and this group lacks

- **Port Scanner:** export results (CSV, JSON, table copy), scan history with re-run, a "scan only open" option, a custom preset editor, a port-number search in results, sort by port, service or latency, and a notice when the host did not answer ICMP or any port.
- **LAN Scanner:** export CSV or JSON (Share exists, format unverified), named devices with notes, saved subnets, mDNS and NetBIOS names in the list, a diff between runs (new and gone devices), and results kept when leaving the screen.
- **Wi-Fi Scanner:** channel-utilisation over time, RSSI history per AP, a 5 GHz channel map, export, best channel for 5 GHz, and a filter for hidden networks.
- **Topology:** export (DOT, PNG, JSON), node search, last-run persistence, one clear Discover action, and a visible explanation of why LLDP/CDP is off.
- **Cross-tool:** a "Last result" with timestamp; Back behaviour consistent for expanded rows and sheets; a snackbar on Copy.

## Open items (not done in this pass)

- Topology positive path and cancel (no SNMP device on this LAN).
- Wi-Fi 5 GHz, sort, auto-refresh changes, rotation, dark mode.
- Port Scanner full 1100-port completion; Protocol toggle off.
- TalkBack was not exercised. Content descriptions were checked only in the UI tree: icon buttons on Port Scanner, LAN and Topology carry labels; the hero icons and the Network Map icons do not.
