# QA findings – Group D-local

Tools: mDNS Browser, Speed Test, Wake-on-LAN (partial), HTTP Probe (POST and Copy as cURL), TLS Inspector (dark mode, font 1.3, rotation)
Device: Android 8.0 tablet, serial U8BBB20806201256, 1200x1920, Wi-Fi with internet
Build: net.aieat.netswissknife.debug (installed from main)
Screenshots and logs: /tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/qaD/
Independent checks: host `curl` (POST to example.com returned 405; replay of Copy-as-cURL returned 405; 2 MB upload to speed.cloudflare.com/__up returned 200 at ~13.6 MB/s).

Device settings: accelerometer_rotation=1, user_rotation=0, font_scale=1.0, night mode off. Restored at the end.

Severity: critical / high / medium / low. Type: BUG, UX, A11Y, PERF, FEATURE, GAP. "UNVERIFIED" marks anything not confirmed.

---

## Summary

| ID | Tool | Type | Severity | Title |
|---|---|---|---|---|
| MDNS-01 | mDNS Browser | BUG | medium | DNS-SD escapes shown raw in instance names (`\032`, `\226\128\153`, `\(...\)`) |
| SPD-01 | Speed Test | BUG | high | Upload phase fails with raw "timeout" on 2 of 2 full attempts (reproduced in a short second run) |
| SPD-02 | Speed Test | UX | medium | Failed run discards partial download/latency results; error shows raw exception text |
| MDNS-02 | mDNS Browser | A11Y | medium | Search field has no leading icon and no clear button |
| MDNS-03 | mDNS Browser | UX | medium | No-match search shows a blank list, no empty-state message |
| MDNS-05 | mDNS Browser → HTTP Probe | BUG | medium | "Open in HTTP Probe" prefills `http://…:80/`, which HTTP Probe rejects (plain HTTP disabled) |
| MDNS-07 | mDNS Browser | UX | medium | Red "safety limit" banner appears after every full scan with ~50 services; no limit is named (UNVERIFIED which) |
| HTTP-05 | HTTP Probe | UX | low | Copy as cURL gives no visible confirmation |
| HTTP-06 | HTTP Probe | UX | low | "Prefilled from mDNS Browser" banner persists after the URL is edited |
| HTTP-07 | HTTP Probe | UX | low | Request body field has no leading icon and no clear button (CLAUDE.md checklist) |
| TLS-05 | TLS Inspector | UX | low | Recent-host chip row is clipped at the right edge with no fade or scroll affordance |
| TLS-06 | TLS Inspector | DOC | low | README says timeout is configurable (500–30 000 ms); the UI has no timeout control |
| MDNS-06 | mDNS Browser | DOC | low | README says "Configurable scan duration"; no control exists. Help says "Reset" but the button is "Clear" |
| MDNS-04 | mDNS Browser | UX | low | Copy/Share export ignores the search filter (exports all services) (source-confirmed) |
| SPD-03 | Speed Test | UX | low | Cancel returns to idle with no "canceled" message; two Start Test controls (circle and bar) |
| WOL-01 | Wake-on-LAN | UNVERIFIED | — | Saved devices, broadcast validation and packet send were not tested (run stalled on the device, see notes) |
| GAP-01 | All | GAP | — | See Gap analysis below |

Confirmed, previously reported by group C: TLS-02 "Trusted" pill colour (reconfirmed in light and dark), HTTP-02 timing phases exceed total time (reconfirmed on POST: 66+179+141+5 = 391 ms vs TTFB 312 ms).

---

## Findings

### MDNS-01 – BUG – medium – DNS-SD instance names show raw escape sequences
- Repro: More → mDNS Browser → Scan Network. Look at `Aieat\226\128\153s\032MacBook\032Air` (`_airplay._tcp`) and `HP\032LaserJet\032300\032colorMFP\032M375nw\032\(615DC8\)` (`_http._tcp`).
- Expected: decode DNS-SD escapes (`\DDD` decimal) and UTF-8 so the name shows "Aieat's MacBook Air".
- Actual: literal backslash sequences are shown in the title. Search and the copy/share export carry the same raw text.
- Screenshots: mdns_03_t12.png, mdns_22_http_filter.png, mdns_09_rerun_2s.png
- Fix: unescape `\DDD` and `\.` in the instance label at parse time (RFC 6763 §4.3), keep the raw form for copy.

### MDNS-02 – A11Y – medium – search field lacks icon and clear
- Repro: type text into "Search services".
- Expected (CLAUDE.md): leading icon and clear (X) button in the OutlinedTextField.
- Actual: no icon, no clear button (uiautomator shows no clickable trailing node). Clearing requires manual deletion.
- Screenshots: mdns_06_state.png, mdns_21_nomatch_nokb.png
- Fix: add `leadingIcon` and a trailing `IconButton` with `contentDescription` shown when non-empty.

### MDNS-03 – UX – medium – no-match search is blank
- Repro: scan, type `zzqq` in Search services.
- Expected: "No services match 'zzqq'" with a Clear action.
- Actual: the list area is empty; Copy and Share results stay visible. Hero still says "51 found" (unfiltered).
- Screenshot: mdns_21_nomatch_nokb.png

### MDNS-05 – BUG – medium – Open in HTTP Probe prefills an unusable URL
- Repro: scan, search `_http._tcp`, tap "Open in HTTP Probe" on any `:80` service.
- Expected: an HTTPS URL, or a clear note that the service is plain HTTP.
- Actual: `http://npi615dc8.local:80/` is prefilled. HTTP Probe then shows "Plain HTTP requests are disabled in this release" and the Send path is blocked until the user edits the URL.
- Screenshot: httpprobe_01_handoff.png, httpprobe_02_state.png
- Fix: for `:80` or `http` services, do not prefill, or prefill `https://` with a hint.

### MDNS-07 – UX – medium – safety-limit banner after normal scans (UNVERIFIED cause)
- Repro: run a full scan on this LAN (about 50 services). The red banner "Discovery reached a safety limit. Showing the services found so far." appears every time.
- Expected: no banner for a normal complete scan, or a banner that names the limit.
- Actual: the banner appears; the limit that fired is not identified. Source shows six possible truncation reasons (MdnsTruncationReason). UNVERIFIED which one fires.
- Screenshots: mdns_03_t12.png, mdns_18_landscape_done.png
- Fix: list the specific truncation reason, use neutral (not error) colour for informational truncation.

### MDNS-04 – UX – low – Copy/Share ignores the search filter (source-confirmed)
- `MdnsDiscoveryScreen.kt` builds the report from `allServices` regardless of `searchQuery`. Decide whether export should follow the visible filter.
- Note: Copy writes a multi-line report, but pasting into a single-line field showed only the first line. Pasting into a multi-line field was not done (UNVERIFIED that the full report is on the clipboard).

### MDNS-06 – DOC – low – README and Help drift
- README: "Configurable scan duration (8-second window)". The UI has no duration control; the window is fixed at 8 s (`DEFAULT_SCAN_DURATION_MS`).
- Help: "Reset – clear all results". The button is labelled "Clear".

### Other mDNS observations (no finding filed)
- Scan spinner overlaps the hero title while scanning (mdns_09_rerun_2s.png). Low.
- Search field: Enter does not dismiss the keyboard; in landscape the keyboard covers the results.
- Ping chips include IPv6 link-local `fe80::…` without a zone id (UNVERIFIED that Ping works with it). A loopback `127.0.0.1` address advertised by a remote host is shown as a Ping chip without a note.

---

### SPD-01 – BUG – high – upload phase fails with "timeout"
- Repro: More → Speed Test → Start Test.
- Result 1: download 34.2 Mbps, upload 14.4 Mbps at ~12 s, then "Speed test failed / Failed during Upload / timeout / Run again" at ~16 s.
- Result 2 (short run, rotated mid-download): download 37.5 Mbps at ~5 s, then the same upload failure.
- Expected: upload completes (host upload to the same endpoint succeeds: 2 MB at ~13.6 MB/s).
- Actual: upload fails in both runs. Likely cause: `OkHttpTransferEngine` sets `writeTimeout(5s)` (OkHttpTransferEngine.kt:33) and uploads 2 streams; a write stall over 5 s throws SocketTimeoutException("timeout") (UNVERIFIED on the device; not confirmed with logs).
- Screenshots: speed_08_done.png, speed_10_rotate_run.png
- Fix: raise write timeout to match the phase duration (or use a per-chunk stall timeout), and retry once.

### SPD-02 – UX – medium – failure discards partial results; raw error text
- Expected: keep latency and download results when upload fails, show a friendly reason ("Upload stalled") and Run again.
- Actual: all results are replaced by the error card; the message is the raw exception text `timeout` (`SpeedTestViewModel` uses `e.message`).
- Fix: map exceptions to ErrorInfo codes (the repository already uses ErrorCode for other failures) and keep partial results.

### SPD-03 – UX – low – cancel feedback and redundant controls
- Cancel returns to idle with no message. Two "Start Test" controls appear on the idle card (large circle and full-width bar). Stream chips "Download streams: 4 / Upload streams: 2" look like controls but appear informational (not verified as non-interactive).
- Screenshots: speed_01_idle.png, speed_05_after_cancel.png

### Speed Test – what worked
- Live phase stepper (Latency / Download / Upload), live gauge, throughput chart, latency under load, Help sheet (speed_09_help.png).
- Cancel during a run works (run stopped and state reset to idle).
- Rotation mid-run keeps phase, gauge and chart (speed_10_rotate_run.png).
- Share button not tested (no successful run; UNVERIFIED).

---

### HTTP-05 – UX – low – Copy as cURL has no feedback
- Repro: HTTP Probe → result → Copy as cURL.
- Expected: snackbar or toast "Copied to clipboard" (strings already exist for other tools: `lan_copied`).
- Actual: no visible confirmation in the UI tree or in code (`HttpProbeScreen.kt` onCopyCurl).

### HTTP-06 – UX – low – stale "Prefilled from mDNS Browser" banner
- After handoff, editing the URL keeps the "Prefilled from mDNS Browser" label until the user taps Clear.

### HTTP-07 – UX/A11Y – low – body field
- Request Body is a plain OutlinedTextField with no icon and no clear control; placeholder "{key: value}" is the only hint.

### HTTP Probe POST and Copy as cURL – tested (worked)
- POST `https://example.com/post-test` with custom header `X-QA-Test: qaD-1` and JSON body: result 405, method POST, protocol h2, timing breakdown, redirect-disabled notice shown. Host curl with the same request also returns 405.
- Copy as cURL output: `curl -X 'POST' -H 'X-QA-Test: qaD-1' --data-raw '{"ping":1,"note":"qaD"}' --proto '=https' 'https://example.com/post-test'`. Replayed verbatim on the host: 405. Pasted into the body field to inspect.
- Final URL shows `https://example.com/[path omitted]`. This is intentional (HttpProbePrivacy.kt), not a bug.
- Timing: DNS 66 + Connect 179 + TLS 141 + Transfer 5 = 391 ms, but TTFB 312 ms and response 321 ms (reconfirms HTTP-02).

---

### TLS-05 – UX – low – recent-host chips clipped
- Portrait and font 1.3: the last chip is cut at the edge ("tls-v1-2.b", "tls"), with no fade or scroll cue.
- Screenshots: tls_05_rotate_mid_run.png, tls_11_font13_top.png

### TLS-06 – DOC – low – timeout not configurable in UI
- README says timeout is configurable (500–30 000 ms). The UI has no timeout field; `INSPECTION_TIMEOUT_MS = 10_000` is fixed.

### TLS Inspector – tested
- Dark mode: correct tokens, Inspect button and cards readable; "Trusted" pill is mauve/pink (TLS-02 reconfirmed). tls_09_dark_result_scrolled.png
- Font scale 1.3: label width grew 218→276 px (applied). Layout reflows, no truncation of title or buttons. tls_11_font13_top.png
- Rotation during a run: portrait→landscape at ~0.4 s during example.com; landscape→portrait during 192.0.2.1. State "Inspecting…", Cancel button and disabled inputs preserved. tls_03, tls_05
- Unreachable host (TEST-NET 192.0.2.1): ends with "Inspection failed / TLS inspection timed out / Retry". Message is clear. tls_06_result_unreachable.png
- Cancel mid-run: "Inspection canceled". OK.
- TLSv1.3 shows "Not testable on this device" (honest on Android 8).

---

## Wake-on-LAN – partial (UNVERIFIED for most items)

Not completed. The validation loop was driven by adb `input text` in a long background script; it stalled and was stopped, so the save/broadcast/send checks were not run. No magic packet was sent in this session.

Checked (idle UI and validation messages only):
- Idle: MAC field with chip icon; "Send magic packet" disabled while empty; "Advanced options" expander. Screenshot wol_01_idle.png
- Invalid inputs `zz:zz`, `AA:BB:CC:DD:EE`, `AA:BB:CC:DD:EE:FF:00`, `00:00:00:00:00:00`, `FF:FF:FF:FF:FF:FF`, `01:00:5e:00:00:01` show "Enter a valid unicast MAC address (AA:BB:CC:DD:EE:FF)" and disable Send. Multicast/broadcast/zero MACs are rejected as unicast-only, which is correct.
- `AA-BB-CC-DD-EE-FF` accepted (Send enabled).
- `AABB.CCDD.EEFF` and `aabbccddeeff`: the reads were inconsistent (error text and Send enabled both appeared in one dump). UNVERIFIED. Recheck needed.
- Not tested: saved devices (none seen in UI), custom broadcast/port validation, packet send (only a made-up MAC to 255.255.255.255 was planned), success card, help.

---

## Gap analysis (commonly expected network tools missing from the app)

Ranked by value for a network engineer on Android, highest first:

1. **Packet capture / PCAP export (local VPN-free sniffing is limited on Android)** – FEATURE/GAP, high value for analysis, high effort. Realistic option: capture via a `VpnService` or remote capture import; at minimum, PCAP import/view.
2. **Bandwidth/Interface monitor** (per-interface RX/TX rate, Wi-Fi link stats over time) – GAP, high value; `NetworkStatsManager` and `TrafficStats` are available without root for own app; per-app is limited.
3. **Traceroute map / hop geolocation** – GAP, medium-high; Traceroute exists but no map or ASN enrichment.
4. **MAC vendor (OUI) lookup** – GAP, high value and cheap; OUI data already bundled (README mentions OUI resource for SNMP/topology). Expose as a standalone tool and in LAN Scanner rows.
5. **ARP / neighbour table** – GAP, high value; `/proc/net/arp` is restricted on Android 10+, so show what is readable and explain.
6. **Public IP and IP/network info** (public address, gateway, DNS servers, IPv4/IPv6, Wi-Fi details) – GAP, high value, cheap; a home card exists but no dedicated tool or history.
7. **Reverse DNS (PTR) lookup** – GAP, medium, cheap; DNS Lookup may already cover PTR (UNVERIFIED).
8. **Telnet/SSH banner grab** – GAP, medium; a TCP banner tool (read first bytes, no auth) fits the Port Scanner.
9. **DNS benchmark** (compare resolvers latency/correctness) – GAP, medium, cheap; complements DNS Lookup.
10. **Saved targets / presets across tools** – GAP, medium; WoL, Ping and HTTP Probe each keep only recents.
11. **Export/report bundle** (CSV/JSON across tools) – GAP, medium; Copy/Share exist per tool only.
12. **iperf-style LAN throughput test** (client/server between two devices) – GAP, medium-high for a LAN engineer; needs a peer.

---

## Tested checklist (D-local)

| Tool | Checked |
|---|---|
| mDNS Browser | idle; scan to completion; stop mid-scan; rerun (stale banner cleared); rotation during scan; search match, no-match, clear; TXT expand; Copy (partial paste check); Share (idle hides it; results-state share not verified); Help; Ping chip handoff and back; Open in HTTP Probe handoff; _http filter; landscape layout; escapes in names |
| Speed Test | idle; one full run (failed at upload); one short run with cancel; one short run rotated mid-download (then failed at upload); Help; error state; dark mode not tested; font scale not tested |
| Wake-on-LAN | idle; MAC invalid inputs; MAC valid notations AA-, AABB.CCDD.EEFF (UNVERIFIED), bare (UNVERIFIED); Help (not tested); saved devices (not present); packet send (not done); broadcast/port validation (not done) |
| HTTP Probe | POST with body and custom header; send and result panel; Copy as cURL and replay; Share (not tested); handoff from mDNS |
| TLS Inspector | dark mode result; font 1.3; rotation mid-run; cancel; unreachable host error; recent chips |

## What worked well
- mDNS: fast live discovery, grouped by type, per-instance TXT expand, Ping and HTTP Probe handoffs with prefill, Stop and Clear behave well.
- Speed Test: clear phase stepper and gauge; cancel and rotation keep state; Help explains latency vs. bandwidth well.
- HTTP Probe: POST flow, redirect notice explaining why cURL suppresses redirects, privacy-redacted final URL, copy-as-cURL output is shell-safe and replays correctly.
- TLS Inspector: dark mode and font 1.3 are clean; the "Inspecting…" state survives rotation; error messages for unreachable hosts are readable; the Probe older TLS toggle is clear.
- WoL: MAC validation message is specific and unicast-only rule is explained.
