# VERIFY-5 – device verification of WP25, WP26, WP28, WP29 + final regression

Build: HEAD 2f1a543 (debug, `net.aieat.netswissknife.debug` 1.0.0-debug), tablet U8BBB20806201256, Android 8.0, 1200x1920.
Evidence folder: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/verE/` (screenshots `*.png`, uiautomator dumps `mac_*.xml`, helper scripts `ui.py`, `open.py`, `ime.sh`, `clip.py`).
Clipboard was read with `adb shell service call clipboard 2 s16 com.android.shell` through `clip.py` (heuristic parser, may drop the first characters of a long text). Toasts were checked by screenshot and by `dumpsys notification --noredact` (ToastRecord with the app package).
Device settings restored at the end: accelerometer_rotation 1, user_rotation 0, font_scale 1.0, night no, ACCESS_FINE_LOCATION revoked.

## 1. Back/forward state

| Item | Result | Evidence |
|---|---|---|
| DNS: domain and last result kept after Home and reopen | PASS | `dns_back.png`; UI: EditText `example.com`, record cards, Copy/Share present |
| Traceroute: completed result (10 hops, Reached) kept | PASS | `tr_reopen_scrolled.png` |
| Traceroute: run to 203.0.113.9 left mid-run (Home after ~3 s) | PASS (not restored as running) | `tr_reopen_running2.png`: Start Trace enabled, no spinner, input kept, 9 partial hops kept. Message reads "Trace canceled. 9 hops retained" although Stop was never tapped (see V5-01) |
| WHOIS: input and result kept | PASS | `whois_reopen.png` |
| Subnet: input and full result kept | PASS | `subnet_reopen.png` (10.20.30.40/22, error card and breakdown restored) |
| Wake-on-LAN: MAC and device-name field kept (no packet sent) | PASS | `wol_reopen.png` (02:ab:cd:ef:12:34, "QA box") |
| HTTP Probe: URL, last result (200, 386 ms) kept | PASS | `http_reopen.png` |
| HTTP Probe: cURL copy of restored result has no headers or body | PASS for this case, path not exercised | Copy as cURL gave `curl -X 'GET' ... -L 'https://example.com'`. No custom header was set, so the known WP25 gap (headers/body dropped on restored results) was not reproduced; UNVERIFIED with headers set |
| Ping: Count, Payload, Interval sliders kept across Home | PASS | `ping_reopen.png`: Count 31, Payload 736 bytes, Interval 7624 ms. Timeout and TTL not confirmed (TTL tap landed on its existing value) |
| Port Scanner: host and result (1.1.1.1, Duration 2.1 s) kept | PASS | `ports_reopen.png` |
| Port Scanner running state not restored | UNVERIFIED | Scan finished in about 2 s before Home could be tapped |
| LAN Scanner: stopped scan kept as partial results, not running | PASS | `lan_running.png` (Scanning...), then reopen shows "Scan paused" with 1 host of 50 scanned. Subtitle wording is wrong (V5-02) |
| HTTP Probe / WHOIS running state not restored | UNVERIFIED | Requests completed before navigation |

## 2. Copy and Share (WP26 audit table)

Clipboard text was read back for every row. "Toast" means the Copied toast was seen (screenshot or dumpsys). Share = Android share sheet opened ("Share with", Gmail, Copy to clipboard, Drive) and closed with Back.

| Tool (WP26 row) | Result | Evidence |
|---|---|---|
| Ping: Copy as CSV | PASS (clipboard: CSV rows + stats, no secrets). Toast not checked. Share not in WP26 scope | `copy_ping.png` |
| Traceroute: Copy | PASS (clipboard: header + hop list). Toast not checked. Share PASS (after a 1.1.1.1 run) | `copy_tracert.png`, `copy_tracert.png` |
| Port Scanner: Copy report | PASS. Toast PASS (screenshot, dumpsys). Share PASS. Export shows raw "Duration: 2083 ms" (V5-04) | `copy_ports.png` |
| LAN Scanner: Copy and Share results | PASS. Toast PASS (screenshot "Copied to clipboard"). Share PASS. Text has the "Duration: 30.6 s" line | `lan_copy_toast.png`, `lan_share_sheet.png` |
| DNS: result Copy | PASS (clipboard "example.com / Server: System DNS / A ..."). Toast PASS (dumpsys ToastRecord; screenshot). Share PASS | `dns_copy_toast.png` |
| Wi-Fi Scanner: Copy and Share | PASS (clipboard "Wi-Fi scan: 14 access points ..."). Share PASS. Toast not checked. Location granted for this test and revoked afterwards | `copy_wifi.png` |
| Network Topology | UNVERIFIED. No SNMP device on the LAN, so no result to copy. Error state checked: "Discovery Failed ... no response within 3000 ms; verify the host, UDP port, firewall, and community string" (clear message) | none |
| TLS Inspector: Copy and Share results | PASS (clipboard: leaf/intermediate chain, SANs, SHA-256). Share PASS. Toast not checked. First characters may be cut by the reader | `copy_tls.png` |
| WHOIS: Copy and Share | PASS (clipboard: parsed fields + raw). Toast PASS (dumpsys). Share PASS. Dates are raw epoch ms (V5-03) | `copy_whois.png` |
| HTTP Probe: summary Copy and Share | PASS (clipboard: status, time, size, response headers, security checks; no request headers, no body). Share PASS. Toast not checked | `copy_http.png` |
| HTTP Probe: Copy as cURL | PASS (see section 1) | `http_curl_copy.png` |
| Subnet Calculator: Copy and Share | PASS (clipboard: CIDR, network, broadcast, hosts, masks, scope). Share PASS. Toast not checked. Copy and Share sit below the result, not in the header | `copy_subnet.png` |
| mDNS Browser: Copy and Share | PASS (clipboard: service records, addresses, TXT). Share PASS. Toast not checked. TXT includes device MAC and UUID (device identifiers, user-initiated) | `copy_mdns.png` |
| Speed Test: result Copy | PASS (clipboard: latency, jitter, load latency, download/upload avg/peak). Share not re-run (test not repeated to spare the link) | `copy_speed.png` |
| Wake-on-LAN: Copy and Share | UNVERIFIED. The card appears only after a magic packet is sent; packets were not sent | none |
| Network Info: Copy all and Share | PASS (clipboard: transport, Wi-Fi link, addresses). Share PASS. Toast not checked | `copy_netinfo.png` |
| MAC Lookup: Copy all and Share | PASS (clipboard: input, address, type, vendor, block). Share PASS. Toast not checked | `copy_mac.png` |
| Secrets in any copied text | PASS | No SNMP community, no request headers, no request body in any copied text read |

## 3. LAN scan summary (WP28, WP29) and durations

| Item | Result | Evidence |
|---|---|---|
| Portrait: headline "56 hosts confirmed of 254 scanned" readable, meta "10.109.208.0/24 · 30.6 s" | PASS | `lan_done_portrait.png` |
| Duration shown human-readable ("30.6 s") | PASS | same |
| Re-scan (primary, full width), Copy / Share results / Clear in one row | PASS | same; no wrapping at font 1.0 |
| Landscape layout | PASS | `lan_landscape.png` |
| Landscape with font 1.3 | PASS | `lan_landscape_f13.png` (headline and stat tiles scale; buttons below the fold) |
| Dark mode with font 1.3, portrait | PASS | `lan_portrait_f13_dark.png` (contrast fine; "Gatew..." and "Amaz..." truncated in the map at 1.3, acceptable) |
| Network Map: no duplicated labels | PASS | Each node shows one vendor label (ellipsised) and one octet caption. Gateway shows "Gateway" and "1" |
| Network Map uses the whole card width | FAIL (low, V5-05) | 5 columns occupy about two thirds of the card on a 1200 px tablet |
| Port Scanner duration human-readable | PASS | "Duration: 2.1 s" (UI) |
| Port Scanner export duration | Raw ms in the export text (V5-04) | `copy_ports.png` |

## 4. Keyboard regression (WP24 behaviour on HEAD)

IME state read from `dumpsys input_method` (mInputShown). Each tool opened from Home with the IME closed. Steps: landscape, then dark + font 1.3, then restore.

| Tool | Result | Evidence |
|---|---|---|
| DNS Lookup: closed after landscape, dark+font 1.3, restore | PASS (all false, no focused field at open) | ime.sh output |
| Port Scanner | PASS (all false) | ime.sh output |
| Subnet Calculator | PASS (all false) | ime.sh output |
| Ping | PASS (all false) | ime.sh output |
| Wi-Fi Scanner (location granted for the run) | PASS (all false) | ime.sh output |
| MAC Lookup | PASS (all false) | ime.sh output |
| Wi-Fi search: type, then Clear | PASS. IME open while typing (true), closed after Clear (false), field emptied | `wifi` test in session |
| MAC Lookup: type MAC, tap Look up | PASS. IME true while typing, false after Look up | session test |
| DNS: tap domain field, type, tap Look up | PASS. IME true after tap, still true while typing, false after Look up. Field focused (FOCUSED EditText) | session test |

## 5. Smoke (16 tools)

| Item | Result | Evidence |
|---|---|---|
| Open all 16 tools from Home, no crash | PASS | Ping, Traceroute, Port Scanner, LAN Scanner, DNS Lookup, Wi-Fi Scanner, Network Topology, TLS Inspector, WHOIS Lookup, HTTP Probe, Subnet Calculator, mDNS Browser, Speed Test, Wake-on-LAN, Network Info all show their title. MAC Lookup opens (input present; its title was scrolled off, so the title check reported 0) |
| logcat has no FATAL / ANR | PASS | logcat was cleared before the smoke pass; `FATAL EXCEPTION` and `ANR in` counts are 0. No crash was seen at any earlier step either. Note: `am force-stop` was used once to reset the MAC input, and `pm revoke` restarts the process; both are expected |

## 6. Tablet look and feel (observed)

- Two-column tool grid on Home, rail on the left: fine in portrait and landscape.
- Result cards in all checked tools are full width with no stretched text fields; spacing consistent.
- Network Map leaves the right third of the card empty (V5-05).
- Traceroute configuration and sliders use the full card width without clipping.

## NEW issues

| ID | Type | Severity | Summary and repro | Expected | Actual | Suggested fix |
|---|---|---|---|---|---|---|
| V5-01 | UX | low | Traceroute: start a trace to 203.0.113.9, tap Home in the rail after about 3 s, reopen Traceroute. Repro screenshot `tr_reopen_running2.png` | Message explains what happened, e.g. "Trace stopped when you left the screen" | "Trace canceled. 9 hops retained as partial results." although the user never tapped Cancel | Use a distinct message for leave-screen stops, or reword to "stopped when you left the screen" |
| V5-02 | UX / copy | low | LAN Scanner: start a scan, tap Home in the rail mid-scan, reopen. The subtitle reads "These are partial results from the scan paused when the app went to the background." The app was never backgrounded (the rail is in-app). `res/values/strings.xml:397` (`lan_scan_paused_subtitle`) | Subtitle names the real cause | Wrong cause stated | Reword to "paused when you left this screen" |
| V5-03 | BUG (export) | low | WHOIS: Copy or Share a lookup of example.com. The text contains `Created: 808372800000`, `Updated: 1786694503000`, `Expires: 1818129600000` (epoch milliseconds) | ISO or human-readable dates, as an engineer would paste into a ticket | Raw epoch ms. Not compared with the on-screen date format | Format timestamps with the same formatter as the UI, keep the raw value only in the RDAP section |
| V5-04 | UX (export) | low | Port Scanner: Copy report prints `Duration: 2083 ms` while the screen shows "2.1 s" (WP29 formatter) | Same human format as the screen | Raw ms in the export | Use `humanDuration` in the report builder (golden test update) |
| V5-05 | UX / tablet | low | LAN Scanner, portrait and landscape: the Network Map grid stops at about two thirds of the card width, leaving an empty third. Screenshot `lan_done_portrait.png` | Columns fill the card (adaptive count from width) | Fixed five columns | Derive the column count from the available width, as the stat grid already does |
| V5-06 | UX | low | Ping advanced: the Interval slider stores non-round values ("Interval: 7624 ms"). Screenshot `ping_reopen.png` | Round steps (e.g. 100 ms) or an explicit value | Arbitrary step | Snap to a step; intent UNVERIFIED |

Not classed as new (carry-over, not reproduced this round): WP25 open item "copy-as-cURL of a restored HTTP Probe result omits request headers and body" (not exercised with headers set). WP26 deferred items (DNS per-record copy toast, export footers) are unchanged.

## Summary

1. HEAD 2f1a543 on the tablet: Back/forward restore works for DNS, Traceroute, WHOIS, Subnet, WoL, HTTP Probe, Ping sliders, Port Scanner and LAN (section 1).
2. Running requests are not restored as running (Traceroute, LAN). Traceroute and LAN messages are misleading (V5-01, V5-02).
3. Copy works for every WP26 row that could be run (clipboard checked); Share opened the sheet for all of them except Speed Test (not re-run), Ping (not in WP26 scope) and WoL (no result without sending). Topology is UNVERIFIED (no SNMP target).
4. Copied text is sane: no SNMP community, no HTTP request headers or body. WHOIS dates (V5-03) and raw ms in the Port Scanner report (V5-04) are the export issues.
5. LAN summary is readable in portrait, landscape, font 1.3 and dark: headline, "30.6 s", Re-scan/Share/Clear row, no duplicated map labels.
6. Map uses about two thirds of the width on the tablet (V5-05).
7. Keyboard: the IME stays closed after landscape, dark and font 1.3 on DNS, Ports, Subnet, Ping, Wi-Fi and MAC Lookup.
8. Wi-Fi search Clear and MAC Look up hide the keyboard; DNS Look up hides it too.
9. Smoke: all 16 tools open, no FATAL or ANR in logcat.
10. Settings restored (rotation auto, user_rotation 0, font 1.0, night off, location revoked). Six new low-severity issues V5-01 to V5-06, none blocking.
