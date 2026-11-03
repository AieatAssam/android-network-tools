# VERIFY-7B - final visual pass, phone emulation (C2, C3, C4 cells for V7B tools)

Build: HEAD f1a29e3 (debug build `app/build/outputs/apk/debug/app-debug.apk`, installed with `adb install -r`), package `net.aieat.netswissknife.debug`, tablet U8BBB20806201256 (1200x1920 px, Android 8.0).
Method: density change only (no `wm size`), relaunch after each config change, `am get-config` checked each time.
Screenshots: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/v7b/` (prefix `c1_`, `c2_`, `c3_`, `c4_`). Paths are given per finding below.
Targets: example.com (TLS, HTTP, DNS-based), AS15169 (WHOIS), 10.109.208.0/24 (Subnet, local), mDNS on the local LAN, Cloudflare speed test (one run, C1 only), MAC 00:03:93:12:34:56. Wake-on-LAN: form, validation, saved-device list and delete-confirm only. "Send magic packet" was never tapped.

## 1. Configurations and get-config

| Id | Setup | get-config (abbreviated) |
|---|---|---|
| C1 | density 480, portrait, font 1.0, light | `sw400dp-w400dp-h593dp-normal-notlong-...-port-notnight-xxhdpi` |
| C2 | density 533 + font_scale 1.3, portrait, light | `sw360dp-w360dp-h531dp-normal-notlong-...-533dpi-port-notnight` |
| C3 | density 480, landscape (accelerometer 0, user_rotation 1), font 1.0, light | `sw400dp-w640dp-h353dp-normal-notlong-...-land-notnight-xxhdpi` |
| C4 | density 480, portrait, dark (`cmd uimode night yes`) | `sw400dp-w400dp-h593dp-normal-notlong-...-port-night-xxhdpi` |

Landscape note: the C3 screenshots are 1920x1200 px (rotated). The More sheet in landscape is about 400 px tall; it was scrolled with swipes at y 650 to 330 (V6B-12 still applies).

## 2. Per-tool x config checklist

Key: P = seen and reviewed element by element, passed (issues noted by ID). NT = not tested, with reason. "IME" = keyboard open on the main field, field + error + Recent + primary action checked.

| Tool | C1 portrait 400 | C2 360 + font 1.3 | C3 landscape 400 | C4 dark 400 |
|---|---|---|---|---|
| TLS Inspector | P (result run for example.com). Leaf/Connection/chain reviewed. Chain titles ellipsized ("Cloudflare T...", "SSL.com TL...") - V7B-01 (LOW here). Issuer "CA / 3" orphan - V7B-10. `c1_tls_idle.png`, `c1_tls_typed_ime.png`, `c1_tls_result_a..e.png` | IME P: field + inline error + Recent visible (`c2_tls_ime_invalid.png`). Result: Trusted, leaf "example....", chain names as "..." - **V7B-01 MEDIUM** (`c2_tls_result_d.png`). Pin placeholder 2 lines - V7B-12. Inspect reachable by scroll (V6B-10 fixed). | IME P: field + error + Recent visible, Inspect below IME (V6B-13) (`c3_tls_ime.png`). Result P: Cipher/Issuer on one line, Root name full (`c3_tls_result_b.png`, `c3_tls_result_c.png`). | P: idle + result, contrast fine for Trusted/titles. "52 days left" green on dark-green chip low contrast - V7B-07 (LOW, UNVERIFIED ratio). `c4_tls_idle.png`, `c4_tls_b.png` |
| WHOIS Lookup | P: AS15169 via Recent chip, Query Chain, GOOGLE header, "Network Range 15169" (ASN shown as range) - V7B-15 (LOW). Raw toggle + Copy/Share below card. `c1_whois_a.png`, `c1_whois_b.png` | P: IME field + Recent visible ("example.commm" typo chip still present, PHB-11). Result: Query chain single node card is large (dead space), "Network Range" label wraps. `c2_whois_ime.png`, `c2_whois_a.png`, `c2_whois_b.png` | P: IME field + Recent visible; Look up below IME (V6B-13). Result fits. `c3_whois_ime.png`, `c3_whois_b.png` | P: result, gradient hero fine, Copy/Share contrast ok. `c4_whois_b.png` |
| HTTP Probe | P: https://example.com 200 / 374 ms, tabs, timing bar. Timing values column at x~532 vs key/value rows at x~500 - V7B-14 (LOW). `c1_http_a.png`, `c1_http_b.png` | P (IME): field with Recent visible. Result: "Security" tab clipped to "Sec" at right edge - **V7B-02 MEDIUM** (`c2_http_a.png`). Form scroll-to-bottom reachable (sheet scroll OK). | P: IME field + invalid-URL error ("The URL could not be parsed.") + Recent row visible (`c3_http_now.png`). Result: all four tabs fit; timing list (`c3_http_b.png`). | P: idle + result. "200" dark green on dark card, low contrast - V7B-07 (LOW, UNVERIFIED ratio) (`c4_http_b.png`). |
| Subnet Calculator | P: 10.109.208.0/24 result (254 usable hosts, Network/Broadcast/First/Last/Total), binary breakdown, Notation, Address properties. `c1_subnet_a.png`, `c1_subnet_b.png`, `c1_subnet_c.png` | P (IME): field + Calculate above IME (`c2_subnet_ime.png`). Result: info icon of "Network address" squeezed to a dot - V7B-03 (LOW) (`c2_subnet_a.png`). | P (IME): field + Calculate above IME. Result: rows fit, info icons aligned. Wide gap between label and value (LOW, not logged separately). `c3_subnet_ime.png`, `c3_subnet_a.png` | P: 10.0.0.0/8 result, hero gradient ends in maroon, readable. `c4_subnet_a.png` |
| mDNS Browser | P: scan (46 found), notice text plain, service group + "Ping hostname" action. Results start at about y 1120, below the fold (V6B-07 still present). `c1_mdns_a.png` | P (idle with 1.3 font): hero squeezed. Scan: 4-line notice pushes results below the fold; page-level swipe did not scroll; results only inside a ~260 dp inner window showing "Ping 10.109.208.132" / IPv6 "Ping 2a10:..." with the service names above the window - **V7B-04 MEDIUM** (`c2_mdns_a.png`, `c2_mdns_c.png`). | P/**V7B-08 MEDIUM**: after scan, the results region is about 70 px tall under Scan/Clear and the search field (`c3_mdns_a.png`). Frame b (after swipe) not reviewed. | P: scan 49 found, results visible, "Ping hostname" clipped by inner window (V6B-07 family). `c4_mdns_a.png` |
| Speed Test | P: ONE run (C1). Idle latency Min 51 / Avg 75.4 / Max 184 / Jitter 44.8 ms, Connect 19 ms; Download 27.5 Mbps avg, 41.1 peak, 33.2 MB; Upload 16.1 Mbps avg, 40.3 peak, 19.2 MB; throughput charts with round ticks 0-60 (V6B-08 fixed); latency under load. `c1_speed_b.png`, `c1_speed_c.png` | P idle. Run NT: one-run limit (C1 only). Idle card fills the screen; Start Test is below the fold (scroll needed) - V7B-21 (LOW). `c2_speed_idle.png` | P idle. Run NT: one-run limit. Start Test below the fold (scroll). `c3_speed_idle.png` | P idle: Start Test, stream chips visible, dark contrast fine. Run NT: one-run limit. `c4_speed_idle.png` |
| Wake-on-LAN (UI only) | P: invalid MAC inline error (VERIFY-6B); valid MAC typed, Send enabled, NOT tapped; saved list empty (`c1_wol_valid.png`). Two filled primary buttons on the card (Send + Save device) - V7B-17 (LOW, C2 seen). | P (UI): valid MAC + keyboard; saved device row: MAC wraps "AA:BB:CC:DD:EE" / ":FF" - **V7B-05 MEDIUM** (`c2_wol_saved.png`). Delete confirm dialog: "Delete saved device?" with Cancel / Delete; copy "...will be removed from Saved devices. You can save it again later." (`c2_wol_delete.png`). Deleted test device afterwards (list empty). | P (UI): IME with MAC field visible, Send magic packet hidden under keyboard (V6B-13) (`c3_wol_ime.png`); empty saved state (`c3_wol_b.png`). | P: valid MAC, Send enabled (mint), NOT tapped (`c4_wol_a.png`). |
| MAC Lookup | P: valid result Apple, Inc., block size, type, notations (copy icon-only per notation), Lowercase toggle, "1 entry / Clear results". PHB-10 (auto-capitalise) still present: field shows "Zz 00:..." style. `c1_mac_a.png`, `c1_mac_b.png` | P (IME): invalid input error "2 entries are not MAC addresses or OUI prefixes". **V7B-06 MEDIUM**: "Paste from clipboard" wraps to four lines ("clipboa / rd") and Look up to two lines; Recent row hidden with error (V6B-11). Result P (`c2_mac_b.png`). `c2_mac_ime_invalid.png` | P (IME): error "1 entry is not a MAC address or OUI prefix" + Look up + Paste above IME; Recent hidden (V6B-11). Result P (`c3_mac_a.png`). `c3_mac_ime.png` | P: result, notation rows, contrast good. `c4_mac_a.png` |

## 3. New issues (V7B-nn)

### V7B-01 UX - TLS chain card titles are hidden at 360 dp + font 1.3 (MEDIUM; C2; LOW at C1)
Repro: TLS Inspector, host example.com, Inspect, scroll to the "Intermediate / Intermediate / Root" cards.
Expected: each certificate name visible (wrap to 2 lines if needed).
Actual: C2 shows "Intermediate  ...  3127 days left" and "Intermediate  ...  4001 days left"; Root shows "SSL.co...". At C1 (400 dp, font 1.0) titles show as "Cloudflare T...", "SSL.com TL...", "SSL.com TLS ECC R...".
Screenshots: `.../v7b/c2_tls_result_d.png`, `.../v7b/c1_tls_result_e.png`.
Fix: let the name take a second line (or put the days-left chip on its own row) so the name never collapses to "...".

### V7B-02 UX - HTTP Probe result tab row clips the "Security" tab (MEDIUM; C2)
Repro: HTTP Probe, https://example.com, Send Request, portrait 360 dp + font 1.3.
Expected: all four tabs (Overview, Headers, Body, Security) reachable, with a visible scroll cue or fitting tabs.
Actual: the row ends with "Sec" cut at the card's right edge; no fade or cue that more tabs exist.
Screenshot: `.../v7b/c2_http_a.png`. Landscape and 400 dp portrait fit all four tabs.
Fix: use scrollable tabs with edge padding and a fade, or shrink the tab label padding at large font so four tabs fit.

### V7B-03 UX - Subnet "Network address" info icon squeezed to a dot (LOW; C2)
Repro: Subnet Calculator, 10.109.208.0/24, Calculate, C2.
Actual: the label "Network address" runs into the info icon, which collapses to a 2 px dot; other rows show a full icon.
Screenshot: `.../v7b/c2_subnet_a.png`. Fix: let the label wrap ("Network / address") or give the icon a fixed slot.

### V7B-04 UX - mDNS results are below the fold and in a tiny inner window at 360 dp + font 1.3 (MEDIUM; C2)
Repro: mDNS Browser, Scan Network, wait for the scan to finish, C2.
Expected: results are the primary content and visible; the page scrolls.
Actual: hero (56 found), a four-line notice, Scan Network / Clear and the search field fill the first screen. The results sit in an inner window of about 260 dp; a swipe at page level did not scroll the page, and only the inner list moves. Results show only the "Ping <address>" buttons in view, the service name above is cut away.
Screenshots: `.../v7b/c2_mdns_a.png`, `.../v7b/c2_mdns_c.png`. Relates to V6B-07 (worse at font 1.3).
Fix: let the whole screen scroll; collapse the hero to one line when results exist; move the notice into the results header.

### V7B-05 UX - WoL saved device row breaks the MAC mid-address (MEDIUM; C2)
Repro: Wake-on-LAN, type AA:BB:CC:DD:EE:FF, Save device (no name), C2.
Actual: the saved-device row shows "AA:BB:CC:DD:EE" on one line and ":FF" on the next; "Use", edit and delete icons sit on the same line as the MAC.
Screenshot: `.../v7b/c2_wol_saved.png`. Fix: show the name as title and the MAC in monospace on its own line, with actions in a row below (or a menu).

### V7B-06 BUG - MAC "Paste from clipboard" and "Look up" wrap badly at 360 dp + font 1.3 (MEDIUM; C2)
Repro: MAC Lookup, type an invalid entry (e.g. two bad entries), C2.
Expected: two readable buttons; no mid-word breaks.
Actual: "Paste from clipboard" becomes a tall pill broken into "Paste / from / clipboa / rd" (word split mid-token); "Look up" wraps to "Look / up". The error card and Recent row are not shown (V6B-11).
Screenshot: `.../v7b/c2_mac_ime_invalid.png`.
Fix: shorten to "Paste" (keep icon and content description) or stack both buttons full width at narrow widths; never split a word.

### V7B-07 A11Y - low-contrast green text on dark cards (LOW; C4; UNVERIFIED ratio)
Repro: dark mode, portrait 400 dp. HTTP Probe result: "200" (dark green on the dark card). TLS result: "52 days left" (green on a dark green chip).
Screenshots: `.../v7b/c4_http_b.png`, `.../v7b/c4_tls_b.png`.
Action: measure with a contrast tool; if below 4.5:1 for normal text, use the `onSurface`/`primary` tokens for these labels. Not measured in this pass.

### V7B-08 UX - mDNS results region is about 70 px tall in landscape (MEDIUM; C3)
Repro: mDNS Browser, Scan Network, landscape 400 dp (640x353 dp).
Actual: after the scan, the list is a sliver below Scan Network / Clear and the search field; the hero takes about 40 % of the height. Frame after swipe not reviewed.
Screenshot: `.../v7b/c3_mdns_a.png`. Fix: as V7B-04 (scrolling page; compact hero in landscape).

### V7B-10 DUP/UX - "CA / 3" orphan in TLS issuer (LOW; C1 and C4)
Actual: "Cloudflare TLS Issuing ECC CA" with "3" alone on the next line in the Issuer CN row (C1 wraps; C2 and C3 fit).
Screenshots: `.../v7b/c1_tls_result_c.png`, `.../v7b/c4_tls_b.png`. Fix: non-breaking space before the last token, or widen the value column.

### V7B-12 UX - TLS pin field placeholder wraps to two lines (LOW; C1, C2)
Actual: "Expected SHA-256 certificate pin (optional)" fills two lines inside the outlined field, larger than the host placeholder; reads like a value. Screenshot: `.../v7b/c2_tls_result_a.png`, `.../v7b/c1_tls_form_scrolled.png`. Fix: shorter placeholder, move "(optional)" to helper text.

### V7B-13 UX - TLS invalid-host error wraps with an orphan (LOW; C2)
Actual: "Hostname or IP cannot contain / spaces" (orphan "spaces"). Screenshot: `.../v7b/c2_tls_ime_invalid.png`. Fix: shorter copy or allow the field width to fit.

### V7B-14 UX - key/value column alignment differs between blocks (LOW; C1, C2)
Actual: HTTP timing list values start at x~532 px while the "Protocol" / "Time to first byte" rows start at x~500 px. TLS "Signature Algorithm" label wraps while the value sits on one line. Screenshots: `.../v7b/c1_http_b.png`, `.../v7b/c2_tls_result_c.png`. Fix: one shared value column width per card.

### V7B-15 UX/wording - WHOIS "Network Range" shows the ASN number (LOW; C1-C4)
Actual: AS15169 result shows "Network Range 15169" (an ASN, not a range). Screenshot: `.../v7b/c1_whois_b.png`. Fix: label "ASN" for ASN queries or show the prefix range.

### V7B-17 UX - two filled primary buttons on the WoL card (LOW; C2, C1)
Actual: "Send magic packet" and "Save device" are both filled green in one card; hierarchy unclear. Screenshots: `.../v7b/c2_wol_saved.png`, `.../v7b/c1_wol_valid.png`. Fix: make Save device a tonal/outlined button.

### V7B-18 UX - double divider with empty gap at the top of the leaf certificate (LOW; C1, C3)
Actual: two horizontal rules with about 50 px between them before "Issuer CN". Screenshots: `.../v7b/c1_tls_result_c.png`, `.../v7b/c3_tls_result_c.png`. Fix: drop one divider.

### V7B-19 UX - MAC notation copy is icon-only (LOW; C1-C4)
Actual: each notation row has an icon-only copy button, while TLS/WHOIS/HTTP/Subnet use labelled "Copy" / "Share". Screenshot: `.../v7b/c1_mac_b.png`. Fix: same labelled pattern (or at least a visible label at font 1.3).

### V7B-21 UX - Speed Test idle card pushes Start Test below the fold at C2 and C3 (LOW)
Actual: the idle card (gauge, "Ready to test your connection", stream chips) fills the first screen; Start Test is below it (C2 y ~1447 after scroll; C3 needs scroll). Screenshots: `.../v7b/c2_speed_idle.png`, `.../v7b/c3_speed_idle.png`. Fix: compact the idle card or put Start Test above the stream chips.

## 4. Still-present earlier findings re-observed in this pass

| ID | Status in VERIFY-7B | Evidence |
|---|---|---|
| V6B-07 (mDNS results below the fold) | Still present; worse at C2 (V7B-04) and C3 (V7B-08) | `c1_mdns_a.png`, `c2_mdns_a.png`, `c3_mdns_a.png` |
| V6B-11 (MAC Recent row hidden with error) | Still present at C2, C3 | `c2_mac_ime_invalid.png`, `c3_mac_ime.png` |
| V6B-12 (landscape More sheet small scroll region) | Still present (about 400 px list) | navigation in C3 |
| V6B-13 (primary action under IME in landscape) | Still present: TLS, WHOIS, WoL (Send under IME) | `c3_tls_ime.png`, `c3_whois_ime.png`, `c3_wol_ime.png` |
| PHB-10 (MAC auto-capitalises) | Still present in C2 and C3 ("Zz-00:1A", "Zz 00:1A:12:34:56") | `c3_mac_ime.png`, `c2_mac_ime_invalid.png` |
| PHB-11 (Recents store typos) | Still present in WHOIS Recent ("example.commm", "example.coexample.co...") | `c3_whois_ime.png` |
| PHB-08 (HTTP "HTTP" icon text and URL duplicated) | Still present (hero icon is text "HTTP"; URL chip and result title) | `c4_http_a.png` |

## 5. Things that worked well (this pass)
- Inline validation and IME behaviour: field, inline error and Recent row stay above the keyboard in portrait (C2) for TLS, WHOIS, HTTP, Subnet, MAC and WoL; landscape keeps field + error + Recent visible for TLS, WHOIS, HTTP, MAC.
- HTTP invalid URL shows "The URL could not be parsed." with Send disabled and the Recent row kept; no hidden error.
- The WoL delete confirmation copy is clear and the Cancel / Delete pair is consistent; no send was attempted.
- Speed Test chart ticks are round (0-60) and copy uses "Avg" consistently.
- Dark mode (C4) is clean on all eight tools: no hard-coded light colours seen; primary buttons (mint) read well.
- Landscape (C3) fits all results except mDNS list height (V7B-08); tab row and TLS cards fit at 640 dp.

## 6. Coverage notes and limits
- Every tool x config cell above is P, an issue ID, or NT with reason. The only NT cells are Speed Test runs in C2, C3 and C4 (one-run limit: a single run was made in C1). Their idle state was reviewed instead.
- Not reviewed frame by frame: `c3_mdns_b.png` (after swipe in landscape), `c2_wol_delete.png` (only dialog text read via labels), `c1_http_c.png`, `c1_whois_c.png`, `c2_whois_b`, `c4_wol_b.png`. Findings above rely on other frames or labels for these states.
- Contrast ratios for V7B-07 are UNVERIFIED (visual only).
- Test data: saved device "AA:BB:CC:DD:EE:FF" created in C2 and deleted via the confirm dialog. Recent entries for TLS (wrong.host.badssl.com, example.com, 1.1.1.1), HTTP (httpbin.org), WHOIS (AS15169, example.commm) pre-existed; not removed.
- Network: example.com, rdap.arin.net (WHOIS AS15169), local subnet 10.109.208.0/24 (Subnet calc only, no scan this pass except mDNS multicast), Cloudflare speed test once. No Wake-on-LAN packet sent.
- Restore done: `wm density reset`, `wm size reset`, accelerometer_rotation 1, user_rotation 0, font_scale 1.0, `cmd uimode night no`, `pm revoke ... ACCESS_FINE_LOCATION` (exit 0). Post-restore: density Physical 320 only, font 1.0, user_rotation 0, accelerometer 1.
