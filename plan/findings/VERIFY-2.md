# Verification – Group 2 (post-remediation, WP18)

Build: net.aieat.netswissknife.debug 1.0.0-debug, installed 2026-10-31 20:22 (HEAD 97af785 committed 20:21), branch plan/ux-remediation-2026-10-31. Device: Android 8.0 tablet U8BBB20806201256, 1200x1920, Wi-Fi with internet.
Inputs read (read-only): C-inspect.md, D-local.md, WP08–WP17 result blocks in plan/plan.yaml.
Screenshots and UI dumps live in /tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/verB/shots/. Evidence column names are file names in that folder.
Device settings restored and re-checked at the end: accelerometer_rotation=1, user_rotation=0, font_scale=1.0, night mode no. No `am kill-all`. Once `am force-stop` was used on the app (not kill-all). Host-side tools used: curl (RDAP and ipify checks), Python (parsing the device CA store).

Result key: PASS, FAIL, PARTIAL (fix present, not complete), NOT-REPRODUCIBLE (repro ran, defect absent), UNVERIFIED (not confirmed; reason given).

## Findings verification

### TLS Inspector

| Finding | Result | Evidence |
|---|---|---|
| TLS-01 wrong-host cert shown "Trusted" | PASS | wrong.host.badssl.com:443 → badge "Untrusted". The Hostname row was not captured in this pass. `tls_01_wronghost.png` |
| TLS-02 "Trusted" pill uses error colour | PASS | Green text on neutral chip. `tls_02_example.png` |
| TLS-03 pin mismatch does not change verdict | PASS | example.com:443, pin 00112233…FF → badge "Untrusted", row "Expected pin: Mismatch", finding PIN_MISMATCH "does not match the expected SHA-256 pin". `tls_03_pin_bad_scrolled.png` |
| TLS-04 1.1.1.1 shown "Untrusted" | PARTIAL (root cause confirmed) | Finding text now "not trusted by this device's certificate store". Badge still "Untrusted". Root cause: neither device store has an SSL.com root. Pulled /system/etc/security/cacerts (156 certs) and /system/etc/security/cacerts_google (59 certs), parsed the PEMs and searched the DER for "SSL Corporation" / "SSL.com": 0 hits in both. The 1.1.1.1 chain ends at an SSL.com root, so it cannot verify on this device. `tls_04_1111_scrolled.png` |
| TLS-05 plain-HTTP port gives generic error | FAIL | example.com:80 → "Inspection failed / Handshake failed / Retry". No "port answered with plain text" message. The message mapping probably does not match the Android 8 text (not confirmed from logs). `tls_05_port80.png` |
| TLS-06 self-signed connect-time outlier | UNVERIFIED | Not re-run. |
| D:TLS-05 recent chips clipped | UNVERIFIED | Fade added in code; chip row not re-inspected. |
| D:TLS-06 timeout README drift | PASS (source) | Timeout is fixed, README corrected (WP15). |
| Cancel mid-run (new) | PASS | badssl.com:8443, Cancel during "Inspecting…" → "Inspection canceled". `tls_06_cancel.png` |
| Rotation during run, dark, font 1.3 (D run) | not repeated | Recorded in D-local as PASS; not re-run in this pass. |

### WHOIS Lookup

| Finding | Result | Evidence |
|---|---|---|
| WHOIS-01 empty domain-status chips | PASS | "Delete Locked", "Transfer Locked", "Update Locked". `whois_01_example_auto.png` |
| WHOIS-02 WHOIS mode dates from IANA | PASS | Registered 1995-08-14, Updated 2026-08-14, Expires 2027-08-13 (matches Verisign). Chain: whois.iana.org → whois.verisign-grs.com. `whois_05_whois_lifecycle.png` |
| WHOIS-03 raw section opens only via switch | PASS | Tapping the label row expands the section (switch on, chevron up); the raw text itself was not captured. `whois_08_raw_after_label.png` |
| WHOIS-04 "999.1.1.1" generic error | PASS | "An IPv4 address needs four numbers from 0 to 255". `whois_12_999_error.png` |
| WHOIS-05 IANA node pink | PASS | IANA and REGISTRY nodes both green. `whois_08_raw_after_label.png` |
| WHOIS-06 Auto IP/ASN falls back to WHOIS, protocol unclear | PARTIAL (root cause confirmed, see V-07) | Answer line now says "Answered by WHOIS (port 43) from whois.arin.net". Root cause confirmed: RDAP-only for 8.8.8.8 on the device → "Lookup failed – RDAP request failed with HTTP 403" (`whois_15_rdap403.png`). Host check: rdap.org returns 403 for the OkHttp default user agent (`okhttp/4.12.0`) and 200 for a custom UA. Auto mode falls back silently. |
| WHOIS dark mode, font 1.3 (WP09 deferred) | UNVERIFIED | Not run. |
| WHOIS rotation during lookup | UNVERIFIED | Not run. |

### HTTP Probe

| Finding | Result | Evidence |
|---|---|---|
| HTTP-01 Overview Content-Type "N/A" | PASS | "text/html; charset=utf-8". `http_03_ctype.png` |
| HTTP-02 timing phases exceed total | PASS | example.com: DNS 55 + Connect 20 + TLS 140 + Server 155 + Transfer 7 = 377 ms; response 384 ms; TTFB 370 ms. `http_02_overview_scrolled.png` |
| HTTP-03 trailing space errors | PASS | "https://google.com " sent: 200 with 1 redirect. `http_04_google_enter.png` |
| HTTP-04 keyboard covers results after Enter | PASS | IME Enter submits; keyboard dismissed; results visible. `http_04_google_enter.png` |
| HTTP-05 "[path omitted]" on root URL | PASS | Root → "https://example.com/". POST with a path → "[path omitted]". `http_01_example_overview.png`, `http_14_post_result.png` |
| HTTP-06 DNS and timeout share generic text | PASS | nonexistent-host.invalid → "The DNS lookup failed."; badssl.com:8443 → "The network request timed out."; 127.0.0.1:1 → "The server refused the connection." `http_06_dns_fail.png`, `http_07_timeout_8443.png`, `http_16_refused` (text in dump) |
| HTTP-07 selected GET chip blue | PASS | GET and POST selected chips are both mint. `http_11_post_selected.png` |
| D:HTTP-05 Copy as cURL no feedback | PASS | Toast "cURL command copied to clipboard". `http_05_copy_curl_toast.png` |
| D:HTTP-06 stale "Prefilled" banner | PASS | Editing the URL clears the handoff notice. `http_15_after_edit.png` |
| D:HTTP-07 body field icon and clear | PASS (icon) / UNVERIFIED (clear) | Pencil leading icon and "Request Body" label visible. Clear button with text not observed. `http_11_post_selected.png` |
| POST with body and custom header (new) | PASS | POST example.com/post-test, X-QA-Test: qaD-1, JSON body → 405, same as host curl. Redirect-disabled notice shown. `http_14_post_result.png` |
| Cancel during request (new) | PASS (after retry) | Portrait cancel → "Request canceled". Two earlier cancel attempts had no effect (see V-04). `http_10_cancel_portrait.png` |
| Rotation during request (new) | PASS | Landscape kept "Sending…" and disabled inputs. `http_08_rotate_during.png` |

### Subnet Calculator

| Finding | Result | Evidence |
|---|---|---|
| SUB-01 /31 shows 0 usable | PASS | 10.0.0.0/31: "2 usable hosts", Usable 2, first/last consistent. `sub_01_31.png` |
| SUB-02 stale result after clearing input | PASS | Clear button empties the result and shows the empty state. `sub_02_cleared.png` |
| SUB-03 info icons 28 px, "Help", binary cells unlabelled | PASS | Icons are 48 dp (96 px); descriptions "Explain Network address" etc. Binary rows are one content description: "Network address, 12 network bits, 20 host bits" (from the UI dump). |
| SUB-04 tablet single column stretched | PARTIAL | Landscape content capped at about 1200 px, centred. Still one column; no two-pane (deferred). `sub_10_landscape.png` |
| SUB-05 no copy or share | PASS | Copy → "Copied to clipboard" toast; Share → share sheet. `sub_05_copy_toast.png`, `sub_06_share.png` |
| SUB-06 abbreviated counts | PASS | 16,777,214 (10.0.0.0/8); 4,194,302 (100.64.0.0/10); 254 (/24). |
| SUB-07 "8 / 32" row | PASS | Now "Prefix length /24". |
| SUB-08 100.64.0.0/10 labelled Public | PASS | "Shared (CGNAT, RFC 6598)". `sub_09_cgnat.png` |
| SUB-09 keyboard opens on entry | PASS | On entry: no keyboard (`dumpsys input_method` mInputShown=false). `sub_11_entry_keyboard.png` |
| SUB-10 wrong icon | PASS | Network glyph in the input. `sub_10_landscape.png` |
| SUB-11 "Invalid prefix:" with nothing after | PASS | "Enter a prefix length after the slash, for example /24." (UI dump text only) |
| SUB-12 IPv6 rejected | PASS (by design) | "IPv6 is not supported yet. Enter an IPv4 address or subnet." |
| SUB-13 "Network addr" truncated | PASS | Label "Network address" in full. |
| SUB-14 "1.2.3.4/08" silently read as /8 | PASS | "08 has a leading zero. Remove it, for example 8 instead of 08." |
| SUB-15 "1 usable hosts" | PASS | "1 usable host" (10.0.0.5/32). |
| APP-03 name mismatch | PARTIAL | Home tile and screen title read "Subnet Calculator". The All Tools sheet was not opened. |
| New: membership check | PASS | 192.168.1.50 → "Yes… usable host address"; 10.0.0.1 → "No, outside"; 192.168.1.0 → network-address reason. `sub_07_membership_yes.png` |
| New: split into equal subnets | PASS | 192.168.1.0/24 → /26 gives "4 subnets of /26" with rows. `sub_08_split26.png` |

### mDNS Browser

| Finding | Result | Evidence |
|---|---|---|
| MDNS-01 escapes shown raw | PASS | "HP LaserJet 300 colorMFP M375nw (615DC8)" decoded. The MacBook/_airplay entry from the first run was not in this scan. `mdns_01_done.png` |
| MDNS-02 search without icon or clear | PASS | Magnifier leading icon; clear X appears with text. `mdns_04_clipboard_chip.png` |
| MDNS-03 no-match blank list | PASS | "No services match \"airplay\"" with "Clear search" action. `mdns_02_airplay.png` |
| MDNS-04 copy/share ignores filter | PASS | Filtered Copy (search "LaserJet") put "mDNS services: 7 / Filter: LaserJet / Discovery window complete. / Truncated: ADDRESS_LIMIT / …" on the clipboard (read by pasting into MAC Lookup, dump shows real newlines). `mdns_clip_paste_in_mac.png` |
| MDNS-05 "Open in HTTP Probe" prefills http://…:80 | PASS | Port 80 service: URL left empty with "npi615dc8.local uses plain HTTP on port 80. HTTP Probe sends HTTPS only, so no URL was filled in." `mdns_05_handoff_8080.png` |
| MDNS-05 handoff for :443 / :8443 (WP10 deferred) | UNVERIFIED | Not run in this pass. |
| MDNS-06 README / help drift | PASS | Button labelled "Clear" (source for README). |
| MDNS-07 red banner, limit not named | PASS | Neutral info banner names the limit: "addresses per service (max 16 addresses or 1024 bytes)". This LAN raises it on each full scan (about 64 services). `mdns_01_done.png` |
| Rotation during scan (WP05 device check) | PASS | Landscape kept "Scanning…" and Stop, then completed. `mdns_06_rotate_scan.png` |

### Speed Test

| Finding | Result | Evidence |
|---|---|---|
| SPD-01 upload fails with raw "timeout" | PASS | One full run completed: download 29.0 Mbps avg (38.4 peak, 34.9 MB); upload 15.9 Mbps avg (24.8 peak, 18.9 MB); latency 66 ms min. No error. `spd_04_final.png` |
| SPD-02 failure discards partial results, raw text | UNVERIFIED (N/A this pass) | No failure happened, so the error path was not exercised. |
| SPD-03 cancel with no message, two Start buttons | PASS | Cancel → "Test canceled" notice, single "Start Test" button. `spd_06_cancel.png` |
| Share results (new) | PASS | Opens the Android chooser. `spd_05_share.png` |

Run count: one full test, plus one "Run again" cancelled after about 3 s to check the notice.

### Wake-on-LAN

| Finding | Result | Evidence |
|---|---|---|
| WOL-01 validators disagree, AABB.CCDD.EEFF rejected | PASS | Accepted with no message: AA-BB-CC-DD-EE-FF, AABB.CCDD.EEFF, aabbccddeeff, padded "  02:ab:cd:ef:12:34  ". Specific rejections: "AA:BB:CC:DD:EE" (12 hex digits); "zz:zz…" (digits 0-9, A-F); 01:00:5E:00:00:01 ("Multicast addresses are not devices…"); FF:FF:FF:FF:FF:FF ("broadcast address, not a device"); 00:00:00:00:00:00 ("is not a device address"); mixed separators ("use one notation…"). `wol_01_validation.png`, `wol_02_multicast.png` |
| Broadcast validation (new) | PASS | "10.0.0.256" and "example.com" → "Enter an IPv4 address such as 192.168.1.255. Host names are not supported…". 255.255.255.255 accepted. |
| UDP port validation (new) | PASS | 0, 70000, abc → "Enter a UDP port from 1 to 65535"; Send disabled. `wol_04_port_abc.png` |
| Saved devices add (new) | PASS | "QA dummy" saved for 02:AB:CD:EF:12:34. `wol_05_saved.png` |
| Saved devices edit (new) | PASS | Form prefilled; renamed to "QA renamed"; list updated. `wol_07_renamed.png` |
| Saved devices duplicate MAC (new) | PASS with note (V-03) | No duplicate row; the existing row's name was replaced without a message. `wol_08_dup.png` |
| Saved devices use and delete (new) | PASS | Use fills the MAC field; Delete empties the list. `wol_09_deleted.png` |
| Packet send (one packet) | PASS | Made-up MAC 02:AB:CD:EF:12:34 to 255.255.255.255 port 9 only. UI: "Magic packet sent", "Sent to 02:AB:CD:EF:12:34 via 255.255.255.255 port 9 (3 packets)", fire-and-forget note. `wol_10_sent.png` |
| Advanced panel defaults | PASS | Broadcast 255.255.255.255 with hint; UDP port 9 with discard-port hint. `wol_03_advanced.png` |
| Help, rotation during Sending | UNVERIFIED | Not run (send is instant). |

### Network Info (new, WP16)

| Check | Result | Evidence |
|---|---|---|
| Cards render: active network, Wi-Fi link, IP config, gateways, DNS, MTU, MAC, search domains, proxy, public IP | PASS | Wi-Fi: -52 dBm, 65 Mbps, 2417 MHz (2.4 GHz, channel 2). Network name "Unavailable (location permission needed)". `ni_00_top.png`, `ni_01_bottom.png` |
| SSID redaction without location permission | PASS | Shows "Unavailable (location permission needed)". |
| IPv4 and IPv6 addresses, prefixes, gateways, DNS | PASS | 10.109.208.146/24, gateway 10.109.208.1, global and link-local IPv6. |
| Check public IP explicit only | PASS | Button only on entry. Result 67.208.54.3, London, AS51809. Host curl to api4.ipify.org also returned 67.208.54.3. `ni_02_public_ip.png` |
| Copy all | PASS | "Copied" toast. `ni_03b_copy_all.png` |
| Per-value copy icon | UNVERIFIED (toast) | Tapped; toast not captured (`ni_03_copy_ipv4.png` taken, not inspected). |
| Share | PASS | Android share sheet. `ni_04_share.png` |
| Rotation | PASS | Landscape renders, content centred at about 1200 px. `ni_05_landscape.png` |
| Offline error path | UNVERIFIED (environment blocked) | `adb shell svc wifi disable` fails: SecurityException (CHANGE_WIFI_STATE), system process. Wi-Fi was never turned off. Connectivity confirmed after the attempts: wlan0 inet 10.109.208.146/24, WIFI CONNECTED, and a public IP lookup returned 67.208.54.3. |
| Refresh button, dark mode, font 1.3 | UNVERIFIED | Not run for this tool. |

### MAC Lookup (new, WP17)

| Check | Result | Evidence |
|---|---|---|
| Notations: colon, hyphen, Cisco dotted, bare | PASS | 00:1A:2B:3C:4D:5E shows all four notations. `mac_01_batch.png`. Lowercase toggle not exercised. |
| Multicast | PASS | 01:00:5E:00:00:FB → "Multicast (a group of devices)", vendor "Unknown vendor". |
| Broadcast | PASS | FF:FF:FF:FF:FF:FF → "Not applicable: broadcast address", type Broadcast; "Locally administered: Yes" (the U/L bit is set in FF, see V-05). `mac_01_batch.png` |
| Locally administered | PASS | 02:00:00:00:00:01 → "Not meaningful: locally administered" with explanation. 33:33:00:00:00:01 → Multicast, locally administered. |
| OUI-only prefix | PASS | 00:1A:2B → "OUI prefix only…", Ayecom Technology, MA-L. |
| Invalid input | PASS | zz:zz → "Nothing to look up… Try again". Batch with one bad entry → "1 entry is not a MAC address or OUI prefix". |
| Paste from clipboard | PASS | Copied the device MAC from Network Info; Paste from clipboard filled 34:46:EC:B4:F4:70 and looked it up at once → "Huawei Device Co., Ltd.". `mac_03_paste.png` |
| Copy all | UNVERIFIED (toast) | Tapped; toast not captured (`mac_04_copy_all.png` taken, not inspected). |
| Share | PASS | Android share sheet. `mac_05_share.png` |
| Dark mode | PASS | Dark surfaces, mint primary, readable result card. `mac_07_dark.png` |
| Font 1.3 | PASS | Layout wraps cleanly; "Paste from clipboard" wraps to two lines. `mac_08_font13.png` |
| Landscape | UNVERIFIED | Captured (`mac_06_landscape.png`), not inspected. |

## Regression and fresh exploratory pass

- Rotation: HTTP Probe (during Sending) PASS; mDNS (during scan) PASS; Subnet (landscape) PASS; Network Info (landscape) PASS; MAC Lookup captured only.
- Dark mode: MAC Lookup PASS. TLS and Subnet from the D run (not repeated). WHOIS not run.
- Font 1.3: MAC Lookup PASS. TLS from the D run (not repeated). WHOIS not run.
- Keyboard: HTTP Probe dismisses on Enter (PASS). Subnet has no keyboard on entry (PASS). MAC Lookup keeps the keyboard over results after Look up (V-01). mDNS keyboard covers results in landscape (D-local, not re-checked).
- Back navigation: Subnet Back → Home (PASS). mDNS → HTTP Probe handoff; Back returns to mDNS (PASS).
- Process death (WP15 device check): `am kill` on the app did not terminate its process (same pid before and after, presumably because the app was not in the cached state). Restore after relaunch came from the same process, so the check is UNVERIFIED. Inputs were restored on relaunch.
- logcat (AndroidRuntime:E): zero "Process: net.aieat…" entries, so the app did not crash. Three FATAL entries (system process, SecurityException from `svc wifi`) are from the svc command, not the app. Checked after night-mode and font changes: none new.
- Touch targets: Subnet info icons 48 dp; Network Info copy icons 48 dp.

## NEW issues (V-prefixed)

| ID | Type | Severity | Title | Repro and evidence | Suggested fix |
|---|---|---|---|---|---|
| V-01 | UX | medium | MAC Lookup: keyboard stays open after Look up and covers results | Type several MACs, tap Look up. The keyboard stays up and hides the result cards. `mac_01_batch.png` | Clear focus and hide the keyboard on Look up and Paste (as HTTP Probe does, WP01). |
| V-02 | UX | low | MAC Lookup: duplicate notations of one MAC give duplicate cards | 00:1A:2B:3C:4D:5E, 00-1a-2b-3c-4d-5e, 001A.2B3C.4D5E and 001a2b3c4d5e give four identical cards; the count shows "10 entries" for the batch. `mac_01_batch.png` | Merge identical canonical MACs and mark "duplicate of entry N". |
| V-03 | UX | low | Wake-on-LAN: re-saving an existing MAC silently renames it; name field keeps old text | Save "QA dummy" for 02:AB:CD:EF:12:34, then save "dup" for the same MAC: one row, named "dup", no message; the name field still shows "dup". `wol_08_dup.png` | Say "Already saved as QA dummy, renamed" (or refuse), and clear the name field after save. |
| V-04 | BUG (UNVERIFIED, intermittent) | medium | HTTP Probe: Cancel during Sending did not take effect on two of four attempts | Attempt 1: start 8443 request, rotate to landscape, tap Cancel at 1332,1124: no effect. Attempt 2: rotate back, tap Cancel at 912,1240: no effect; the request ended on its own with "The network request timed out." Attempt 3: start, rotate to landscape, tap Cancel at the dumped position: idle "Send Request" with no error text. Attempt 4: fresh request in portrait, Cancel at 912,1240: "Request canceled". Repeat before filing. `http_08_rotate_during.png`, `http_09_after_cancel.png`, `http_10_cancel_portrait.png` | Check the cancel path across configuration change; add a repro test. |
| V-05 | UX | low | MAC Lookup: broadcast FF:FF:FF:FF:FF:FF shows "Locally administered: Yes" | Correct (U/L bit set), but a reader may take it as a problem. `mac_01_batch.png` | Show the U/L row only for unicast, or add "(bit set in broadcast)". |
| V-06 | Environment note | — | `svc wifi disable` is refused on this device | SecurityException (CHANGE_WIFI_STATE), system process. The offline path for Network Info cannot be driven with adb here. | Test-environment note, not an app issue. |
| V-07 | BUG | medium | WHOIS: IP and ASN RDAP lookups are rejected by rdap.org (HTTP 403) for the app's user agent; Auto mode hides it | Device: 8.8.8.8 in RDAP-only → "RDAP request failed with HTTP 403". Host: `rdap.org/ip/8.8.8.8` returns 403 for UA `okhttp/4.12.0` and 302→200 for other UAs (for example `NetSwissKnife/1.0` returns 200). `whois_15_rdap403.png`. Auto mode then falls back to WHOIS with no RDAP failure shown. | Send a descriptive User-Agent on RDAP requests (for example "NetSwissKnife/<version>"). Show the RDAP failure reason in the Auto query chain. |
| V-08 | UX | low | WHOIS: DNSSEC value differs between modes | WHOIS mode shows the raw value "signedDelegation"; Auto (RDAP) shows "Signed". `whois_08_raw_after_label.png`, `whois_02_scrolled.png` | Map the raw WHOIS DNSSEC value to the same label as RDAP. |

## Not run (honest coverage gaps)

- WHOIS: dark mode, font 1.3, rotation during lookup, RDAP-only mode for an IP was run only for 8.8.8.8 (failure above).
- TLS: TLS-06 and D:TLS-05 (chip fade); TLS-05 root-cause log capture; rotation, dark and font were not repeated (from the D run).
- HTTP Probe: PUT, PATCH, DELETE, HEAD, OPTIONS; Follow Redirects OFF; dark and font for this screen; body field clear with text.
- Subnet: IP Range tab; dark mode in this pass.
- mDNS: Stop mid-scan; TXT expand; :443 and :8443 handoff (UNVERIFIED).
- Speed Test: error path (SPD-02); rotation mid-run (from the D run).
- Wake-on-LAN: Help; rotation during Sending.
- Network Info: offline path (environment); Refresh; dark; font.
- MAC Lookup: lowercase toggle; landscape and copy-all toast (captured, not inspected).
- Home: "Scroll for more tools" hint (APP-01) not re-checked; All Tools sheet (APP-03) not opened.
- WP15 process-death restore: not verified (see Regression).

## What worked well

- TLS: verdict follows hostname and pin failures; pill colour correct; pin mismatch is a clear finding.
- WHOIS: status chips labelled; registry dates correct; raw section opens from the whole row; chain colours consistent.
- HTTP Probe: timing reconciles; root URL and DNS / refused / timeout messages are specific; trailing space trimmed; keyboard dismisses on Enter; Copy as cURL toast; POST with header and body works.
- Subnet: /31 correct; clear resets results; exact counts; scope labels; membership and split features work and read well; binary rows have one clear description.
- mDNS: DNS-SD escapes decoded; no-match state with Clear search; banner names the limit; filtered Copy includes the filter line; handoff explains rather than prefilling a broken URL.
- Speed Test: a full run now completes the upload phase; cancel notice; a single Start button.
- Wake-on-LAN: validation messages are specific per cause; saved devices work end to end; send confirmation is honest about fire-and-forget.
- Network Info: clear card layout; explicit public IP check; the result matches the host.
- MAC Lookup: clear per-address breakdown; offline vendor data; paste from clipboard looks up at once; dark mode and font 1.3 are clean.

## Tablet look and feel

- Portrait: single centred column (about 1000 px) beside the nav rail; no stretched fields.
- Landscape: content capped at about 1200 px and centred, with empty side gutters. No master/detail on Subnet, HTTP Probe, MAC Lookup or Network Info. Two-pane remains deferred.
- Touch targets, info icons and copy icons are 48 dp.
- Font 1.3 on MAC Lookup wraps without clipping.
