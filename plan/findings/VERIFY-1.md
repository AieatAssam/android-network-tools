# Device verification: GROUP 1 (app shell, Ping, Traceroute, DNS, Port Scanner, LAN Scanner, Wi-Fi Scanner, Topology)

Device: U8BBB20806201256 (Android 8.0, 1200x1920 @ 320 dpi, density 2), `net.aieat.netswissknife.debug` built from `plan/ux-remediation-2026-10-31` (HEAD 97af785). Build check: Ping help reads "anything above 0%", so the install is the remediated build.
Evidence: screenshots and uiautomator captures in `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/verA/` (file names below are relative to that directory).
Status legend: PASS, FAIL, PARTIAL, NOT-REPRODUCIBLE, UNVERIFIED. Deferred items get FAIL (deferred) or "deferred, no change" and are never PASS.

Device settings restored at the end: accelerometer_rotation 1, user_rotation 0, font_scale 1.0, night mode no, always_finish_activities 0, location permissions revoked again (they were not granted at start). No `am kill-all` was used. Source not edited. `AndroidRuntime` fatal count: 0.

## Verification table

| Finding | Result | Evidence |
|---|---|---|
| SHELL-01 Home hint over nav bar and cards | FAIL | "Scroll for more tools" still draws over the WHOIS Lookup and HTTP Probe captions at the bottom of Home (163-home-final.png). |
| SHELL-02 tablet width and two-pane | FAIL (deferred) | Landscape content is capped near 610 dp of the 960 dp width (14-ping-rot-land-b.png, 13-ping-rot-land-a.png). Portrait is one column, about 490 dp wide. Two-pane not built (WP01 deferred). |
| SHELL-03 IME covers primary action | PARTIAL | PASS: Ping Start Ping stays above the IME (38-final.png); Topology Discover Topology stays above the IME (141-topo-kbd.png). FAIL: DNS Look up is covered when the IME auto-opens after a dark-mode change (160-dark-dns.png); Port Scanner Scan is covered after rotation and after font 1.3 (88-now.png, 162-font13-ports.png). |
| SHELL-04 rail highlights More for Traceroute | NOT-REPRODUCIBLE (by design) | Traceroute opened from the More sheet keeps More highlighted (44-tr-end.png). WP15 documents this as intended. |
| SHELL-05 48 dp touch targets | PASS | Clickable ancestors measured in uiautomator: Help 96 px, Clear 96 px, Remove from recents 96 px, Clear recent hosts 96 px (48 dp at 2 px/dp). Visible glyphs are still small. |
| SHELL-06 Back from tool to Home | PASS | Back from Ping, Traceroute, DNS, Port Scanner, LAN and Wi-Fi returned to Home. |
| SHELL-07 Settings and About | UNVERIFIED | Not re-checked this pass (WP15 says it exists in the More sheet). |
| PING-01 rotation ends the run | PASS | Rotated at about 3/10 (34-slow-t4.png, host 10.109.208.200, 5 s timeout). Run continued and finished: "Statistics 10 Sent / 0 Received / 100% Loss" with the full 10 rows (35-rot-a.png, 36-rot-b.png, 38-final.png). The first rotation (on 1.1.1.1) also finished at 10/10 (13/14-ping-rot-land-*.png). |
| PING-02 "0%%" typo | PASS | Help reads "anything above 0% suggests congestion..." (01-ping-help.png). |
| PING-03 Start covered by keyboard | PASS | Start Ping stays above the IME (38-final.png). Enter starts the run. |
| PING-04 advanced reset and defaults | PARTIAL | "Reset to defaults" present under Advanced options (63-ping-adv.png). Showing the default next to each slider was deferred and is not present: FAIL (deferred). |
| PING-05 stale result after host edit | PASS | Editing the host to example.com under a 1.1.1.1 result shows "Showing the previous result for 1.1.1.1. Run again to update it for the new host." (61-ping-stale.png). |
| PING-06 colour semantics | PASS | 0% loss card uses a neutral gradient and neutral 0% (60-ping-111-done.png). 100% loss stays red (38-final.png). |
| PING interrupted label (WP02) | PASS | User Stop at 9 probes: "Stopped early after 9 probes. Statistics cover only the probes that were sent." (62-ping-stopped.png). Backgrounding mid-run (HOME) at 3 probes gave "Stopped early after 3 probes" (64-ping-bg.png). |
| PING-07 recent chip clipped with no cue | FAIL (deferred) | Last Recent chip still clipped mid-word with only a fade (34-slow-t4.png). WP02 deferred this. |
| PING-08 timeout chips 1s/3s, count slider | UNVERIFIED | 2s and 5s used. 1s and 3s not exercised. |
| PING-09 export, presets, history | deferred, no change | Share exists in the finished panel. Presets and per-target history are unbuilt product features. |
| TR-01 generic host message | PASS | "a..b": "A hostname cannot have an empty part between two dots". "bad!!": "Hostnames can only contain letters, digits, hyphens and dots". "999.999.999.999": "An IPv4 address needs four numbers from 0 to 255" (50-tr-a-b.png, 52-tr-999-start.png). |
| TR-02 raw output order and timeout glyph | PASS | Raw output lists hops 1..12 in order, with "* * *" for timeouts (uiautomator text of "Raw Trace Output"). |
| TR-03 geo contradicts RTT | PASS | 1.1.1.1 trace: "Some hop locations conflict with the measured round-trip times, so distance, route and speed are hidden." Approximation caveat shown. No emoji, no "Pkt Speed" (48-tr-1111-res.png). |
| TR-04 progressive hops | PASS | Counter rose during the run: 2, 16, 20, 26, 28 hops, then "Trace Results" (41-tr-rot-a.png and polls). |
| TR-05 failed card clears on edit | UNVERIFIED | Could not reproduce a "Trace failed" card in this pass (test-harness focus problems). Not counted. |
| TR-06 clear affordance | no change | Host field has the clear X. |
| TR-07 rotation mid-run | PASS | Trace to 10.109.208.250 rotated at 2 hops. Counter continued to 16, 20, 26, 28, then completed with 30 hops (41-44 png). |
| TR-08 export and history | deferred, no change | Share icon only. No export format, no history. |
| DNS-01 OPT badge | PASS | OPT record badge and name read "OPT". Additional section now collapsed by default (72-dns-a-scroll.png). |
| DNS-02 invalid domain | PASS | "bad host!!" gives inline "Domain names cannot contain spaces". Look up is disabled (accessibility enabled=false) (73-dns-invalid.png). |
| DNS-03 stale error cleared | PASS | NXDOMAIN card cleared after the domain was edited to example.org (75-dns-nx.png, 76-dns-edited.png). |
| DNS-04 NOERROR colour | PASS | "RCODE: NOERROR" renders in the neutral tone (72-dns-a-scroll.png). |
| DNS-05 keyboard on entry | PASS | Entering DNS Lookup did not show the IME (dumpsys input_method mInputShown=false; 70-dns-idle.png). The IME appears only after a config change (V-02). |
| DNS-06 record chips clipped under chevron | FAIL (deferred) | SOA chip still clipped under the chevron (75-dns-nx.png). WP04 deferred. |
| DNS-07 null MX note | PASS | "Null MX: this domain does not accept mail" (77-dns-mx.png). |
| DNS-08 resolver comparison, PTR, export | deferred, no change | Product features, not built. |
| DNS-09 other record types, resolvers | UNVERIFIED | Only A, MX and NXDOMAIN ran this pass. |
| PORTS-01 over-limit range | PASS | End port 20000: "Custom range must not exceed 10,000 ports" inline. Scan Ports enabled=false. No "Scan failed" card (82-ports-20000.png, 83-ports-20000-tap.png). |
| PORTS-02 generic host message | PASS | "bad!!": "Hostnames can only contain letters, digits, hyphens and dots" (84-ports-badhost.png). |
| PORTS-03 user-stopped scan banner | PASS | "Stopped at 153 of 1100 ports. Showing the results collected so far." (90-ports-stopped.png). |
| PORTS-04 rotation mid-scan | PASS | Rotated during a 1-1100 scan of 10.109.208.1. Scan continued to "Scanned 1100", duration 44415 ms (86-88 png, 89-after-rot.png). |
| PORTS-05 recent row scroll and fade | PARTIAL | Newest host is shown first. The edge cue is still missing (FAIL (deferred)): the last Recent chip on the Ports row fades with no scroll cue. |
| PORTS-06 "Well-known" as service name | PASS | Ports with no known name show no service label (94-wk-rows.png). |
| PORTS-07 help lists an "All" preset | PASS | Help text: "Common services, Well-known 1–1024, Web, Databases, Mail, Remote access" (170-ports-help.png). No "All" preset is named. |
| PORTS-08 leave and return keeps result | PASS | Scan 1.1.1.1, Back to Home, re-open from the Home tile and the rail: "Scan Results" and Host 1.1.1.1 still shown (96-ports-leave-return.png). |
| PORTS-09 IME covers Scan | PARTIAL | Enter starts the scan. After rotation or font 1.3 the IME auto-opens and covers Scan (88-now.png, 162-font13-ports.png). |
| PORTS-10 status-bar scrim | UNVERIFIED | Not captured. |
| PORTS-11 copy snackbar | UNVERIFIED | Copy report present. No snackbar captured. |
| PORTS-12 FILTERED tag contrast | PASS (visual) | Dark maroon on pale pink, readable (94-wk-rows.png). Not measured. |
| LAN rotation mid-scan | PASS | /24 scan rotated at 50/254. Counter kept rising: 130, 227. Completed with 65 confirmed, 189 uncertain (101-103 png). |
| LAN-01 Back with an expanded row | PASS | Back collapsed the expanded row and stayed on LAN Scanner. Scan Complete and results kept (107-lan-row-open.png, 108-lan-after-back.png). |
| LAN leave and return | PASS | Back to Home and back via the tile: Scan Complete and 10.109.208.0/24 still shown (109-lan-return.png). |
| LAN-02 Gateway label vs latency | PASS | 16 px gap between "Gateway" and "Latency 40 ms" on the same row (114-lan-gwfilter.png). |
| LAN-03 raw exception text | PASS | Reads "10.109.208.2: TCP probe timed out, port 80" (111-lan-uncertain.png). |
| LAN-04 ARP-only IPs missing | deferred, no change | Needs per-IP evidence. WP06 found no code defect. Not re-run (UNVERIFIED). |
| LAN-05 pull-to-refresh discards results | PASS | Downward swipe at the top: no restart, "Scan Complete" kept (110-lan-pull.png). |
| LAN-06 "+N" chip over nav bar | PASS | "+44 more..." sits above the search field, clear of the nav bar (112-lan-map-end.png). |
| LAN-07 search, filter, sort | PASS | Search, All/Gateway/Has Ports filters and IP/Latency/Hostname/Vendor/Port count sorts present; Gateway gave "Results (1 of 65)" (114-lan-gwfilter.png). |
| WIFI-01 2.4 GHz labels overlap | PASS | Five SSIDs: labels stacked in tiers and readable. Minor: VM1174274 label sits on a triangle edge (123-wifi-spectrum.png). |
| WIFI-02 no-match search | PASS | "0 networks" and "No networks match "ZZZnomatch". Clear the search to see every network." Spectrum card hidden (125-wifi-nomatch2.png). |
| WIFI-03 permission gate title | PASS | "Wi-Fi Scanner" title above the "Location Permission Required" card (121-wifi-gate.png). |
| WIFI-04 animated reorder, hold while touched | UNVERIFIED | Reorder animation not observed. Hold-while-touched was deferred by WP07. |
| WIFI-06 best-channel recommendation | PASS (plausible) | "Channel 11" recommended with an AP near 11 and none within 4 channels. Not re-derived from raw scan data (123-wifi-spectrum.png). |
| WIFI-07 max speed label | PASS | "Standard max (theoretical)": "Up to 289 Mbps at 20 MHz" for Ch 2, 20 MHz, Wi-Fi 4 (131-bssid-detail.png). |
| Wi-Fi rotation during first scan | UNVERIFIED | Manual refresh finishes in about 1 s; rotation after refresh kept "4 SSIDs · 9 APs". Mid-scan rotation not captured. |
| TOPO-01 Discover visible and Enter starts | PASS | Discover Topology is visible with the card open and with the IME open (141-topo-kbd.png). Enter starts discovery ("Scanning… 0 nodes found", 142-topo-enter.png). Cancel works: "Discovery canceled. 0 discovered nodes retained as partial results." (145-topo-cancel.png). |
| TOPO-02 seed field icon and clear | PASS | Router icon and clear X present (141-topo-kbd.png). |
| TOPO-03 failure message and seed validation | PASS | "SNMP v2c GET ... failed: no response within 3000 ms ..." with Retry. Seed "bad!!" gives "Hostnames can only contain letters, digits, hyphens and dots" (146-topo-badseed.png). |
| TOPO-04 community string | PASS | Masked with a show-password toggle (141-topo-kbd.png). |
| Process death (force-stop, WP15) | PARTIAL (method limit) | `am force-stop` gave a cold start (pid 22869 then 1069). Recent chips survived (DataStore): PASS. Form inputs did not survive: the Ping host "example.com" and the Port preset were reset (150-151, 170 png). This matches SavedStateHandle being dropped by a user force-stop. Not a code FAIL. With `always_finish_activities=1` (activity recreation, not process death), the Port host 1.1.1.1 was restored. Results are not restored (WP15 deferred). |
| Logcat | PASS | No AndroidRuntime fatal exceptions across the session. |

## NEW issues (V-)

| ID | Type | Severity | Repro | Expected vs actual | Evidence |
|---|---|---|---|---|---|
| V-01 | BUG | low | Ping: put the host field into the state "10.109.208.25010.109.208.200" (text appended to an existing value), press Enter. | Expected: invalid text is rejected or not saved. Actual: no run starts, but the malformed value is added as a Recent chip ("10.109.208.25010.109.208..."). Cause not isolated. | 33-slow-t4.png, 34-slow-t4.png |
| V-02 | UX | medium | Open DNS Lookup or Port Scanner, then rotate, or toggle dark mode, or set font_scale 1.3. | Expected: the IME does not re-open after a configuration change. Actual: the first field regains focus and the IME opens, covering Look up or Scan Ports. | 160-dark-dns.png, 88-now.png, 162-font13-ports.png |
| V-03 | UX | medium | DNS Lookup: look up nosuch-zz-test.example. | Expected: NXDOMAIN shown as an error or neutral card. Actual: green success card with "RCODE: NXDOMAIN" in green. | 75-dns-nx.png |
| V-04 | BUG | low | Traceroute 1.1.1.1 (ICMP, 30 max hops). | Expected: the trace stops at the destination. Actual: hop 11 and hop 12 both show 1.1.1.1 (21 ms), so the trace continues past the destination. | Raw output text (uiautomator), 48-tr-1111-res.png |
| V-05 | UX | medium | Traceroute to unreachable 10.109.208.250. | Expected: a clear explanation. Actual: 30 hops, each reported as 10.109.208.146 (the tablet itself) at about 3 s, "Reached: No", 45.9 s total, with no explanation. | 44-tr-end.png, 45-tr-results.png |
| V-06 | GAP | low | Network Topology: run discovery, cancel, leave the screen, return. | Expected: last state kept, as Ping and LAN now keep results. Actual: state resets to the empty state. | 143-topo-after-leave.png |
| V-07 | UX | low | Wi-Fi Scanner: tap Clear in the search field. | Expected: focus stays off the field. Actual: the IME opens over the results. | 128-wifi-clear.png |
| V-08 | UNVERIFIED | low | Pressing Enter (keyevent 66) with no text field focused navigated to Home, twice. | Expected: no navigation. Cause not isolated (possibly harness focus). | Not captured |

## Harness notes
- Several taps landed on the Huawei launcher or Weather app during the pass because of Back or Home navigation. No grants were made in those apps. The stray Weather permission prompt was not accepted.
- The Wi-Fi search field showed stray text ("t5") once. It was not reproduced, so it is not counted as an issue.

## Summary
- Checked against WP01-WP07 and WP15 device checks, plus the caller items (rotation for Ping, Traceroute, Port, LAN and Wi-Fi; Topology Discover; LAN Back; leave and return; force-stop).
- PASS: Ping rotation (PING-01), Traceroute rotation and streaming (TR-07, TR-04), Port rotation and stopped banner (PORTS-03/04), LAN rotation and Back-with-row (LAN-01), Wi-Fi gate title and no-match and 289 Mbps, Topology Discover visible and Enter.
- Still open: SHELL-01 Home hint overlap, SHELL-02 two-pane (deferred), DNS-06 and PORTS-05 chip clipping (deferred), PING-07 (deferred).
- Keyboard: Ping and Topology keep the primary action above the IME. DNS and Port Scanner lose it after a configuration change (V-02).
- Force-stop: recents survive. Form inputs do not survive a force-stop, which is expected for SavedStateHandle; not a code FAIL.
- New issues: V-01 to V-08 (chip pollution, Traceroute past-destination hop and unreachable output, NXDOMAIN colour, Topology state loss).
- UNVERIFIED: TR-05, LAN-04, WIFI-04, mid-scan Wi-Fi rotation, PORTS-10/11, PING-08, DNS-09, SHELL-07.
