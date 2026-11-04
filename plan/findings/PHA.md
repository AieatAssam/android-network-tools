# PHA – phone-mode QA (tablet emulating a phone)

Build: HEAD de5a39c (debug, `net.aieat.netswissknife.debug`), tablet U8BBB20806201256, Android 8.0, 1200x1920 px.
Screenshots and UI dumps: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/pha/` (`*.png` + `*.xml`).
Scope: app shell, Ping, Traceroute, DNS, Port Scanner, LAN Scanner (priority), Topology, Wi-Fi, Network Info. Two-pane layout out of scope. V5-01..V5-06 not repeated.

## 1. Configurations and get-config lines

| Id | Setup | get-config (first line, abbreviated) |
|---|---|---|
| C1 | density 480, portrait, font 1.0, light | `sw400dp-w400dp-h593dp-normal-notlong-...-port-notnight-xxhdpi` |
| C2 | density 533 + font_scale 1.3, portrait, light | `sw360dp-w360dp-h531dp-normal-notlong-...-533dpi-port-notnight` (font 1.3 is a setting, not in the config string) |
| C3 | density 480, landscape (user_rotation 1), light | `sw400dp-w640dp-h353dp-normal-notlong-...-land-notnight` |
| C4 | density 480, portrait, dark (`uimode night yes`) | `sw400dp-w400dp-h593dp-normal-notlong-...-port-night` |

Note: `wm size` was never changed; only density was overridden. Device restored at the end (section 7).

## 2. Findings

Unless a finding names another configuration, it was seen in C1.

Severity legend: high = blocks a clear reading of a result; medium = wrong data or a missing expected action; low = polish.

### PHA-01 DUP – LAN Scanner summary repeats the same numbers and subnet (high, C1, portrait, font 1.0, light)
Repro: LAN Scanner, Scan Network on the default subnet, wait for "Scan Complete" (run 1: 56 confirmed of 254; run 2: 54; run 3: 51). Screenshots `13_lan_done_top.png`, `14_lan_done_s1..s5.png`, `211_lan_p360f13_s1.png`.

Exact repeated strings and where they appear (run 1 values):

| String | Location 1 | Location 2 | Location 3 | Location 4 |
|---|---|---|---|---|
| `10.109.208.0/24` | Scan Configuration card title | Summary meta `10.109.208.0/24 · 30.8 s` | Stat tile `/24` ("Prefix") | |
| `56` | Headline `56 hosts confirmed of 254 scanned` | Tile `56` "Confirmed" | List header `Discovered Hosts (56)` | |
| `254` | Headline `… of 254 scanned` | Tile `254` "Scanned" | Implied in `Show uncertain probes (198)` (198 = 254 − 56) | |
| `Confirmed` | Headline word "confirmed" | Tile label "Confirmed" | Header "Confirmed Hosts" during the scan | |
| `Scan Complete` | Status line | Restates the headline verdict | | |
| `Re-scan / Copy / Share results / Clear` | Summary card | (not repeated) | | |

Expected: each number appears once; the summary holds only aggregate facts. Actual: the subnet appears 3 times in the summary area, the host count 3 times, the scanned count 2 times, and the uncertain count is a derivable fourth number.

Also: the uncertain probes list (`Show uncertain probes`) is inserted inside the summary card and holds per-host rows (`10.109.208.32: TCP probe timed out, port 80`, 198 rows, unbounded, no count header). It pushes the map and the host table far below the fold (`20_lan_uncertain.png`).

Suggested fix: see section 4 (true-summary design).

### PHA-02 DUP – Network Map repeats the host table (medium, C1)
Repro: after a scan, compare Network Map nodes with Discovered Hosts rows.
- Map node `Google, … / 103` and table row `10.109.208.103` (vendor `Google, Inc.` in the map and in the row second line).
- Map node `Amazon … / 118` and row `10.109.208.118 · none-3.local · Amazon Technologies Inc. · 68:9A:87:A4:57:FA`.
- Gateway: map node `Gateway / 1`, row `10.109.208.1` with chip `Gateway`, and filter chip `Gateway` (three places).
- The map shows 21 of 56 hosts and ends with `+35 / more…`, which is not tappable (`clickable=false`; `MoreDevicesNode` has no click handler, `LanScanScreen.kt` ~line 1536). Map nodes are not tappable either.
- Search and filters do not change the map (search `zzz` left all map nodes on screen).
Expected: map is a view of the same hosts that opens the host row, honours filters, and does not restate vendor text. Actual: a second, lossy copy of the table with dead controls.
Suggested fix: fold the map into the hosts section as a List/Map toggle over the same filtered list; tapping a node scrolls to and expands the row; remove vendor text from node labels.

### PHA-03 BUG – "Latency 0 ms" shown for hosts that were never pinged (medium, C1)
Repro: scan 10.109.208.0/24. Hosts `10.109.208.118`, `10.109.208.125`, `10.109.208.127`, `10.109.208.147`, `10.109.208.154`, `10.109.208.164`, `10.109.208.180`, `10.109.208.187` show `Latency 0 ms`; the expanded detail shows `Ping 0 ms`.
Cross-check from the tablet shell: `ping -c 2 10.109.208.118`, `.125`, `.3` all 100% loss (ARP entries exist: `REACHABLE`, `DELAY`). Host `.1` answers (5–13 ms).
Cause (source): `core-network/.../lan/PresenceClassifier.kt` line 16 and 28: `pingMs` defaults to `0L` and is only set by an ICMP echo; hosts confirmed by TCP, mDNS or NetBIOS keep 0. `LanScanScreen.kt` line ~1712 renders `lan_ping_status_format` unconditionally.
Expected: no latency value (or "No ping reply") when no RTT was measured. Actual: "0 ms", which reads as a perfect link.
Suggested fix: make `pingMs` nullable, show "Latency unavailable" or hide the chip; keep "Ping" in details consistent.

### PHA-04 UX – LAN search with no matches shows an empty void (medium, C1)
Repro: LAN Scanner after a scan, type `zzz` in "Search IP, hostname, vendor…". Result: the header "Discovered Hosts (56)" and the list disappear, leaving a blank area under the chips (`24_lan_search_empty.png`). Matching `Amazon` works (`Results (7 of 56)`, `26_lan_search_amazon2.png`).
Expected: "No hosts match "zzz"" with a Clear action. Actual: blank.

### PHA-05 UX – LAN sort and filter chips look alike and wrap awkwardly (low, C1)
Two chip rows with the same style: filters `All / Gateway / Has Ports`, sort `IP / Latency / Hostname / Vendor / Port count`. No "Sort by" label; `Port count` wraps alone onto a third line (`25_lan_search_amazon.png`). The selected sort (`Latency`) is the default and looks like a filter.
Suggested fix: label the row "Sort by", use a dropdown or a single row, and make the selected state distinct from filters.

### PHA-06 UX – Empty-state copy contradicts a filled input (low, C1; same pattern on Ping)
- LAN idle: field shows `10.109.208.0/24` but the empty-state card says "Enter a subnet to scan" (`11_lan_idle_scrolled.png`).
- Ping: host `1.1.1.1` is in the field and the Recent row, but the card says "Enter a host to ping" (`31_ping_idle_scrolled.png`).
Expected: state text follows the input (or is hidden when a value is present).

### PHA-07 UX/A11Y – LAN wording: "hosts" used for subnets (low, C1)
The Recent row lists subnets (`10.109.208.0/24`), but the controls are `Clear recent hosts` (trash icon) and `Remove from recents`. The Recent chip also repeats the field value.

### PHA-08 UX – LAN expanded host repeats its own header (low, C1)
Repro: tap a host row (e.g. `10.109.208.147`, `22_lan_host_expanded.png`).
- Header: `Latency 0 ms`, `1 port`, `ledpanel-zero2w.aieat.net · Raspberry Pi (Trading) Ltd · 2C:…` (truncated).
- Details: `Hostname ledpanel-zero2w.aieat.net`, `MAC Address 2C:CF:67:B7:6A:1C`, `Vendor Raspberry Pi (Trading) Ltd`, `Ping 0 ms`, `Open Ports (1)` (same as the `1 port` in the header).
- The `Wake device` button sits between the MAC and Vendor rows and breaks the key-value list. The detail panel uses a teal tint not used elsewhere on the screen.
Suggested fix: keep the header to IP + latency + ports; keep details as the only place for hostname, MAC, vendor; move Wake device to the bottom action row with Ping host and Scan ports.

### PHA-09 UX – LAN stat tiles orphan "/24" at phone-360 with font 1.3 (low, C2)
`211_lan_p360f13_s1.png`: tiles wrap 2 + 1, the `/24 Prefix` tile sits alone on a row. The summary headline and meta also wrap to 3 lines. Fixed by PHA-01 (fewer tiles).

### PHA-10 BUG – Wake-on-LAN missing from the More sheet (medium, C1)
Repro: bottom nav More → sheet. Section "Utilities" shows only `Subnet Calculator` and `Speed Test`, then `Settings` (`82_more_bottom.png`). Wake-on-LAN is on the Home grid, in `NavRoutes.allTools` (route `wol`) and in `TOOL_SECTIONS` (`MoreToolsSheet.kt` line 67), so it should appear.
Cause: not determined (UNVERIFIED). Expected: Wake-on-LAN listed under Utilities. Actual: missing from the sheet.

### PHA-11 BUG/UX – Ping: unresolvable host reported as 100% packet loss with raw resolver text (medium, C1)
Repro: Host `nosuchhost.invalid`, Start Ping (`46`, `48_ping_invalid_scrolled.png`).
Actual: Statistics show `Sent 1 · Received 0 · Loss 100%`; the row says `Unable to resolve host "nosuchhost.invalid": No address associated with hostname`. No error card and no Retry.
Expected: a DNS failure is an error state ("Could not resolve nosuchhost.invalid", Retry), not a loss figure. Shell check: `ping nosuchhost.invalid` → `unknown host`.

### PHA-12 UX – Ping: stale-result banner sits below the fold and is cut by the nav bar (low, C1)
After the host is cleared, `Showing the previous result for 1.1.1.1. Run again to update it for the new host.` is at the bottom edge, partly under the bottom nav (`44_ping_empty_submit.png`). The Start Ping button is disabled with no reason text while the field is empty.
Suggested fix: move the banner into the Statistics card header; add helper text under the disabled button.

### PHA-13 DUP – Ping: host and min/avg/max repeated (low, C1)
- `1.1.1.1` appears in the Recent chip, the field, the Statistics subtitle and the "Pinging…" card.
- Min 13 ms / Avg 25.1 ms / Max 56 ms appear in the Statistics tiles and in the RTT chart's TalkBack description (`RTT chart: … Min 13 ms, Avg 25.1 ms, Max 56 ms`). The chart shows the axis only.
Low impact; mention only if a summary is redesigned.

### PHA-14 A11Y/colour – Count colours read as errors (low, C1, light)
Ping: `10 Received` in a mauve/brown tint (`33_ping_done.png`). Port Scanner: `19 Filtered` on pink (`74_ports_results2.png`). Neither is an error. UNVERIFIED which colour tokens are used (in `StatusColors.kt` or the Material scheme).
Suggested fix: use the primary/positive token for received and a neutral for filtered.

### PHA-15 UX – Traceroute: number/unit spacing and mixed header actions (low, C1)
- Summary `1010ms Time` has no space; Ping and Port Scanner use `13 ms` / `2.1 s` (`51_tr_done.png`).
- Trace Results header: Copy and Share are icon-only, `Clear` is text. Ping uses an icon `Copy as CSV`, LAN and Wi-Fi use text buttons, Port Scanner uses three icons. The same job (copy, share, clear) has four patterns.

### PHA-16 UX – Traceroute hierarchy: the hop list is far down (medium, C1)
Order after a run: Trace Configuration (a tall card of sliders), Trace Results summary, Journey Intelligence card (two stat tiles and two long caveat paragraphs), RTT Analysis, Hop Details. The first hop is about 1,400 px below the summary (`52_tr_hops.png`, `53_tr_hops2.png`). The configuration card stays expanded during results.
Suggested fix: collapse the configuration after a run ("Settings: 30 hops · ICMP · 3 s") and show hops before the GeoIP card.

### PHA-17 DUP – DNS: record type and resolver repeated per record (low, C1)
Each record row shows the type three times: round `A` badge, the text `A`, and `IPv4` (`63_dns_records.png`). The result header says `via System DNS` while the DNS server dropdown already says `System DNS`.

### PHA-18 DUP – Port Scanner: Host and Resolved IP identical; OPEN badge repeats the section (low, C1)
For an IP input, `Host: 10.109.208.1` and `Resolved IP: 10.109.208.1` are the same (`74_ports_results2.png`). Each row shows an `OPEN` badge under the `Open Ports` heading and the `4 Open` tile.
Suggested fix: show "Resolved IP" only when it differs from the host.

### PHA-19 UX/Error – Topology: no Retry on the failure card; config summary lost on collapse (medium, C1)
Repro: More → Network Topology, target `10.109.208.1`, community default, Discover Topology (`93_topo_done.png`). Actual: `Discovery Failed` card with the message `SNMP v2c GET 1.3.6.1.2.1.1.1.0 to 10.109.208.1:161 failed: no response within 3000 ms; verify…`. There is no retry action inside the card (CLAUDE.md error-state requirement), and the collapsed "SNMP Configuration" header shows no target or version.
Also (UNVERIFIED animation artefact): with the config expanded, the Community String field is cut at the card bottom (`90_topo_idle.png`), and a stray small green dot sits below the Discover button.
Suggested fix: add "Retry" to the error card; show `10.109.208.1 · v2c` in the collapsed header.

### PHA-20 UX – Wi-Fi: scan action is icon-only; duplicated counts and time (medium, C1)
- Hero: `7 SSIDs · 18 APs · 23:22:36`, then an unlabeled refresh icon (the only scan action, content-desc `Scan`). `Updated 0 seconds ago` chip below repeats the clock time.
- The list header `7 networks` repeats `7 SSIDs`.
- Spectrum labels overlap the curves (`VM1174274`, `VM7114223` over other triangles) and the yellow labels have low contrast on the light card (`103_wifi_list2.png`).
Suggested fix: one labelled "Scan" button; show the time once (in the "Updated" chip); use "7 networks" once; keep spectrum labels off the curves or move them to a legend.

### PHA-21 UX – Home: hero fills the viewport in landscape; no tool visible above the fold (medium, C3)
Landscape 640×353dp (`300_home_land.png`): hero plus title take the full height; "All Tools" heading and "Scroll for more tools" are the only content above the nav bar.
Similar at portrait phone-360 with font 1.3: the first tool starts about 55% down (`201_home_p360f13b.png`), and each tool is a full-width card, so the list is about 16 cards tall.
Suggested fix: collapse the hero to a compact header when height < 480dp, and give the grid a stable height per row (see PHA-22).

### PHA-22 UX – Home: uneven tile heights in the two-column grid (low, C1)
Speed Test (three description lines) is taller than Wake-on-LAN, so rows end at different heights (`03_home_scroll3.png`). Tile descriptions also vary between one and three lines.

### PHA-23 UX – Landscape tool screens: hero card uses about 28% of the height (low, C3)
Ping and Port Scanner in landscape (`301_ping_land.png`, `302_ports_land.png`): the same hero as portrait, with the host field starting at about y 600 of 1200 and the Recent chips clipped by the nav bar. The primary Start/Scan button is below the fold.
Suggested fix: use a single-line header in landscape or when the height is under 480dp.

## 3. Things that worked well
- Ping: validation messages are specific (`An IPv4 address needs four numbers from 0 to 255`, `Hostname or IP cannot contain spaces`, `Domain names cannot contain spaces`), and the field goes red. Start Ping is disabled while the field is empty.
- Ping result vs ICMP cross-check: min 13 ms matches `ping -c 10 1.1.1.1` on the tablet (min 13.651, avg 18.0). RTT chart and per-probe list are clear.
- Traceroute: completed 11 hops, Reached = Yes; hop 1 is `10.109.208.1` and matches the gateway; Hop Details and Raw Output tabs.
- DNS: `example.com` A returned NOERROR with 2 records; TTL and Copy value per record; Additional section collapses.
- Port Scanner: summary tiles Open/Closed/Filtered/Scanned add up (4+0+19 = 23); open-port rows show service name and banner.
- LAN Scanner: a full scan finishes in about 31 s with a live progress card; "Show uncertain probes" and host rows expand; Gateway filter and search (`Results (7 of 56)`) are correct; dark mode contrast is good.
- Wi-Fi Scanner: location prompt has a clear reason and a "Don't ask again" choice; the channel graph and sort/refresh controls are readable.
- Network Info: clean key-value cards, copy icon beside the network name, values match the Wi-Fi Scanner (IP `10.109.208.146`, network name `Brilliant Radiator!`).
- Home and More: bottom nav pins (Ping / DNS / Ports) work, the grid is readable at 360dp with font 1.3 and the bottom nav labels fit.
- Dark mode (C4) on Home and LAN: no contrast problems seen, hero gradient adapts.
- No FATAL or AndroidRuntime errors in logcat across all runs.

## 4. Proposal: true-summary design for LAN Scanner (no per-host data, no repeated number)

Principle: the summary card states the result once, in aggregate. Per-host data lives only in host rows. Inputs (subnet, timeout, concurrency) live only in the config card.

Current vs proposed:

| Element | Today (repeats) | Proposed (shown once) |
|---|---|---|
| Subnet | config title + summary meta + `/24` tile | config card only. Summary meta line shows duration only |
| Host count | headline + tile + list header | one headline: `56 hosts responded of 254 addresses` (or `56 of 254 addresses responded`). List header: `Hosts` (plus `7 of 56` only while filtering) |
| Scanned count | headline + tile | in the headline only |
| Uncertain count | button `(198)` | button `No reply: 198 · View` opens a sheet with the per-host probe list. Not inline |
| Verdict | status line + headline | status line only: `Scan complete · 30.8 s` |
| Terms | Confirmed / Discovered / Scanned | one term: "hosts" and "addresses" |
| Map | separate card, vendor and octet per node, +35 dead | a List/Map toggle inside the hosts section, same filtered data, nodes tappable (scroll to row), no vendor text |
| Actions | Re-scan, Copy, Share, Clear | Re-scan primary; Copy, Share, Clear as labelled secondary actions in one row |
| Latency | `0 ms` when no ping | shown only when an ICMP reply exists |

Result: the summary holds 3 facts (verdict and duration, responded/checked counts, the no-reply link); the subnet and settings are in the config card; the host list is the only place with per-host data.

## 5. Coverage (phone mode, per tool)

Legend: P = pass, F = fail (finding), NT = not tested this round, UNV = unverified. C1 = portrait phone-400 (deep), C2 = phone-360 + font 1.3 (visual), C3 = landscape (visual), C4 = dark (visual).

| Tool | Idle | Valid run / result | Empty / invalid input | Rerun | Cancel / rotate mid-run | Copy / share | C2 | C3 | C4 |
|---|---|---|---|---|---|---|---|---|---|
| Home (grid, nav, hero) | P (tile heights PHA-22) | n/a | n/a | n/a | n/a | n/a | PHA-21 (hero, 1-col) | PHA-21 (no tool above fold) | P |
| More sheet | PHA-10 (WoL missing), pins P | n/a | n/a | n/a | n/a | n/a | NT | NT | NT |
| Back / navigation | P (bottom nav, rail) | n/a | n/a | n/a | NT | n/a | NT | NT | NT |
| Ping | P | P (10/10, min matches ICMP) | P (validation), PHA-11 (unresolvable) | P | NT | NT (CSV icon present) | NT | PHA-23 | NT |
| Traceroute | P | P (11 hops, Reached) | NT | NT | NT | NT (icons present) | NT | NT | NT |
| DNS Lookup | P | P (example.com A, NOERROR) | P (spaces), NXDOMAIN NT | NT | NT | NT | NT | NT | NT |
| Port Scanner | P | P (gateway, 4 open) UNV vs independent scan (no nc on device) | NT | NT | NT | NT | NT | PHA-23 | NT |
| LAN Scanner | P | PHA-01, PHA-02, PHA-03 (runs: 56, 54, 51 hosts) | P (search), PHA-04 (no match) | P (Re-scan visible, not run) | NT this round (VERIFY-5 covered partial results) | NT this round (VERIFY-5 covered Copy/Share) | PHA-09 | NT | P (dark, summary) |
| Network Topology | P | PHA-19 (SNMP timeout to gateway) | NT | NT | NT | NT | NT | NT | NT |
| Wi-Fi Scanner | P (location prompt) | P (7 SSIDs, list, spectrum) | NT | NT | NT | NT | NT | NT | NT |
| Network Info | P | P (values match other screens) | n/a | n/a | n/a | NT | NT | NT | NT |

Not done this round: rotation or cancel mid-run for Ping, Traceroute, Port Scanner, Topology, Wi-Fi; DNS NXDOMAIN; Port Scanner validation; Topology input validation; C2/C3/C4 visual passes for Ping, Traceroute, DNS, Port Scanner, Topology, Wi-Fi, Network Info (only the screens listed above). These are UNVERIFIED in phone mode.

## 6. Also observed (not new findings)
- LAN host count varies between runs (56, 54, 51) on the same subnet, which is expected for ARP and mDNS-based discovery. Not reported as a bug.
- The tablet itself appears in the LAN list as `HUAWEI_MediaPad_T5-85cbe3.aieat.net` at `10.109.208.146`.
- LAN wake-on-LAN was not sent. Only the "Wake device" button position was inspected (PHA-08).

## 7. Restore (done)
`adb shell wm density reset` and `wm size reset` first; then accelerometer_rotation 1, user_rotation 0, font_scale 1.0, `cmd uimode night no`, location revoked for `net.aieat.netswissknife.debug`. Verified: physical density 320, no override; size 1200x1920; rotation auto; font 1.0; night no; `ACCESS_FINE_LOCATION` not granted; get-config back to the tablet profile (`sw600dp`); no FATAL in logcat.
