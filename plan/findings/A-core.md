# QA findings: A-core (app shell, Ping, Traceroute, DNS Lookup)

Device: U8BBB20806201256, 1200x1920 @ 320 dpi (density 2.0), Android 8.0, Wi-Fi with internet. Build: `net.aieat.netswissknife.debug` from main.
Screenshots and scratch: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/qaA/` (referenced below by file name).
Targets used: example.com, 1.1.1.1, 10.109.208.250 (LAN, unreachable, tablet's own /24), 10.109.208.1 (gateway, seen in traceroute).
Device settings were restored at the end: user_rotation 0, accelerometer_rotation 1, font_scale 1.0, night mode no. No crashes logged (`AndroidRuntime:E` empty).

Status legend: VERIFIED (observed on device), UNVERIFIED (could not confirm or not exercised).

---

## Findings

### App shell (home, rail, navigation, theme)

**SHELL-01** | UX | low
- Repro: launch app, look at the bottom 80 px on the Home screen.
- Expected: hint text sits clear of the system navigation bar and of the card content.
- Actual: "Scroll for more tools" is drawn over the system nav bar and over the bottom cards; the "Domain and IP registration lookup" and "HTTPS request tester..." captions are cut off underneath it.
- Screenshot: `01-home.png`, `10-back-once.png`.
- Fix: add bottom content padding equal to navigation bar inset, or drop the floating hint and let the grid scroll.

**SHELL-02** | UX | medium (tablet)
- Repro: open Ping, Traceroute or DNS in portrait.
- Expected: a tablet-aware layout (two panes, or a capped content width for form and results).
- Actual: a single column stretched across ~1000 px. Traceroute's configuration card is ~1300 px tall, so the results start below the fold (you must scroll ~1000 px before seeing the first hop). Ping's Start button and results are similarly pushed down.
- Screenshots: `30-tr-idle.png`, `34-tr-complete.png`, `02-ping-idle.png`.
- Fix: use a master/detail or two-column layout at width >= 600 dp (form left, results right), or cap the form at ~600 dp.

**SHELL-03** | UX | medium
- Repro: open Ping (or Advanced options on Ping) in landscape.
- Expected: the primary action is reachable without scrolling or dismissing the keyboard.
- Actual: in landscape the Start Ping button sits below Advanced options; on DNS the Look up button is fully covered by the soft keyboard (only its top edge is visible), and the keyboard auto-opens on screen entry.
- Screenshots: `12-ping-landscape-run.png`, `74-landscape-dns.png`.
- Fix: keep the primary button in a sticky bottom bar or use `windowSoftInputMode` / `imePadding()` so the button stays above the IME.

**SHELL-04** | UX | low
- Repro: More > Traceroute.
- Expected: the navigation rail highlights the tool's group consistently.
- Actual: rail highlights "More" while Traceroute is open. Traceroute has no rail entry, so it is only reachable from the More sheet.
- Screenshot: `30-tr-idle.png`.
- Fix: highlight the More item for sheet-launched tools with a label such as the tool name, or add Traceroute to the rail (see GAP-01).

**SHELL-05** | A11Y | medium
- Repro: uiautomator dump on Ping and Traceroute result areas.
- Expected: all tappable icons at least 48 dp (96 px at 320 dpi).
- Actual: measured bounds: Ping/Traceroute info (i) and Clear (X) icon buttons 48x48 px (24 dp); "Remove from recents" X 28x28 px (14 dp); "Clear recent hosts" 36 px (18 dp). Content descriptions exist for these icons ("Clear", "Help", "Remove from recents", "Clear recent hosts"), which is good.
- Fix: enlarge hit areas to 48 dp (IconButton with `Modifier.minimumInteractiveComponentSize()`).

**SHELL-06** | UX | low
- Repro: Home > Ping > Back.
- Result (VERIFIED): Back from a tool returns to Home; Back from Home exits the app. Behaviour is correct.
- Status: WORKED WELL (not a bug).

**SHELL-07** | UNVERIFIED
- Process death and restore: text in the Ping host field survived `am kill` plus relaunch. I could not confirm the process was actually killed (the app was resumed, not cold-started), so this is UNVERIFIED as a process-death test.
- Screenshot: `72-after-kill.png`.
- Dark mode: Ping screen verified readable with good contrast (`71-dark-ping.png`). Home dark mode captured (`70-dark-home.png`) but not reviewed closely.
- Large font (1.3x): DNS screen reflows without clipping the main elements (`73-font13-dns.png`).
- Settings and About: no entry located on the Home screen or rail. The More sheet was only partially inspected (Ping and Traceroute seen). UNVERIFIED whether a settings/about screen exists.

---

### Ping

**PING-01** | BUG | high
- Repro: Ping `1.1.1.1`, count 10 (default). When the progress shows 6/10, rotate the device (portrait to landscape and back, or `adb shell settings put system user_rotation 1` then 0). Wait 15 s.
- Expected: the run continues to 10/10, or the screen shows an explicit "interrupted, partial results" state.
- Actual (VERIFIED, reproduced twice): after rotation the "Pinging... 6/10" card disappears, "Start Ping" is re-enabled, and the Statistics card is presented as a completed result with Sent 6 / Received 6 / 0% loss. No progress continues. A second run on 10.109.208.250 that was rotated at 3/10 ended with "4 Sent, 0 Received, 100% Loss", a misleading packet-loss figure for an interrupted run.
- Screenshots: `11-ping-unreach-run.png` (running), `12-ping-landscape-run.png` (state already lost), `13-ping-portrait-back.png` (stopped at 4), `15-after-rotate-run.png` (stopped at 6).
- Suspected cause: run scope is tied to the composition or Activity and is cancelled on configuration change, and the partial state is then rendered as final. UNVERIFIED in code.
- Fix: keep the run in a ViewModel-owned scope (`viewModelScope`) or persist the run state; if it is cancelled, label the result "Interrupted" and do not compute loss from partial data.

**PING-02** | BUG | low
- Repro: Ping > info (i) button in the header > "What it does / Key parameters / Reading the results".
- Expected: "anything above 0% suggests ...".
- Actual (VERIFIED in UI tree): the text reads `anything above 0%% suggests congestion or a firewall dropping packets.` (double percent sign from a format string that was not formatted).
- Screenshot: `21-info.png` (sheet opened; the bullet text is in the uiautomator dump).
- Fix: use `%` in the string resource (or `%%` only where a format is applied) and check the other resources for the same pattern.

**PING-03** | UX | medium
- Repro: Ping > tap the Host field.
- Expected: the Start Ping button remains visible above the keyboard.
- Actual (VERIFIED): with the soft keyboard open the Start Ping button is cut off at the keyboard edge (only the top ~40 px visible). Pressing the keyboard IME action (Enter) does start the ping, which works.
- Screenshot: `03-ping-typed-kbd.png`, `04-ping-enter-run.png`.
- Fix: see SHELL-03.

**PING-04** | UX | medium
- Repro: Ping `example.com`, count 10, run to completion.
- Expected: the primary action and advanced controls behave predictably.
- Actual (VERIFIED): a single tap anywhere on the Advanced option sliders sets the value (for example, one stray tap moved payload from 56 to 736 bytes and another moved TTL to 128). There is no reset to defaults, and the change is silent because the values are only shown as labels. Advanced options stay expanded across runs.
- Screenshots: `20-advanced.png`, `22-continuous.png`, `24-now.png`.
- Fix: add a "Reset" action, show the default value near each slider, and consider a drag-only or long-press model for sliders in a collapsible section.

**PING-05** | UX | low
- Repro: run Ping to example.com, then type `bad host!!`.
- Result (VERIFIED): the inline error "Hostname or IP cannot contain spaces" appears under the field, Start Ping is disabled, and the previous Statistics card is still shown below for example.com. The inline error and the stale result are both correct behaviour but the stale result looks current.
- Screenshot: `09-ping-badip.png`.
- Fix: dim or label the previous result when the input has changed.

**PING-06** | UX | low
- Statistics card uses a pink-to-green gradient on successful runs (pink on the left suggests an error). The 0% loss value is shown in maroon, while the 100% loss is red. Colour semantics are unclear.
- Screenshot: `06-ping-done-later.png`.
- Fix: use a neutral gradient for success and reserve the red/pink hue for loss above a threshold.

**PING-07** | UX | low
- Recent chip row clips the last chip mid-word ("examp") with no fade or scroll cue. Recent X (remove) targets are 14 dp (see SHELL-05).
- Screenshot: `20-advanced.png`.

**PING-08** | UNVERIFIED
- Not exercised: Timeout chips 1s, 3s, 5s (only 2s was selected); Count slider drag; tapping a Recent chip to re-run; the copy icon on Ping Results; the 999.999.1.1 invalid-IP case (my input was appended to existing text, so the test was invalid).

**PING-09** | GAP | medium (product)
- No export or share of the Statistics card (Ping Results has a copy icon, but Statistics has none). No saved targets or presets, and no history beyond the Recent chips. For a network engineer the typical expectations are share/export of the summary (CSV or text), a per-target history with timestamps, and a "copy as command" option.

**Works well (Ping)**
- Accurate timing: min/avg/max from the app (14 / 19.3 / 26 ms over 10 probes) agree with `adb shell ping -c 10 example.com` (12.9 / 20.0 / 31.8 ms), taken at a different time.
- Continuous mode: toggle works, shows "Pinging continuously... / Pings sent: N", kept running at 4 then 10 sent over 16 s, Stop works and leaves the last statistics (VERIFIED).
- Unreachable hosts: per-packet "Timeout" and "Host unreachable from 10.109.208.146" messages are clear and specific.
- Empty state: Start Ping is disabled and the "Enter a host to ping" empty state is clear.
- Recent chips with per-chip remove and clear-all: good discoverability.
- Dark mode is well-contrasted.

---

### Traceroute

**TR-01** | BUG | medium
- Repro: Traceroute, host field: clear with X, type `a..b` (or `999.999.999.999`), observe the error.
- Expected: a message about the actual problem (invalid label or invalid address), or a pass-through to the resolver error.
- Actual (VERIFIED): "Hostname or IP cannot contain spaces" is shown for inputs that contain no spaces. The message looks like a generic fallback.
- Screenshots: `41-tr-badip2.png`, `42-tr-err-variants.png`.
- Fix: map each validation failure to its own string (empty, spaces, invalid label, out-of-range IPv4).

**TR-02** | BUG | medium
- Repro: Traceroute `1.1.1.1` to completion, open Raw Output.
- Expected: lines in hop order 1..11.
- Actual (VERIFIED): the raw text lists hops 1, 2, 3, 5, 6, 7, 8, 9, 10, 11 and then `4 * *` at the end. The timeout line also shows two asterisks where the Hop Details row shows three (`* * *`).
- Screenshot: `36-raw.png`.
- Fix: keep the raw output in the same hop-index order as the list, and use the same timeout representation for both.

**TR-03** | UX / accuracy | medium
- Repro: Traceroute `1.1.1.1`. Read Journey Intelligence and hop 11.
- Observed (VERIFIED): hop 11 `1.1.1.1 (one.one.one.one)` is labelled "Brisbane, Australia", the journey card shows "17k km distance, Route: United Kingdom -> Australia", and "Pkt Speed ~100% of [sun emoji]". The same trace shows 13 ms RTT at hop 11 and ~15 ms at hop 10 in London. A 17,000 km path cannot produce 13 ms RTT, and the app's own footnote says the speed-of-light estimate is ~200 km/ms. The geolocation is for an anycast address and is presented as fact.
- "Pkt Speed ~100% of [sun]" has no clear definition.
- Screenshots: `35-tr-results.png`, `36-raw.png`.
- Fix: flag geolocation as approximate, detect anycast or low-confidence locations, or remove the distance/route cards when the RTT contradicts the geolocation. Define or remove "Pkt Speed" and drop the emoji.

**TR-04** | UX | medium
- Repro: Traceroute, start a trace, check the result area while it runs.
- Expected: progressive hop list as each hop finishes.
- Actual (UNVERIFIED in detail): the run completes in ~3-4 s for 1.1.1.1 and the hop list appears only after completion, so progressive display could not be confirmed. Cancel mid-run works: "Trace canceled. 10 hops retained as partial results." (VERIFIED, `32-tr-run.png`).
- Fix: stream hops into the list while running.

**TR-05** | UX | low
- Repro: Traceroute, enter an unknown host, then edit the field and start again.
- Result (VERIFIED): the "Trace failed" card with reason and Clear/Retry remains visible after editing. The message itself is good: `Unable to resolve host "999.999.999.9991.1.1.1": No address associated with hostname`.
- Fix: clear the failed card when the host text changes.

**TR-06** | UX | low
- Tapping the Host field does not clear the existing text. Repeated tests appended text (e.g. `1.1.1.1999.1.1.1`). This is standard, but the X button is the only clear affordance and is easy to miss.
- Screenshot: `40-tr-badip.png`.

**TR-07** | UNVERIFIED
- Rotation during a run: my first attempt did not test it (the trace finished in 3.6 s before the rotation at ~6 s). A second attempt also finished before the rotation point. Retest with a longer trace (e.g. UDP or a host that times out per hop) is needed.
- Not exercised: UDP probe protocol; Max hops, Timeout per hop, Probes per hop, MTU discovery and Packet size sliders; share icon; Clear.

**TR-08** | GAP | low
- No hop-list export or copy beyond the share icon; no reverse-DNS toggle; no saved trace history. Expected for network engineers: export to text/CSV, per-run history and compare.

**Works well (Traceroute)**
- Hop list: colour-coded RTT badges, hostnames from reverse DNS, AS and location chips, expandable per-hop details (IP, RTT, status, location, ISP, ASN, coordinates).
- Summary cards: hops, time, reached, resolved IP, average RTT, fastest and slowest hop, timeout percentage.
- Raw Output tab and a share icon are present.
- Unknown host failure: clear message plus Retry and Clear.
- Partial results kept after cancel.

---

### DNS Lookup

**DNS-01** | BUG | medium
- Repro: DNS Lookup, `example.com`, record type A (or MX), System DNS. Look at the "Additional" card.
- Expected: the OPT pseudo-record has a type badge of "OPT".
- Actual (VERIFIED, reproduced on A and MX): the OPT record shows a badge reading "TXT" next to the name "OPT", and TTL 0s. The Additional card is expanded by default, which surfaces this pseudo-record to users.
- Screenshots: `58-dns-a-result.png`, `62-mx-scroll.png`.
- Fix: map RR type 41 to "OPT" in the badge, and consider collapsing the Additional section by default or hiding OPT.

**DNS-02** | UX | medium
- Repro: DNS Lookup, type `bad host!!`.
- Expected: inline validation, Look up disabled, error in plain language (like Ping and Traceroute).
- Actual (VERIFIED): Look up stays enabled. After pressing it, the card reads "Lookup failed / Invalid DNS domain name: Invalid internationalized domain label" for a string with a space.
- Screenshots: `63-dns-invalid.png`, `64-dns-invalid-lookup.png`.
- Fix: validate on input, as Ping does, and use the message "Domain names cannot contain spaces".

**DNS-03** | UX | medium
- Repro: DNS Lookup; leave `Lookup failed` after an invalid input and then change to a valid domain (or switch to Custom server).
- Expected: the stale error is cleared.
- Actual (VERIFIED): the "Lookup failed / Invalid DNS domain name..." card persists after typing `example.org` and opening the DNS server dropdown (Custom server...).
- Screenshot: `65-dd.png`, `66-custom.png`.
- Fix: clear result/error state when the query or server changes.

**DNS-04** | UX | low
- "RCODE: NOERROR" for successful lookups is coloured in the error hue (maroon). Use the success/neutral colour.
- Screenshot: `58-dns-a-result.png`.

**DNS-05** | UX | medium
- The text field receives focus on entry, so the keyboard opens immediately and covers the lower half of the screen (including Look up on phone-width layouts and entirely in landscape). See SHELL-03.
- Screenshots: `50-dns-idle.png`, `74-landscape-dns.png`.

**DNS-06** | UX | low
- The Record type chips scroll horizontally, but the last visible chip (SOA) is clipped under the chevron overlay, and there is no scroll cue beyond it. The record type list is also incomplete in the row (SOA is only reachable by scrolling). Consider a segmented or wrap layout on tablet, where there is room for all types.
- Screenshot: `51-dns-a.png`.

**DNS-07** | UX / GAP | low
- MX for example.com returns priority 0, mail server `.` (null MX per RFC 7505). The app shows the raw values without explanation. Add a note: "Null MX: domain does not accept mail".
- Screenshot: `62-mx-scroll.png`.

**DNS-08** | GAP | medium (product)
- No side-by-side resolver comparison (e.g. System vs Google vs Cloudflare for the same name), no PTR or reverse lookup entry point (UNVERIFIED for IP input), no "query all common records" action, and no export of the raw response (beyond the "Raw DNS response" card).

**DNS-09** | UNVERIFIED
- Not completed: resolver choice Google/Cloudflare results (the dropdown was opened and displayed, but the back key went to Home before a run); Custom server text entry and validation (the field appeared, but I could not type a server address reliably); record types AAAA, TXT, CNAME, NS, SOA (only A and MX were run); rotation during lookup; copy/share on results; Retry button on a real network failure.
- Empty input: Look up is disabled (VERIFIED, `50-dns-idle.png`).
- Verification of A records: the app listed 172.66.147.243 and 104.20.23.154 for example.com. `adb shell ping example.com` on the tablet resolved to 104.20.23.154, which is in the app's set. The lookup time (30-92 ms) is consistent with the device's resolver.

**Works well (DNS Lookup)**
- Resolver dropdown: System DNS, Google, Cloudflare, OpenDNS, Quad9 and Custom, each with a short description.
- Result header: domain, record-type chip, resolver used, RCODE, flags, server address, record count and timing.
- Share and clear on the result card, and Recent chips.

---

## Per-tool "tested" checklist

### App shell
| Item | Status | Notes |
|---|---|---|
| Home idle / grid / scroll | Tested | SHELL-01 |
| Rail navigation (Home, Ping, DNS, Ports, More) | Tested | Traceroute via More; SHELL-04 |
| Back from tool to Home; Back from Home exits | Tested | SHELL-06 (works) |
| Portrait / landscape | Tested | SHELL-02, SHELL-03 |
| Dark mode | Tested (Ping) | Good |
| Font scale 1.3 | Tested (DNS) | No clipping in DNS |
| Rotation during run | Tested (Ping: BUG; Traceroute: UNVERIFIED) | PING-01, TR-07 |
| Process death / restore | UNVERIFIED | SHELL-07 |
| Settings / About | UNVERIFIED | No entry located |
| Touch targets | Tested | SHELL-05 |
| Content descriptions | Tested (UI tree) | Good on icons |

### Ping
| Item | Status | Notes |
|---|---|---|
| Idle state | Tested | Good empty state |
| Valid host (example.com) | Tested | Accurate vs adb ping |
| Empty input | Tested | Start disabled |
| Invalid input with spaces | Tested | Inline error (good) |
| Invalid IP without spaces (999.999.1.1) | UNVERIFIED | Input appended to existing text; re-test needed |
| Unreachable LAN host (10.109.208.250) | Tested | Timeout and "unreachable" messages good |
| Cancel mid-run (Stop) | Tested | Works |
| Rerun | Tested | Works |
| Rotate during run | Tested | PING-01 (BUG) |
| Back navigation | Tested | Works |
| Clear button | Tested | Works |
| Keyboard IME action | Tested | Enter starts ping; PING-03 |
| Keyboard covering Start | Tested | PING-03 |
| Continuous toggle | Tested | Works |
| Count slider | Not exercised | Only default shown |
| Timeout chips | Partly (2s only) | 1s, 3s, 5s UNVERIFIED |
| Advanced sliders (payload, TTL, interval) | Tested | PING-04 |
| Recent chips (select, remove, clear) | Partly | Remove and clear viewed; re-select UNVERIFIED |
| Info sheet | Tested | PING-02 (%%) |
| Copy on results | UNVERIFIED | Icon present |
| Dark mode | Tested | Good |
| Landscape | Tested | Start below fold; SHELL-03 |
| Tablet layout | Tested | SHELL-02 |
| Result accuracy | Tested | Consistent with adb ping |

### Traceroute
| Item | Status | Notes |
|---|---|---|
| Idle state | Tested | Good config layout |
| Valid host (1.1.1.1) | Tested | 11 hops reached |
| Empty input | Tested | Start disabled |
| Invalid input (spaces / no spaces) | Tested | TR-01 |
| Unknown host | Tested | Good message (TR-05) |
| Cancel mid-run | Tested | Partial results kept (positive) |
| Rerun | Tested | Works |
| Rotate during run | UNVERIFIED | TR-07 |
| Back navigation | Partly | Via More; not separately tested |
| Clear | UNVERIFIED | Button present |
| Keyboard IME action | Partly | Not conclusively verified |
| ICMP / UDP chips | Partly | ICMP only |
| Max hops, timeout, probes, MTU, packet size | UNVERIFIED | Not exercised |
| Hop list + expansion | Tested | Good |
| Raw output | Tested | TR-02 |
| Journey intelligence | Tested | TR-03 |
| Share | UNVERIFIED | Icon present |
| Tablet layout | Tested | SHELL-02 |

### DNS Lookup
| Item | Status | Notes |
|---|---|---|
| Idle state, keyboard auto-open | Tested | DNS-05 |
| Empty input | Tested | Look up disabled |
| Valid domain (example.com, A) | Tested | Result good |
| MX record | Tested | DNS-07 |
| AAAA, TXT, CNAME, NS, SOA | UNVERIFIED | Chips visible; not run |
| Invalid input (bad host!!) | Tested | DNS-02 |
| DNS server dropdown | Tested | Options good |
| Google / Cloudflare results | UNVERIFIED | Not run |
| Custom server | Partly | Opened; validation UNVERIFIED |
| Stale error state | Tested | DNS-03 |
| Cancel / rerun | UNVERIFIED | Not exercised |
| Rotate during lookup | UNVERIFIED | Not exercised |
| Back navigation | Tested | Works |
| Clear / Recent | Tested | Works |
| Copy / share | UNVERIFIED | Share icon present |
| Dark mode | Not separately exercised | |
| Landscape | Tested | DNS-05 |
| Font scale 1.3 | Tested | Good |

---

## Product-owner gaps (summary)
- GAP-01: Traceroute is not in the rail; it is reachable only from More. Promote it (or add a rail entry for the tool family).
- GAP-02: Tablet: all tools are a single column; add a two-pane layout for Ping, Traceroute and DNS (form left, results right).
- GAP-03: Ping: no export or share for statistics; no saved targets or presets; no history with timestamps.
- GAP-04: Traceroute: no export of hops, no trace history, no reverse-DNS toggle.
- GAP-05: DNS: no resolver comparison; no reverse (PTR) entry point; no export of the raw response.
- GAP-06: Shell: no visible Settings or About entry found (UNVERIFIED).

## Things that worked well
- Ping's Continuous mode with live statistics and an RTT chart.
- Ping/Traceroute/DNS error messaging for unreachable and unknown hosts (Ping's per-packet "unreachable" lines; Traceroute's "Unable to resolve host" message with Retry and Clear).
- Traceroute hop list: colour-coded RTT, reverse DNS, expandable details and a raw-output view; partial results kept on cancel.
- DNS resolver dropdown with descriptions and the result header (RCODE, flags, server used, timing).
- Consistent Material 3 look across tools; dark mode contrast; large-font reflow on DNS.
- Back navigation from tools returns to Home, and the app exits correctly from Home.
- Recent-host chips with per-item removal and clear-all.
