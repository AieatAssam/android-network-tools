# VERIFY-6B - phone-mode QA of V6B tools (TLS Inspector, WHOIS, HTTP Probe, Subnet, mDNS, Speed Test, Wake-on-LAN, MAC Lookup)

Build: HEAD 2bdb6c2 (debug, `net.aieat.netswissknife.debug`), tablet U8BBB20806201256, Android 8.0, 1200x1920 px.
Method: density change only (no `wm size`). Config per the phone brief. Screenshots and label dumps in
`/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/v6b/` (file names c1_, c2_, c3_, c4_ prefixed).
Targets used: example.com, 1.1.1.1, 10.109.208.0/24 (local subnet), google.com not needed, AS15169 (WHOIS). One Speed Test run (Cloudflare). Wake-on-LAN: form, validation and saved-device UI only; "Send magic packet" was never tapped.
Clipboard reads: `service call clipboard` decoded with the script from the earlier round.

## 1. Configurations and get-config (first line, abbreviated)

| Id | Setup | get-config |
|---|---|---|
| C1 | density 480, portrait, font 1.0, light | `sw400dp-w400dp-h593dp-normal-notlong-...-port-notnight-xxhdpi` |
| C2 | density 533 + font_scale 1.3, portrait, light | `sw360dp-w360dp-h531dp-normal-notlong-...-533dpi-port-notnight` (`settings get system font_scale` = 1.3, `wm density` = Override 533) |
| C3 | density 480, landscape (user_rotation 1), light | `sw400dp-w640dp-h353dp-normal-notlong-...-land-notnight-xxhdpi` |
| C4 | density 480, portrait, dark (`cmd uimode night yes`) | `sw400dp-w400dp-h593dp-normal-notlong-...-port-night-xxhdpi` |

Restore done at the end: `wm density reset`, `wm size reset`, accelerometer_rotation 1, user_rotation 0, font_scale 1.0, `cmd uimode night no`, `pm revoke ... ACCESS_FINE_LOCATION`. Post-restore get-config is the tablet config (sw600dp, density 320, night no).

## 2. Requested extra checks

| Check | Result | Evidence |
|---|---|---|
| (a) Landscape 400dp More sheet, scroll fully | PASS. All 16 tools reachable: Diagnostics (Ping, Traceroute, Port Scanner, DNS, Network Info), Wi-Fi & LAN (LAN Scanner, Wi-Fi Scanner, Network Topology, mDNS, MAC Lookup), Security (TLS, WHOIS, HTTP Probe), Utilities (Subnet, Speed Test, Wake-on-LAN). The list area is only about 400px tall (see V6B-12). | `c3_more_end.png` (scroll dump) |
| (b) More sheet at 360dp + font 1.3 portrait, scroll to bottom | PASS. Same 16 tools, Wake-on-LAN and Speed Test reachable at the bottom; the PHA-10 "could not scroll" gap from WP35b is no longer reproduced. | `c2_more_end.png` |
| (c) LAN Scanner, one scan (56 devices, 254 addresses, 30.6 s) | Only one host shows "Latency 0 ms": 10.109.208.146, which is this tablet (`ip addr` wlan0). `adb shell ping -c2 10.109.208.146` gives rtt avg 0.222 ms, so 0 ms is sub-millisecond rounding on the tablet's own address, not a remote host. No other host shows 0 ms. The tablet row carries no "this device" label (V6B-09). For reference, 10.109.208.217 shows "Latency 5 ms" in the app and 11.05 ms in `ping -c2` (different probe method; not called a bug). | `c1_lan_run.png`, `v6b/lan_labels.txt` |
| (d) Recent chip duplicates the typed host on DNS / Ping / Ports | NOT REPRODUCED. Typing `google.com` into DNS did not add a chip; the Recent row shows earlier hosts only. A pre-existing corrupted recent entry is visible on DNS ("example.commple.comnvalid", see PHB-11). Ping and Ports recents show 1.1.1.1 / 10.109.208.1 only. | `c1_dns_typed.png` |

## 3. Previously reported findings (PHB-nn) - status for V6B tools

| PHB | Status | Evidence / note |
|---|---|---|
| PHB-01 keyboard clips form under focused field | FIXED for all V6B input tools, portrait and landscape. Field, error text and Recent row stay above the IME. Landscape: Inspect/Look up/Calculate sit below the keyboard until it closes (V6B-13 note). | `c1_tls_ime_invalid.png`, `c1_whois_ime_invalid.png`, `c1_http_ime_invalid.png`, `c1_subnet_err_ime.png`, `c1_mac_ime_invalid.png`, `c1_wol_ime_invalid.png`, `c3_tls_ime.png`, `c3_whois_ime.png`, `c3_http_ime.png`, `c3_subnet_ime.png`, `c3_mac_ime.png`, `c3_wol_ime.png` |
| PHB-02 WHOIS validates only on submit | FIXED. Inline error on typing, "Enter a domain or IP address (or an ASN such as AS15169), with no spaces or symbols." (matches the field label). | `c1_whois_ime_invalid.png` |
| PHB-03 Retry style differs between error cards | NOT RE-TESTED (time). Subnet and TLS (C2) error cards both show a Retry; styles not compared side by side in this pass. |  |
| PHB-04 Subnet repeats subnet six times, host count twice | PARTIAL. Fixed: "Usable hosts" row removed (254 shown once in hero line), CIDR row under Notation removed, the input is not echoed. STILL PRESENT: "24 network bits, 8 host bits" is repeated as a caption on three binary rows and again in the legend ("Network: 24 bits", "Host: 8 bits"). | `c1_subnet_done_c.png`, `v6b/` labels |
| PHB-05 TLS repeats host and expiry date | PARTIAL. Fixed: expiry date chip removed (Leaf header now "53 days left"; date once in Valid To), Connection row no longer repeats the host. STILL PRESENT (rejected by WP33 as chain link): "Cloudflare TLS Issuing ECC CA 3" is in the Leaf Issuer CN row and the Intermediate header. Host "example.com" is in the Recent chip, the Leaf header and SAN. | `c1_tls_done_top.png`, `c1_tls_done_mid.png` |
| PHB-06 expiry chips all one pink | PARTIAL / NOT RE-TESTED. Chips replaced by "days left" text in Leaf and Intermediate headers; colour per state not re-checked. |  |
| PHB-07 WHOIS repeats RDAP server; two affordances on raw toggle | FIXED for the server name (rdap.arin.net once) and the raw toggle (single card-style button with chevron); range shows "15169" once. Residual: "RDAP" repeated in the node label and the caption "Answered by RDAP" (V6B-04). Country/RIR/abuse fields not re-checked (NT, product gap). | `c1_whois_done.png`, `c1_whois_raw.png` |
| PHB-08 HTTP repeats URL, method, method list | PARTIAL. Timing legend now has text labels (DNS, Connect, TLS, Server and headers, Transfer), fixed. STILL PRESENT: "https://example.com" as Recent chip and "https://example.com/" as result title; "HTTP" still drawn in hero icon and field icon. Empty-state list not re-checked. | `c1_http_run.png`, `c1_http_done_bottom.png` |
| PHB-09 MAC repeats value, two "Clear" | PARTIAL. Results header "Clear" is now "Clear results" (second Clear distinguishable). STILL PRESENT: MAC shown in input, Recent chip and Colon notation; Copy actions now "Copy" and "Share" (per-notation "Copy Colon" etc. remain). | `c1_mac_done.png` |
| PHB-10 MAC field auto-capitalises | STILL PRESENT. "zz 00:1A" shows as "Zz 00:1A" (portrait and landscape). Keyboard capitalisation not disabled. | `c1_mac_ime_invalid.png`, `c3_mac_ime.png` |
| PHB-11 Recents store typos | STILL PRESENT. WHOIS Recent: "example.commm", "example.coexample.comm"; DNS Recent: "example.commple.comnvalid". | `c3_whois_ime.png`, `c1_dns_typed.png` |
| PHB-12 Copy/Share pattern differs per tool | FIXED for V6B tools. Labelled "Copy" and "Share" text buttons trailing in one row on TLS (with "Share PEM"), WHOIS, HTTP ("Copy as cURL" + Copy + Share), Subnet, mDNS, Speed Test ("Run again" below), MAC. Subnet row sits below the Split card, not under the result (V6B-17). | screenshots as above |
| PHB-13 mDNS spinner covers title | FIXED. Spinner sits in the list area; title reads "mDNS Browser". | `c1_mdns_running.png` |
| PHB-14 mDNS status said three times | FIXED. Running: "28 found", "3s elapsed", Stop; no status line duplicated in hero. | `c1_mdns_running.png` |
| PHB-15 mDNS notice jargon; empty state wrong button | PARTIAL. Notice now plain ("Some devices reported more than we show (first 16 addresses per service)"). Stale tail "Showing the services found so far." remains after a finished scan (V6B-06). Empty state "Tap Scan Network to find services" still repeats the button label (PHB-15 family). | `c1_mdns_done.png`, `c2_mdns_idle.png` |
| PHB-16 WoL save flow split across two cards; delete without confirm | PARTIAL. Delete now asks "Delete saved device?" with Cancel / Delete (FIXED). STILL PRESENT: MAC in the top card, device name and "Save device" in the lower "Saved devices" card. | `c1_wol_saved.png`, `c1_wol_delete.png`, `c4_wol_idle.png` |
| PHB-17 WoL helper repeats field values | NOT RE-TESTED (Advanced options not expanded in this pass). |  |
| PHB-18 TLS Cancel ends in bare card | NOT RE-TESTED (cancel not exercised in this pass). |  |
| PHB-19 TLS error repeats status word | NOT RE-TESTED for the timeout copy. A DNS-failure error card now reads "Inspection failed" / raw resolver text (V6B-01). |  |
| PHB-20 text breaks mid-token | PARTIAL. TLS cipher suite now breaks after the underscores (zero-width break points) - FIXED for TLS. HTTP security header names not re-checked (NT). | `c1_tls_done_mid.png` |
| PHB-21 hero subtitle ellipsis hides meaning | PARTIAL. MAC Lookup hero now shows all three lines - FIXED. Subnet C2 full subtitle shows - FIXED. STILL PRESENT: HTTP Probe hero "Plain HTTP is disabled in this ..." (ellipsis) at C1 with the keyboard open; mDNS hero squeezed to four narrow lines at C2 (360dp + 1.3). | `c1_http_ime_invalid.png`, `c2_mdns_idle.png` |
| PHB-22 Speed Test chips and duplicate gauge icon | NOT RE-TESTED (idle captured, not inspected in detail; one run limit). |  |
| PHB-23 Cloudflare named four times | PARTIAL. The hero no longer names Cloudflare (labels show "Measure download, upload speed and latency"); the disclaimer and chip were not re-checked. |  |
| PHB-24 "Avg" vs "Average" | FIXED for the wording (every card now says "Avg"). Throughput axis ticks still non-round: 0, 10, 19, 29 (V6B-08). | `c1_speed_done.png` |
| PHB-27 TLS "Trusted" badge has no detail | NOT RE-TESTED. Badge still present with the line "Chain, hostname and expiry checks passed." |  |

## 4. New issues (V6B-nn)

### V6B-01 BUG - TLS error card shows raw resolver text (MEDIUM; C2 portrait 360dp + font 1.3)
Repro: TLS, host field holds an unresolvable name (in this run the host read `exaexample.commmple.com` after my repeated typing), tap Inspect.
Expected: plain sentence, e.g. "Could not find this host. Check the name."
Actual: card title "Inspection failed", body `Unable to resolve host "exaexample.commmple.com": No address associated with hostname`.
Same family as V6A-04 (Traceroute). The mapping in `resolverErrorMessage` (core-network) is not used by TLS.
Screenshot: `c2_tls_err.png`. Fix: route TLS through the shared resolver-message mapping and keep the host in the body only once.

### V6B-02 UX - Subnet validates only on submit, and its Retry cannot succeed (LOW; C1)
Repro: Subnet, type `10.0.0.0/33`, keyboard closes, the field shows no error and Calculate stays enabled. Tap Calculate: card "Invalid Input / /33 is not a valid prefix. Use a number from 0 to 32, such as /24." with Retry.
Expected: inline error on the field like TLS, HTTP and WoL; Calculate disabled; no Retry for an input error.
Screenshots: `c1_subnet_err_ime.png`, `c1_subnet_err.png`, `c1_subnet_err_b.png`. Fix: same inline validation as TLS/HTTP; drop Retry from validation errors.

### V6B-03 DUP - HTTP Probe repeats one sentence on screen (LOW; C1 with keyboard)
Quoted: hero "Plain HTTP is disabled in this release." and error "Plain HTTP requests are disabled in this release. Use an HTTPS URL."
Screenshot: `c1_http_ime_invalid.png`. Fix: hero describes the tool only; error keeps "Use an HTTPS URL."

### V6B-04 DUP - WHOIS repeats "RDAP" (LOW; C1)
Quoted: Query Chain node protocol "RDAP" and caption "Answered by RDAP". Screenshot: `c1_whois_done.png`. Fix: drop the caption or the node label.

### V6B-05 DUP - MAC copy text repeats the same value (LOW; C1 clipboard)
Clipboard text: "Input: 00:03:93:12:34:56" then "Address: 00:03:93:12:34:56". Fix: omit "Input" when it equals the normalised address.

### V6B-06 BUG/UX - mDNS clipboard and notice wording (LOW; C1)
Clipboard contains the raw token "Truncated: ADDRESS_LIMIT". On screen, after a finished scan, the notice still says "Showing the services found so far." Screenshots: `c1_mdns_done.png`, `c1_mdns_copyrow.png`. Fix: human text ("Some addresses were hidden to keep the list short."); say "Showing all services found." when the scan is complete.

### V6B-07 UX - mDNS results have almost no room at 400dp portrait (MEDIUM; C1)
Repro: mDNS, Scan, wait for done. Hero (40 found), a three-line notice, Scan Network and Clear, and the search field fill about two-thirds of the screen. The results list starts near the bottom and scrolls in a small inner window (about 170dp).
Expected: results are the primary content and sit above the fold.
Screenshots: `c1_mdns_done.png`, `c1_mdns_bottom.png`. Fix: collapse the hero to one line when results exist, move the notice into the results header, or let the whole page scroll instead of the inner list.

### V6B-08 UX - Speed Test throughput axis uses non-round ticks (LOW; C1)
Ticks read 0, 10, 19, 29 (uneven). Screenshot `c1_speed_done.png`. Fix: round to nice values (0, 10, 20, 30).

### V6B-09 UX - Own device shows "0 ms" with no label (LOW; LAN Scanner, C1)
10.109.208.146 (this tablet) shows "Latency 0 ms" and the hostname HUAWEI_MediaPad_T5 with no "This device" marker. `ping -c2` gives 0.222 ms, so sub-millisecond values round to 0. Fix: show "<1 ms" and label the device as this device. Screenshot `c1_lan_run.png`.

### V6B-10 UX - Inspect button sits under the bottom bar at 360dp + font 1.3 until scrolled (LOW; C2)
In `c2_tls_top.png` the Inspect button is cut by the bottom navigation bar until the form is scrolled to its end. Fix: add bottom padding so the primary action can be scrolled fully clear.

### V6B-11 UX - MAC Recent row hidden while the field shows an error (LOW; C1 and C3)
With `zz 00:1A` typed, the field shows the error and Look up; the Recent row is not drawn, although it is visible for valid input. Breaks the "Recent row visible above keyboard" pattern used on other tools. Screenshots: `c1_mac_ime_invalid.png`, `c3_mac_ime.png`. Fix: keep Recent visible with an error.

### V6B-12 UX - Landscape More sheet has a very small scroll region (LOW; C3)
At 400dp landscape, the list area is about 400px tall (about 2 rows visible) and the Settings and Debug Logs footer takes about 260px. All tools are reachable (check a) but browsing needs many swipes. Screenshot `c3_more_end.png`, `c3_state.png`. Fix: lay out the footer as a row, or make the sheet take the full height in landscape.

### V6B-13 UX - Landscape: primary action hidden under the keyboard (LOW; C3)
TLS, WHOIS, HTTP, Subnet and MAC in landscape: field, error and Recent row are above the keyboard, but Inspect / Look up / Calculate sit below it until the keyboard closes. Screenshots `c3_tls_ime.png`, `c3_mac_ime.png`. Fix: same bring-into-view for the primary button when the field is short of room (as V6A-02 in portrait).

### V6B-14 UX - Subnet Copy/Share row sits under the wrong card (LOW; C1)
The Copy and Share row appears after the "Split into equal subnets" card, not directly under the result. Screenshot `c1_subnet_done_c.png`. Fix: place the row under the result hero, as the other tools do.

## 5. Per-tool coverage table (tool x config)

Key: P = checked and passed or issue noted (issue ID shown); V = visual pass, captured and reviewed; NT = not tested, reason given. "captured" means the screenshot was taken but not reviewed element by element in this pass.

| Tool | C1 portrait 400 | C2 360 + font 1.3 | C3 landscape 400 | C4 dark 400 |
|---|---|---|---|---|
| TLS Inspector | P: idle, IME error (PHB-01 FIXED), run example.com, result (PHB-05 PARTIAL), Copy, Back-nav restore PASS | V (idle reviewed) + error state P: V6B-01 (raw resolver text), V6B-10 (Inspect under bar) | V: idle reviewed, IME field+error+Recent visible (PHB-01 FIXED), V6B-13 (Inspect under keyboard) | V: idle reviewed (`c4_tls_idle.png`). Result NT (time) |
| WHOIS Lookup | P: IME inline error (PHB-02 FIXED), AS15169 result, raw toggle, Copy, Back-nav PASS. V6B-04 | NT: idle captured (`c2_whois_idle.png`), not reviewed in detail. Result NT (time) | P: IME error + Recent visible (landscape). Idle captured (`c3_whois_idle.png`) | NT: idle captured (`c4_whois_idle.png`), not reviewed. Result NT (time) |
| HTTP Probe | P: IME error, run https://example.com (200), Copy as cURL, Back-nav PASS. PHB-08 PARTIAL, PHB-21 STILL PRESENT, V6B-03 | NT: idle captured (`c2_http_idle.png`), not reviewed. Result NT (time) | P: IME error + Recent visible. Idle captured (`c3_http_idle.png`) | NT: idle captured (`c4_http_idle.png`), not reviewed. Result NT (time) |
| Subnet Calculator | P: 10.109.208.0/24 result (PHB-04 PARTIAL), Copy row, Back-nav PASS, invalid /33 error V6B-02, V6B-14 | V: idle reviewed (`c2_subnet_idle.png`): disabled Calculate, presets wrap; PHB-21 FIXED. Result NT (time) | P: IME field + Calculate above keyboard (V6B-13 applies). Idle captured (`c3_subnet_idle.png`) | NT: idle captured (`c4_subnet_idle.png`), not reviewed. Result NT (time) |
| mDNS Browser | P: Scan, running (PHB-13 FIXED), done (V6B-07), Copy (V6B-06), Back-nav PASS | V: idle reviewed (`c2_mdns_idle.png`): PHB-21 STILL PRESENT (hero squeezed). Scan result NT (time, C2 not re-run) | P/NT: no text field in idle; Scan button only. Idle captured (`c3_mdns_idle.png`), not reviewed | NT: idle captured (`c4_mdns_idle.png`), not reviewed. Scan NT (time) |
| Speed Test | P: one run (download 13.8 Mbps avg, upload 16.1 Mbps, latency under load), Copy, Share row, Back-nav PASS (results retained). PHB-24 FIXED, V6B-08 | NT: idle captured (`c2_speed_idle.png`). Run NT (one-run limit) | NT: idle captured (`c3_speed_idle.png`). Run NT (one-run limit) | NT: idle captured (`c4_speed_idle.png`). Run NT (one-run limit) |
| Wake-on-LAN | P: invalid MAC inline error; valid MAC typed, "Send magic packet" shown enabled and NOT tapped; save device; delete confirm (PHB-16 partial); Back-nav NT (MAC cleared before leaving) | NT: idle captured (`c2_wol_idle.png`), not reviewed | P: IME error + field above keyboard. Idle captured (`c3_wol_idle.png`) | V: idle reviewed (`c4_wol_idle.png`): PHB-16 split still visible; Send disabled with empty MAC |
| MAC Lookup | P: invalid input count error, valid result (Apple, Inc.), Clear results, Copy, Back-nav PASS. PHB-09 PARTIAL, PHB-10 STILL PRESENT, PHB-21 FIXED, V6B-05, V6B-11 | NT: idle captured (`c2_mac_idle.png`), not reviewed | P: IME error + Look up above keyboard (V6B-11, V6B-13). Idle captured (`c3_mac_idle.png`) | NT: idle captured (`c4_mac_idle.png`), not reviewed. Result NT (time) |

Landscape More sheet (check a) P and 360dp More sheet (check b) P are listed in section 2.

Column notes:
- IME portrait: TLS, WHOIS, HTTP, Subnet, MAC, WoL all done in C1. mDNS and Speed Test have no main text field in idle, so IME NT (no field).
- IME landscape: TLS, WHOIS, HTTP, Subnet, MAC, WoL done in C3. mDNS and Speed Test NT (no field).
- Back-nav restore: TLS, WHOIS, HTTP, Subnet, mDNS, Speed Test, MAC PASS (result kept after leaving and reopening). WoL NT (cleared MAC before leaving).
- Copy/Share once: TLS, WHOIS, HTTP, Subnet, mDNS, Speed, MAC done (clipboard text read). WoL NT (no result surface).
- Error states: TLS (C2, V6B-01), Subnet (C1, V6B-02), HTTP (C1, inline), WHOIS (C1, inline), MAC (C1, count), WoL (C1, inline). mDNS and Speed Test error states NT (not producible without blocking traffic).

## 6. Things that worked well
- Inline validation is consistent on WHOIS, HTTP, MAC and WoL, and the keyboard no longer hides the error or Recent row in portrait (PHB-01 fix verified on six tools).
- WHOIS raw toggle is one control now; query chain shows the server once.
- MAC Lookup hero shows its full text; "Copy" and "Share" use the same labelled style as other tools.
- mDNS scan shows the spinner away from the title and reports counts once in the hero.
- Speed Test results use "Avg" consistently and the Copy/Share/Run again row is clear.
- More sheet lists all 16 tools in landscape and at 360dp with font 1.3 (check a and b).
- Dark mode (C4) on TLS and WoL keeps the hero, fields and disabled Send button readable; no hard-coded light colours seen.

## 7. Safety and state notes
- Wake-on-LAN: no packet sent. Valid MAC `AA:BB:CC:DD:EE:FF` typed to check the Send state only. Test saved device "QA test" was created and deleted (confirmation dialog used). MAC field cleared.
- Speed Test: one run to Cloudflare in C1 (no second run).
- Network use: example.com, 1.1.1.1 (recent lists), httpbin.org not used this pass, the local subnet 10.109.208.0/24 (LAN Scanner, one scan; mDNS multicast), Cloudflare speed test, rdap.arin.net (WHOIS AS15169), example.com (HTTP Probe). An attempt to probe `127.0.0.1:1` appears in HTTP Recent from an earlier session, not from this pass.
- The TLS host field was garbled once by my repeated typing (`exaexample.commmple.com`); the error text in V6B-01 comes from that input.

## 8. Not covered (honest list)
- Results in C2, C3 and C4 for WHOIS, HTTP, Subnet, mDNS and MAC: NT (time). Each was seen only in idle or in the C1 run.
- PHB-17, PHB-18, PHB-19 (timeout copy), PHB-22 detail, PHB-27: NOT RE-TESTED.
- Speed Test second run and error state: NT (one-run limit).
- Captures listed as "captured" were not reviewed element by element.
