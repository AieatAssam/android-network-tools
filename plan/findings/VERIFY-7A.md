# VERIFY-7A - final visual pass, phone emulation C2 / C3 / C4

Build: HEAD f1a29e3 (debug build from `./gradlew :app:assembleDebug`, installed with `adb install -r`). Device U8BBB20806201256, `net.aieat.netswissknife.debug`.
Method: density only (no `wm size`). Screenshots in `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/v7a/` (prefix c2_, c3_, c4_). Each PNG was opened and read.
Targets: local subnet 10.109.208.0/24, gateway 10.109.208.1, example.com, 1.1.1.1, 8.8.8.8 (not used in this pass), google.com (not used). No Wake-on-LAN was sent. No speed test.

## 1. Configurations and get-config

| Id | Setup | get-config (first line, abbreviated) |
|---|---|---|
| C2 | density 533, font_scale 1.3, portrait | `sw360dp-w360dp-h531dp-normal-notlong-...-533dpi-port-notnight` |
| C3 | density 480, user_rotation 1 (landscape) | `sw400dp-w640dp-h353dp-normal-notlong-...-land-notnight-xxhdpi` |
| C4 | density 480, portrait, `cmd uimode night yes` | `sw400dp-w400dp-h593dp-normal-notlong-...-port-night-xxhdpi` |

Restore run after the pass: `wm density reset`, `wm size reset`, accelerometer_rotation 1, user_rotation 0, font_scale 1.0, `cmd uimode night no`, `pm revoke ... ACCESS_FINE_LOCATION`. Post-restore get-config is the tablet config (sw600dp, density 320, night no). `logcat AndroidRuntime:E` showed no FATAL lines.

## 2. Tool x config matrix

Key: P = pass (what was looked at), or an issue ID, or NT with reason. Keyboard column is the C2 / C3 field check (IME open: field, error, Recent row, primary action).

| Tool | C2 (360dp, font 1.3) | C3 (landscape 400dp) | C4 (dark 400dp) | Keyboard C2 / C3 |
|---|---|---|---|---|
| Home | P: single-column tool list, hero and nav fit (c2_home2.png) | P: 3-column grid, hero, nav (c3_home.png) | P: dark hero gradient, 2-column tiles, tile titles and icons legible after entry animation (c4_home2.png, c4_home_scroll.png). See V7A-10 | NT: no input field on Home |
| More sheet | P: all 16 tools reachable by scroll, Wake-on-LAN and Speed Test at bottom (c2_more*.png) | P: Diagnostics block readable, Traceroute/Port Scanner reached (c3_more.png) | P: list reads cleanly, Network Info row cut at scroll edge only (c4_more.png) | NT: no input field |
| Ping | P: idle, Statistics + RTT chart + results rows (c2_ping_run*.png). Error card "Ping failed" with Retry and Clear (c2_ping_err2.png) | P: stats row, RTT chart, results rows with bars (c3_ping_run.png, c3_ping_s2.png, c3_ping_s4.png). Chart/labels fit | P: idle, results, chart in dark (c4_ping_run.png, c4_ping_run2.png). Error card "Ping failed" + Retry + Clear (c4_ping_err.png). See V7A-03 comparison note | Field, error, Recent visible in both (c2_ping_kb.png, c3_ping_kb.png). Start Ping reachable above IME in C3 (scripted, 4 swipes) |
| Traceroute | P: idle, run results, hop cards, Journey card, RTT analysis (c2_trace_run.png, c2_trace_top.png, c2_trace_end.png) | P: hop cards with AS and city, Journey card (c3_trace_run.png, c3_trace_run3.png, c3_trace_journey2.png) | P: hop cards dark, Journey card and RTT tiles (c4_trace_run.png, c4_trace_run3.png) | Field, error, Recent in both (c2_trace_kb.png, c3_trace_kb.png). C3: Start Trace only reached after 6 swipes and sits at the top edge of the keyboard area (V7A-11) |
| DNS | P: idle, result card, answer rows (c2_dns_idle.png, c2_dns_run2.png). See V7A-05 | P: result hero, answer rows (c3_dns_run.png, c3_dns_run2.png) | P: dark hero, answer rows, Additional/Raw sections (c4_dns_run.png, c4_dns_run3.png). See V7A-05 | Field, error, Recent in both (c2_dns_kb.png, c3_dns_kb.png). Look up reachable above IME in C3 (3 swipes) |
| Port Scanner | P: idle, gateway run, Open Ports cards (c2_ports_run*.png). Banner text joined (V7A-01) | P: summary tiles, Open Ports (c3_ports_run.png, c3_ports_r2.png). Banner joined (V7A-01) | P: summary tiles, Open Ports cards, Show all link (c4_ports_run.png, c4_ports_run3.png). Banner truncated with ellipsis (V7A-01) | Field, error, Recent in both (c2_ports_kb.png, c3_ports_kb.png). Scan Ports reachable above IME in C3 (4 swipes) |
| LAN Scanner - Summary | P: "Scan Complete / 56 devices found / 254 addresses / 30.5 s" with uncertain link, Re-scan, Clear/Copy/Share (c2_lan_run.png, c2_lan_run2.png). Copy/Share placement V7A-06 | P: summary block (c3_lan_r2.png). Visible counts "50 devices found / 254 addresses / 28.4 s" | P: "57 devices found" summary and actions (c4_lan_run2.png). Numbers consistent with list | Field in C2 and C3 (c2_lan_kb.png, c3_lan_kb.png). C3 Scan Network reachable above IME (3 swipes). Recent subnet row hidden while field focused (C3) |
| LAN Scanner - List | P: device rows, latency, vendor line (c2_lan_list2.png, c2_lan_run3.png) | P: rows with latency and port count (c3_lan_list.png) | P: rows dark (c4_lan_list.png). Vendor/MAC ellipsized (V7A-08) | n/a |
| LAN Scanner - Map | P: node grid with labels (c2_lan_map.png) | P: map card in landscape (c3_lan_map.png) | P: node grid, labels ellipsized (V7A-08). Green node has no legend (V7A-08) | n/a |
| LAN Scanner - Uncertain probes sheet | P: sheet title, explainer, probe rows (c2_lan_uncertain.png) | P: sheet (c3_lan_uncertain.png). Last row under system nav (V7A-02) | P: sheet (c4_lan_uncertain.png). Last row under system nav (V7A-02) | n/a |
| LAN Scanner - Copy/Share row | P: Clear, Copy, Share row present (c2_lan_copy.png) | P: Clear, Copy, Share row present (c3_lan_r2.png) | P: Clear, Copy, Share row present (c4_lan_run2.png) | n/a |
| Topology | P: config card and Discover button (c2_topo_idle.png). Failure card "Discovery Failed" + Retry (c2_topo_err.png). Target IP keyboard state (c2_topo_kb.png). See V7A-03 | P: config card, Max Hops slider, LLDP checkbox (c3_topo_bottom.png). Failure card reached (c3_topo_err3/err4.png). Keyboard (c3_topo_kb.png) | P: config, failure card with Retry (c4_topo_err2.png). See V7A-03 | Field and error shown (c2_topo_kb.png). C3 keyboard shown (c3_topo_kb.png). **Partial**: scripted C3 check did not focus the field (expand not done), so Discover reachability above IME is UNVERIFIED for C3. NT for success state: no SNMP responder on the local subnet answered (gateway GET timed out), so the discovered-topology result cannot be reached without a target that is out of scope |
| Wi-Fi | P: hero, Updated chip, search, Copy/Share, Currently Connected card (c2_wifi_idle.png, c2_wifi_list.png, c2_wifi_ap.png) | P: hero and list (c3_wifi_idle.png, c3_wifi_list.png, c3_wifi_ap.png) | P: dark hero and list, spectrum chart (c4_wifi_idle.png, c4_wifi_list.png, c4_wifi_ap.png). See V7A-04, V7A-09 | Search field and IME in C2 and C3 (c2_wifi_kb.png, c3_wifi_kb.png). NT for primary action: search has no submit; the primary action is Scan in the hero, which sits above the field |
| Network Info | P: sections, Wi-Fi link, IP configuration (c2_netinfo_idle.png, c2_netinfo_2.png, c2_netinfo_3.png). Hero subtitle absent in this pass (V7A-12, UNVERIFIED) | P: idle and sections (c3_netinfo_idle.png, c3_netinfo_s2.png) | P: idle and sections (c4_netinfo_idle.png, c4_netinfo_s2.png). See V7A-07 | NT: no input field on this screen |

## 3. Issues (new in this pass)

| ID | Type | Sev | Config | Repro | Screenshot | Suggested fix |
|---|---|---|---|---|---|---|
| V7A-01 | BUG | Low-Med | C2, C3, C4 | Port Scanner, Scan Ports against 10.109.208.1; the HTTP open-port banner shows header lines joined with no separator: "HTTP/1.1 301 Moved Permanentlycontent-length: 0location: https://..." | c2_ports_run3.png, c3_ports_r2.png, c4_ports_run3.png | Join banner lines with a newline or " · "; in C4 the single-line banner ends in an ellipsis, so the joined text cannot be read at all |
| V7A-02 | UX | Medium | C3, C4 | LAN Scanner, after a scan, tap "N uncertain probes": the bottom row of the sheet is drawn under the system navigation buttons | c3_lan_uncertain.png, c4_lan_uncertain.png | Add navigation-bar insets to the sheet content (contentPadding / WindowInsets.navigationBars) |
| V7A-03 | UX | Low-Med | C2, C4 | Network Topology, Discover, failure card: the results area scrolls under the pinned Discover button and the card top is clipped flat at the region edge (rounded corners and icon hidden). Repro: C4 c4_topo_err.png vs c4_topo_err2.png (Discover stays at y 819 while the card moves up) | c2_topo_err.png, c4_topo_err2.png | Put results below the button with spacing, or fade the scroll region edge |
| V7A-04 | DUP | Low | C4 | Wi-Fi list, hidden network: name "<Hidden: 77:61:60>" plus a "Hidden" chip says the same thing twice | c4_wifi_ap.png | Drop the chip or the name suffix |
| V7A-05 | DUP | Low | C2, C4 | DNS result: hostname "example.com" appears in the result header and on each answer row, and "A" appears in the Record-type chip, the answer badge and the Answer header | c2_dns_run2.png, c4_dns_run3.png | Show the record name once in the header, or drop it from answer rows |
| V7A-06 | CONSISTENCY | Low-Med | C2, C3, C4 | Copy/Share placement differs: DNS inside the hero card, Traceroute at the top of results, Ports at the bottom right, LAN at the bottom of the summary, Wi-Fi under the search field | c4_dns_run.png, c2_trace_top.png, c2_ports_run2.png, c2_lan_run2.png, c2_wifi_idle.png | One shared result action row placement (e.g. under the result card) |
| V7A-07 | DUP / density | Low | C2, C3, C4 | Network Info: "Prefix /64" caption repeats on each of 9 IPv6 rows; Gateway and DNS server rows both show 10.109.208.1 | c4_netinfo_s2.png, c3_netinfo_s2.png | Group IPv6 addresses under one caption, or show the prefix once |
| V7A-08 | UX | Low | C4 | LAN list: vendor/MAC line ellipsized mid-address ("BC:24:11:5F:A…"). Map view: hostname labels ellipsized ("HUAWEI_…"). The green node in the map has no legend | c4_lan_list.png, c4_lan_map.png | Keep the MAC visible (two-line row or wrap); add a legend for the highlighted node |
| V7A-09 | UNVERIFIED | Low | C4 | Wi-Fi: four networks show identical "-63 dBm / 74%" | c4_wifi_ap.png | Check whether the values are real scan data before changing anything |
| V7A-10 | OBSERVATION | - | C2 vs C4 | Home tile grid is 1 column at 360dp / font 1.3 and 2 columns at 400dp. Not a defect, but the same screen changes layout between the two phone widths | c2_home2.png, c4_home_scroll.png | None required; note for the design doc |
| V7A-11 | UX | Low | C3 | Traceroute with the keyboard open: Start Trace is reachable only after 6 swipes and sits at the top edge of the keyboard area | (scripted, see imecheck run) | Keep the primary action above the IME or make it sticky |
| V7A-12 | UNVERIFIED | Low | C2, C3 | Network Info hero shows the subtitle in C4 ("Your active network, addresses, DNS and public IP") but the label dump of the C2 and C3 captures had no subtitle node. Could be animation timing (captures were 2-3 s after open) | c4_netinfo_idle.png | Re-check on a fresh C2/C3 run before filing |

Note on error cards: Ping (C4) and Topology (C4) use the same saturated red error card. This is consistent within dark mode and is not logged as an issue. The light-mode error card (c2_ping_err2.png) is pale. Both are valid M3 error containers.

## 4. What worked well

- No clipped primary text at font 1.3 in portrait; all primary buttons full width and at least 48dp tall (c2_ping_idle.png, c2_lan_idle.png).
- Keyboard handling: input field, inline error text and Recent row stay above the IME in both C2 and C3 for Ping, DNS, Port Scanner, Traceroute, LAN Scanner, Topology and Wi-Fi search (c2_*_kb.png, c3_*_kb.png).
- Dark mode contrast is good on result text, chips, hop cards and the Journey card (c4_trace_run3.png, c4_dns_run3.png).
- Landscape: no horizontal overflow; More sheet, cards and nav are usable at 640dp (c3_*).

## 5. Gaps (stated explicitly)

- Topology discovery success state: NT. No SNMP responder on the local subnet, so no real topology result could be produced under the allowed targets.
- Topology C3 keyboard primary reachability: UNVERIFIED (scripted check did not focus the field).
- Ping error state in C2: captured (c2_ping_err2.png). Traceroute, DNS and Port Scanner error states: not captured (cheap-error step skipped for time; the richest result state was covered in every config).
- Wi-Fi primary action with keyboard: NT (no submit action on the search field; primary Scan is in the hero).
