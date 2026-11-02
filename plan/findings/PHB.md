# PHB – phone-mode QA, network tools (tablet emulating a phone)

Build: HEAD de5a39c (debug, `net.aieat.netswissknife.debug`), tablet U8BBB20806201256, Android 8.0, 1200x1920 px.
Screenshots and UI dumps: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/phb/` (`*.png` + `*.xml`; file names prefixed `c1_`, `c2_`, `c3_`, `c4_`).
Scope: TLS Inspector, WHOIS, HTTP Probe, Subnet Calculator, mDNS Browser, Speed Test, Wake-on-LAN, MAC Lookup. Known and not repeated: V5-01..V5-06 (VERIFY-5), PHA-* (PHA.md). Two-pane tablet layout out of scope.
Method: density change only (no `wm size`). Results and duplication quotes are from the live UI dumps.

## 1. Configurations

| Id | Setup | get-config (first line, abbreviated) |
|---|---|---|
| C1 | density 480, portrait, font 1.0, light | `sw400dp-w400dp-h593dp-normal-notlong-...-port-notnight-xxhdpi` |
| C2 | density 533 + font_scale 1.3, portrait, light | `sw360dp-w360dp-h531dp-normal-notlong-...-533dpi-port-notnight` (font 1.3 is a setting; confirmed with `settings get system font_scale` = 1.3) |
| C3 | density 480, landscape (user_rotation 1), light | `sw400dp-w640dp-h353dp-normal-notlong-...-land-notnight` |
| C4 | density 480, portrait, dark (`cmd uimode night yes`) | `sw400dp-w400dp-h593dp-normal-notlong-...-port-night` |

Restore (done at the end, in the order the brief requires): `wm density reset`, `wm size reset`, accelerometer_rotation 1, user_rotation 0, font_scale 1.0, `uimode night no`, `pm revoke ... ACCESS_FINE_LOCATION`. Post-restore get-config is the tablet config (sw600dp), density 320.

Coverage legend: F = deep functional test done, V = visual pass only, - = not covered in this run.

## 2. Per-tool checklist (config x tool)

| Tool | C1 (portrait 400) | C2 (360 + font 1.3) | C3 (landscape) | C4 (dark) |
|---|---|---|---|---|
| TLS Inspector | F: idle, invalid host (space), invalid port 70000, valid run to example.com (result read to bottom), Cancel mid-run, IME Enter, clear, recents | V: idle, invalid host error | Rotate to landscape mid-run: run survived and ended in error card (Retry present). Static landscape pass not done | V: idle |
| WHOIS | F: idle, invalid "bad domain!!" (no inline error), run on `AS15169` (RDAP), raw toggle | V: idle | - | - |
| HTTP Probe | F: idle, plain http:// (inline error), run https://example.com (GET), Overview, Security tab | V: idle | - | - |
| Subnet Calculator | F: 192.168.1.0/24 preset, result, binary breakdown | V: idle, preset result | - | - |
| mDNS Browser | F: idle, Scan, running (hero tiles, spinner), done (notice, list) | V: idle | - | - |
| Speed Test | F: one full run in C3 landscape, rotated to portrait mid-run (run continued), results read to bottom, Share results opens chooser | V: idle | Run started and mid-run (landscape) | - |
| Wake-on-LAN | F: form, invalid MAC (inline error), valid MAC (Send enabled, NOT tapped), Advanced options, saved device add/delete (test device, removed afterwards) | V: idle | - | V: idle |
| MAC Lookup | F: invalid input (count error), valid OUI result, Clear | V: idle | - | V: result |

Not done for any tool: Cancel mid-run on Speed Test and mDNS (one-run limit / time), rotation mid-run on WHOIS, HTTP, mDNS, MAC. Cancel mid-run exercised on TLS only (positive result, see PHB-22).

## 3. Findings

Severity legend: high = blocks a clear reading or a primary action; medium = wrong or missing expected behaviour, or a clear duplication; low = polish.

### PHB-01 BUG – With the keyboard open, form cards are clipped under the focused field (high, C1, C2 seen on TLS; C1 on WHOIS)
Repro (TLS Inspector): tap "Host or IP address", type `bad host` (space). The card ends directly below the focused field (y~903 px) and the area below it is blank until the keyboard. Recent chips, Port, the probe toggle and the Inspect button are not drawn. Port variant: type `70000` in Port; the red outline shows but the message "Port must be a number from 1 to 65535" sits at y~1119 px inside the clipped blank region. The same clip happens on WHOIS with the keyboard open.
Expected: the form scrolls the focused field into view with the remaining fields and the supporting text visible above the IME.
Actual: content below the focused field disappears; supporting (error) text is hidden while typing.
Screenshots: `c1_tls_invalid.png`, `c1_tls_port_bad.png` (error hidden), `c1_tls_port_bad2.png` (error visible after keyboard closed), `c1_whois_invalid.png`.
Suggested fix: keep the form in a scrollable column with `imePadding()` and bring the focused field into view (BringIntoViewRequester); do not clip the card to the field. Cause in code UNVERIFIED.

### PHB-02 UX – WHOIS validates only on submit, with inconsistent wording and a pointless Retry (medium, C1)
Repro: WHOIS, type `bad domain!!`, press Look up. The field shows no error; Look up stays enabled. Result card: "Lookup failed / Hostname or IP cannot contain spaces / Retry".
Problems: (a) the field is labelled "Domain, IP, or ASN" but the message says "Hostname or IP"; (b) TLS and HTTP Probe show inline field errors before submit, WHOIS does not; (c) Retry on an input error cannot succeed without editing the input.
Screenshot: `c1_whois_invalid_run2.png`, `c1_whois_invalid_scrolled.png`.
Suggested fix: same inline validation as TLS/HTTP; disable Look up when invalid; name the field type in the message ("Enter a domain, IP or ASN without spaces"); no Retry for validation errors.

### PHB-03 UX – Retry button style differs between tools' error cards (low, C1/C3)
TLS timeout card: "Retry" as a text button with icon. WHOIS error card: filled pill "Retry". Pick one pattern (the CLAUDE.md error+retry requirement is met by both, the styling differs).
Screenshots: `c3_tls_rotate_after.png`, `c1_whois_invalid_scrolled.png`.

### PHB-04 DUP – Subnet result repeats the subnet six times and the host counts twice (medium, C1; same in C2)
Quoted strings, values from `192.168.1.0/24`:
- "192.168.1.0/24": preset chip (row 1 of presets), Network / Address field, result hero title, CIDR row under Notation Equivalents. Four occurrences plus the chip.
- "192.168.1.0": hero title (network part), "Network address" row.
- "254": hero subline "254 usable hosts", "Usable hosts 254" row. Same fact twice in one card.
- "24 network bits, 8 host bits": caption under three binary rows ("IP address, ...", "Subnet mask, ...", "Network address, ...") plus legend "Network: 24 bits", "Host: 8 bits".
Screenshots: `c1_subnet_done_top3.png`, `c1_subnet_done_top2.png`, `c2_subnet_done.png`.
Suggested fix: hero shows CIDR + one derived line ("254 usable hosts"); remove the "Usable hosts" row or the hero line; show the bit counts once in the legend and drop the three per-row captions; do not repeat the input CIDR in the Notation row.

### PHB-05 DUP – TLS result repeats the host and the expiry date (medium, C1)
Quoted strings after a run on example.com:
- "example.com": Host field, Recent chip "example.com", Connection row "example.com:443", Leaf header "example.com", Subject CN "example.com", SAN "DNS:example.com, DNS:*.example.com".
- "2026-12-25": Leaf header chip, Valid To row.
- "Cloudflare TLS Issuing ECC CA 3": Leaf Issuer CN and the Intermediate card header (truncated to "Cloudflare TLS...", so the header itself hides the name).
Also "Trusted" badge and "Hostname: Match" describe the same check.
Screenshots: `c1_tls_done_b.png`, `c1_tls_done_c.png`, `c1_tls_done_e.png`.
Suggested fix: show the leaf expiry once (chip or row); the Leaf header can show the CN only; drop the Connection Host/Port row or the Leaf header host.

### PHB-06 UX – Certificate expiry chips are all the same pink, no meaning (low, C1)
Leaf "2026-12-25", Intermediate "2035-05-27", "2037-10-17", Root "2028-12-31" all use the same pink chip. Today is 2026-11-01, so the leaf expires in under two months, but the colour does not say so and there is no legend.
Suggested fix: colour only by state (valid / expiring within 30 days / expired) with a short legend, or neutral chips.

### PHB-07 DUP – WHOIS result repeats the RDAP server and protocol (medium, C1)
Quoted: "rdap.arin.net" appears in the Query Chain node and in the caption "Answered by RDAP from rdap.arin.net"; "RDAP" appears as the node protocol label and in the caption. The "Network Range" row shows "15169-15169" (start equals end for a single ASN), and the hero is "GOOGLE" (organisation name only; the input AS15169 is not shown).
Raw RDAP/WHOIS card has a switch AND a chevron that both expand it (two affordances for one action).
Product gap: an ASN result shows only name and one range; a network engineer would expect country, RIR, org handle, abuse contact, and dates (the raw JSON contains them).
Screenshots: `c1_whois_done_c.png`, `c1_whois_raw.png`.

### PHB-08 DUP – HTTP Probe repeats URL, method and the method list (medium, C1)
- "https://example.com" in URL field, Recent chip, and result "https://example.com/" (trailing slash differs from the input).
- "GET" selected in the method chips and "Method GET" in Overview.
- The empty-state card "Supports HTTPS GET, POST, PUT, PATCH, DELETE, HEAD, and OPTIONS" repeats the chip row directly above it.
- The text "HTTP" is drawn three times: hero icon, URL field leading icon, empty-state icon.
Timing bar in Overview has no legend for the coloured segments (DNS / Connect / TLS / Server / Transfer).
Screenshots: `c1_http_idle.png`, `c1_http_invalid.png`, `c1_http_done_b.png`.

### PHB-09 DUP – MAC Lookup repeats the MAC and has two "Clear" actions (medium, C1)
- "00:03:93:12:34:56": input, Recent chip, result header, "Colon" notation row (the other notations repeat the same value).
- "Clear" appears twice: the field's clear icon (empties input) and the results header text "Clear" (empties results). Same label, different scope.
- Copy actions: "Copy all" button, a copy icon on each notation, and "Share" button.
Screenshot: `c1_oui_done_b.png`.

### PHB-10 UX – Keyboard field auto-capitalises MAC text (low, C1)
Typing `zz 00:1A` into MAC Lookup produced "Zz 00:1A". The field is hex input; capitalisation and autocorrect should be off (keyboard options). Also SwiftKey inserted a space after "." in the WHOIS domain field ("example. com", "abc. def"); not reproduced in TLS. Cause UNVERIFIED (IME vs app). Screenshots: `c1_whois_invalid.png` (SwiftKey space); MAC case shown in the typed-text dump only (no screenshot kept).

### PHB-11 UX – Recent history saves typos and partial inputs (low, C1)
WHOIS Recent shows "example.commm" and "example.coexample.comm". Both were valid-looking strings typed during this session. Recents store any value that passes the format check, with no dedupe against near-duplicates and no way to edit an entry.
Suggested fix: dedupe, cap length, offer "Edit" on a long press or leave as is with a clear-all.

### PHB-12 UX – Copy and Share pattern differs across tools (medium, all configs)
- TLS: copy icon + share icon + "Share PEM" text, all in one row.
- HTTP: copy icon + share icon + "Copy as cURL" text.
- WHOIS: copy icon + share icon.
- MAC Lookup: "Copy all" and "Share" outlined buttons.
- Speed Test: copy icon in the Results header AND "Share results" outlined button at the bottom (two places, two styles).
- mDNS: text "Copy" + text "Share results".
Same job, five treatments. The icon-only buttons have no visible label (a11y: icon-only actions, known pattern from PHA).
Suggested fix: one pattern per screen (label + icon), same position (under the result card, trailing).

### PHB-13 BUG – mDNS scanning spinner covers the hero title (medium, C1)
While scanning, a round spinner sits on top of the hero title, rendering "mDNS Browser" as "mDNS" + spinner + "rowser". Screenshot `c1_mdns_running.png`.
Suggested fix: place the progress indicator in the status row ("Scanning..." line) not over the title.

### PHB-14 DUP – mDNS status is said three times (low, C1)
Running: hero tile "62 found" and "2s elapsed", line "Scanning..." with spinner, "Scanning in progress" (content description), and the red Stop button. The done state keeps "62 found" in the hero and adds a notice card.

### PHB-15 UX – mDNS notice is jargon and the empty state points at the wrong button (low, C1)
Notice: "Some results were capped by a limit: addresses per service (max 16 addresses or 1024 bytes). Showing the services found so far." Plain words: "Some devices reported more addresses than we show (first 16 shown)".
Empty state: "Tap Scan to discover services", but the button reads "Scan Network". The scan button and the empty state are not inside a card (CLAUDE.md card rule; other tools use an input card).
Screenshots: `c1_mdns_idle.png`, `c1_mdns_done.png`.

### PHB-16 UX – Wake-on-LAN save flow is split across two cards and deletes without confirmation (medium, C1)
- The MAC is typed in the top card; the device name and "Save device" are in the bottom "Saved devices" card. "Save device" stays disabled until the top card holds a MAC. The split is not signposted.
- "Delete saved device" removed the entry at once; no confirmation or undo (verified with a test device, which was then removed).
- Empty state copy "No saved devices yet. Enter a MAC address, then save it here." is good.
Screenshots: `c1_wol_saved.png`, `c1_wol_saved2.png`, `c1_wol_delete_dialog.png` (no dialog appeared).
Suggested fix: one form with MAC, name, and Save together; confirm or snackbar-undo on delete.

### PHB-17 DUP – Wake-on-LAN helper text repeats field values (low, C1)
Broadcast field value "255.255.255.255" and helper "255.255.255.255 reaches the whole local network". UDP port value "9" and helper "Port 9 (discard) is the convention; some devices use 7".
Suggested fix: helper says what it means, not the value: "Reaches every device on the local network" and "Discard port; some devices use 7".

### PHB-18 BUG/UX – Cancel on TLS ends in a bare card (medium, C1)
Cancel during "Inspecting..." returned "Inspection canceled" as a single-line card with no body, no icon, and no next action (no Retry, no hint). Screenshot `c1_tls_after_cancel.png`.
Expected: neutral card with one line of context and Retry (matches the error card pattern).

### PHB-19 DUP – TLS error card repeats the status word (low, C3)
"Inspection failed" title and body "TLS inspection timed out". The title already says failed; the body already names the tool. Suggested: title "Timed out", body "The server did not answer in time. Check the port and host."

### PHB-20 UX – Text breaks mid-token in result tables (low, C1)
- TLS Cipher Suite: "TLS_ECDHE_ECDSA_WITH_AE" / "S_128_GCM_SHA256" (breaks inside the token).
- HTTP Security header names in monospace: "Strict-Transport-Secu" / "rity", "Content-Security-Pol" / "icy". Screenshot `c1_http_security.png`.
- TLS issuer "Cloudflare TLS Issuing ECC CA" / "3" orphan.
Suggested fix: allow break at hyphen/underscore or use a smaller monospace size; avoid single-digit orphans.

### PHB-21 UX – Hero subtitles are truncated with an ellipsis that hides meaning (low, C1 and C2)
- HTTP Probe C1: "...header and security analysis. ..." (hides "Plain HTTP is disabled in this release.").
- MAC Lookup C1: "...MAC address or OUI prefix, o..." (hides "offline").
- TLS C2: "SSL/TLS certificate chain i...". Subnet C2: "IPv4 CIDR, mask, wildcard & binary ..." (hides "breakdown").
- mDNS C2: hero text squeezed to four lines in a narrow column beside the icon.
Suggested fix: allow 2–3 lines in the hero, or move the detail into the info (i) dialog. Keep hero icon narrower at font 1.3.

### PHB-22 UX – Speed Test: chips look like buttons; the two stream chips wrap unevenly (low, C1)
"Download streams: 4" and "Upload streams: 2" are rounded pills with no control affordance; "Upload streams: 2" wraps to two lines while the other stays one. Whether they are tappable: UNVERIFIED (not tapped). Screenshot `c1_speed_idle.png`.
Also the gauge icon appears twice in the idle card (hero icon and the large green circle) and the idle copy "Measures latency, download, and upload speed in sequence" repeats the hero "Measure download, upload speed and latency against Cloudflare's global network".

### PHB-23 DUP – Cloudflare named four times on the Speed Test screen (low, C1)
Hero description "...against Cloudflare's global network", chip "Powered by Cloudflare Speed Test", disclaimer "Test traffic is sent to and measured by Cloudflare's speed.cloudflare.com service...", link "https://speed.cloudflare.com". The attribution is needed once; keep the disclaimer and drop the hero clause.
Screenshots: `c1_speed_top.png`, `c1_speed_done_bot.png`.

### PHB-24 UX – Speed Test results: "Avg" and "Average" both used (low, C1)
Idle latency and latency-under-load cards say "Avg"; the Download and Upload cards say "Average". Use one word. Also the throughput axis top label (47, 27) is the raw max, not a round tick.
Screenshot `c1_speed_done_mid.png`.

### PHB-25 UX – Landscape Home hero fills the screen (confirms PHA-21) (medium, C3)
In C3 the Home hero takes the full viewport; "All Tools" heading is at y 760 px and no tool tile is visible above the fold. Screenshot `c3_home.png`. Known: PHA-21 (not a new finding).

### PHB-26 Observation – System process fatal exception during the config change (not app, UNVERIFIED)
`logcat` at 00:11:50 shows "FATAL EXCEPTION IN SYSTEM PROCESS: main" from `RuntimeInit$KillApplicationHandler`. It occurred around the density/font change. The logcat buffer read at the end contained no AndroidRuntime fatal for `net.aieat.netswissknife.debug`. Cause UNVERIFIED; the app kept running.

### PHB-27 UX – Positive, but watch: TLS "Host / Port" and "Trusted" badge carry a status claim with no detail (low)
"Trusted" is a badge only; the reason (chain to a public root, validity, hostname match) is spread over other rows. Not a bug; a tap target that explains the badge would help a network engineer.

## 4. Things that worked well
- Inline validation with specific wording on TLS (host with spaces, port out of range) and HTTP Probe (plain http:// blocked, with an explanation). MAC Lookup gives a count ("2 entries are not MAC addresses or OUI prefixes").
- Send buttons and Look up are disabled until input is plausible on TLS, HTTP, Subnet, WoL and MAC; enabled state is clear.
- TLS full chain with leaf, intermediates and root, expand/collapse per certificate, SHA-256 fingerprint in monospace, SAN in monospace. Good data density.
- Cancel works on TLS ("Inspection canceled"); rotation mid-run keeps TLS running to a clean error card with Retry, and keeps Speed Test running through a rotation to portrait.
- Subnet math checked by hand for 192.168.1.0/24: network .0, broadcast .255, first .1, last .254, 254 usable, 256 total, wildcard 0.0.0.255, mask 255.255.255.0, hex 0xFFFFFF00, binary breakdown colour-coded with a legend. All correct.
- WHOIS returned a valid RDAP answer for AS15169 (GOOGLE) with a query chain and raw toggle.
- HTTP Probe: 200 with timing breakdown, Overview/Headers/Body/Security tabs, security header pass/warn/fail chips with per-row badges.
- MAC Lookup: 00:03:93 resolved to Apple, Inc. (correct), block size, unicast, locally administered flag, notations with copy per notation.
- WoL: empty state text is clear; the saved-device row (name, MAC, Use, edit, delete) is compact. No packet was sent (see section 5).
- Dark mode (C4): TLS, MAC Lookup results and WoL idle read well; no hard-coded light colours seen.
- Consistent input cards, segmented controls (Subnet CIDR / IP Range), chip patterns for presets and recents across tools.

## 5. Safety and state notes
- No Wake-on-LAN packet was sent: Send magic packet was enabled once (valid MAC AA:BB:CC:DD:EE:FF) and never tapped. The test saved device "QA test" was deleted afterwards; the MAC field was cleared.
- One Speed Test run to Cloudflare was performed (download about 43 Mbps, 52 MB; upload about 18 Mbps, 21 MB). No second run.
- TLS and WHOIS recent lists now contain test entries (1.1.1.1, wrong.host.badssl.com, example.commm, example.coexample.comm).
- Network use: example.com, 1.1.1.1, wrong.host.badssl.com (TLS), rdap.arin.net via WHOIS, example.com and httpbin via HTTP Probe, Cloudflare speed test, local mDNS multicast. No scanning beyond these.

## 6. Coverage gaps (honest list)
- C3 landscape: only TLS mid-run rotation, Speed Test landscape run, and Home. WHOIS, HTTP, Subnet, mDNS, WoL, MAC not inspected in landscape. TLS static landscape not done (the More sheet landscape scroll was not reliable for navigation).
- C4 dark: TLS idle, MAC Lookup result, WoL idle captured (WoL idle not reviewed in detail). WHOIS, HTTP, Subnet, mDNS, Speed Test not covered.
- C2 (360 dp + font 1.3): idle or first result only for TLS, WHOIS, HTTP, Subnet, mDNS, Speed Test idle, WoL, MAC idle captured (only TLS, HTTP, Subnet, mDNS reviewed in detail).
- Not tested: Cancel on Speed Test and mDNS; rotation mid-run on WHOIS, HTTP, mDNS, MAC; TLS "Probe older TLS versions" toggle (the tap landed on the back path); HTTP custom headers and body; mDNS Search field; Speed Test "Download streams" chip behaviour.

## 7. Restore
Done at the end, in order: `adb shell wm density reset`, `wm size reset`, `settings put system accelerometer_rotation 1`, `user_rotation 0`, `font_scale 1.0`, `cmd uimode night no`, `pm revoke net.aieat.netswissknife.debug android.permission.ACCESS_FINE_LOCATION`. Post-restore: config sw600dp, density 320, font 1.0, night no.
