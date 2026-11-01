# Device verification: GROUP 3, round 2 (WP19 and WP20 items, plus smoke pass)

Device: U8BBB20806201256 (Android 8.0, 1200x1920 @ 320 dpi, density 2). `net.aieat.netswissknife.debug` built from HEAD f6174f9 (WP19 + WP20, branch `plan/ux-remediation-2026-10-31`). Build check: the installed app shows the round-2 behaviour (WHOIS DNSSEC "Signed", RDAP answers, Traceroute messages).
Evidence: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/verC` (file names below are relative to that folder).
Status legend: PASS, FAIL, PARTIAL, NOT-REPRODUCIBLE, UNVERIFIED. Source not edited. No `am kill-all`. Settings restored at the end: accelerometer_rotation 1, user_rotation 0, font_scale 1.0, night off. The made-up Wake-on-LAN device (02:AB:CD:EF:12:34) was deleted afterwards. No packets were sent to it.

## Verification table (round-2 items)

| Finding | Result | Evidence |
|---|---|---|
| SHELL-01 Home hint over last tile row (WP19) | PASS | Hint sits in its own slot below the grid. At the end of the grid the last row (Network Info, MAC Lookup) is fully visible with no overlap. `02-home-scrolled-end.png`, `03-home-scrolled-end2.png` |
| VERIFY-1 V-02 DNS Look up with IME open, after rotation | PASS (action visible) | Portrait with IME: Look up sits above the IME (`14-dns-kbd-portrait.png`). Landscape with IME: Look up button ends at the IME top edge (y ~540), fully visible (`15-dns-kbd-land.png`). IME reopen on its own: YES. The IME was closed with Back, then the device was rotated: the IME reopened by itself, focus landed on the DNS server field, and Look up stayed visible (`16-dns-rot-portrait-noime.png`). |
| VERIFY-1 V-02 DNS after dark mode | PASS (action visible), IME reopens: YES | IME closed with Back, then dark mode on: IME reopened and Look up visible (`18-dns-dark-land.png`). |
| VERIFY-1 V-02 DNS after font 1.3 | PASS (action visible), IME reopen: not isolated | Portrait, font 1.3: Look up above the IME (`19-dns-font13-portrait.png`). The IME was already open from the dark-mode step, so this does not test a reopen. |
| VERIFY-1 V-02 Port Scanner Scan after rotation with IME | PASS (action visible), IME reopen: YES on font 1.3, rotation test began with IME open | Font 1.3, portrait: IME opened on entry to the screen by itself; Scan Ports above it (`20-ports-font13.png`). Landscape with IME open: Scan Ports visible above the IME (`21-ports-land-kbd.png`). |
| VERIFY-1 V-02 Port Scanner after dark mode | PASS (action visible), IME reopens: NO | IME was closed before the dark-mode change. It did not reopen. Scan Ports visible (`22-ports-dark-land.png`). |
| VERIFY-1 V-03 NXDOMAIN neutral card | PASS | Summary card uses a neutral grey gradient, RCODE in neutral text (`12-dns-nx.png`). Below Authority: "Domain not found" card with "The DNS server reports that nosuch-zz-test.example does not exist." (`13-dns-nx-scrolled.png`) |
| VERIFY-1 V-01 Ping garbled host "10.109.208.25010.109.208.200" (WP19 saved-state fix) | PASS (rejected, not saved) | Field verified empty first (placeholder). Typed the value and pressed Enter: field red, Start Ping disabled, no run, no new Recent chip. `24-ping-garbled.png`. A chip with this value already existed from the VERIFY-1 run (see V-10). Error text is wrong (see V-09). |
| VERIFY-1 V-06 Topology state kept after leaving and returning | PASS | Ran discovery against 10.109.208.1 (SNMP failed), left the screen while it was running, returned: "Discovery Failed" card and seed 10.109.208.1 kept in SNMP Configuration. `31-topo-run.png`, `32-topo-return.png` |
| VERIFY-1 V-07 Wi-Fi search Clear keeps the keyboard closed | FAIL | Typed in the search field, tapped Clear: IME opened and the search field took focus, 2 of 2 attempts (`34-wifi-clear.png`, `35-wifi-clear2.png`). ToolClearButton's clearFocus() does not hold on this field. |
| DNS-06 / PING-07 / PORTS-05 chip-row edge fades | PASS | Ping recents: fade on the cut-off right edge and on the left after scroll (`25-ping-recents-scrolled.png`). DNS record types: right fade plus chevron (`10-dns-idle.png`). Port Scanner recents: right fade (`20-ports-font13.png`). |
| VERIFY-2 V-01 MAC Lookup keyboard hides after Look up | FAIL | Look up tapped twice: IME stayed open, field kept focus, and the result cards sat under the keyboard until it was dismissed by hand (`41-mac-result.png`, `42-mac-lookup2.png`). |
| VERIFY-2 V-02 MAC duplicate notations merge | PASS | Four notations of 00:1A:2B:3C:4D:5E gave one card, "Entered 4 times, merged into one card"; summary "2 entries" with the broadcast (`43-mac-results.png`). |
| VERIFY-2 V-05 Broadcast FF:FF:FF:FF:FF:FF locally administered | PASS | "Locally administered: Not applicable" with "Not applicable: broadcast address" (`43-mac-results.png`). |
| VERIFY-2 V-08 WHOIS DNSSEC in WHOIS mode | PASS | icann.org, WHOIS mode, answered by whois.publicinterestregistry.org: DNSSEC row shows "Signed" (`50-whois-icann.png`). |
| VERIFY-2 V-07 RDAP 403 on IP, RDAP-only | PASS | 1.1.1.1 RDAP-only: "Answered by RDAP from rdap.apnic.net", no HTTP 403 (`51-rdap-1111.png`). |
| VERIFY-2 V-07 RDAP 403 on ASN, RDAP-only | PASS | AS13335 RDAP-only: CLOUDFLARENET, "Answered by RDAP from rdap.arin.net" (`52-rdap-asn.png`). |
| VERIFY-2 V-07 Auto mode on an IP names the protocol | PASS | 8.8.8.8 Auto: "Answered by RDAP from rdap.arin.net" (`54-auto-8888.png`). The "RDAP could not answer" note was not triggered because RDAP succeeded, so that branch is not exercised. |
| VERIFY-2 TLS-05 TLS on example.com port 80 | PASS (wording differs from the WP20 expectation) | Title "Inspection failed", text: "This port answered with plain text, not TLS. Check the port, or use HTTP Probe for a plain-HTTP service." Recorded as shown; the expected phrase "does not appear to speak TLS" does not appear (`60-tls-80.png`). |
| VERIFY-2 TLS-04 TLS on 1.1.1.1:443 untrusted root wording | PASS | "This device's trust store does not include the issuing root (SSL.com Root Certification Authority ECC)"; verdict Untrusted (`61-tls-1111.png`). |
| VERIFY-2 V-04 HTTP Probe Cancel, 5 of 5 on https://httpbin.org/delay/10 | PARTIAL (11 of 14 confirmed, 1 failed, 2 unconfirmed) | Attempt 1 (portrait, Cancel 2 s after Send) ended "The network request timed out.", no cancel effect (`70-http-cancel-a1.png`). 11 further runs ended "Request canceled": 1 s and 2 s portrait; rotate to landscape during Sending, then Cancel (2 s, 1 s, and immediately after Send); rotate round-trip then Cancel. Two landscape runs (`72-http-cancel-rot.png`, `73-http-cancel-rot-fast.png`) returned to idle "Send Request", but the result line was not read, so they are unconfirmed. See V-13. |
| VERIFY-2 V-04 rotation during a slow request, then Cancel | PASS | Rotate during Sending, then Cancel: "Request canceled" (`81-r-2s.png`, `82-r-0s.png`, `84-r-1s.png`, `86-p-2s-c.png`). |
| VERIFY-2 V-03 WoL re-save existing MAC, blank name | PASS | Re-save with blank name: "Already saved as QA dummy. The saved name was kept." One row, name kept (`90-wol-blank-resave.png`). |
| VERIFY-2 V-03 WoL re-save with a new name | PASS | "Already saved as QA dummy. Renamed to dup." One row, named dup (`91-wol-rename.png`). |
| VERIFY-1 V-04 Traceroute 1.1.1.1 stops at destination | PASS | 11 hops, Reached "Yes", hop 11 is the destination. No duplicate hops after it in the summary (`101-tr-1111-a.png`). |
| VERIFY-1 V-05 Traceroute to unreachable 10.109.208.250 | PASS | 2 hops, Reached "No", 3095 ms. Message: "Host unreachable from this network (no route / destination host unreachable)" (`102-tr-unreach.png`, `103-tr-unreach-scrolled.png`). |

## Smoke pass: all 16 tools

| Tool | Opened | Exercised | Crash |
|---|---|---|---|
| Ping | yes | garbled-host rejection, recents chip fade | none |
| Traceroute | yes | 1.1.1.1 full run; 10.109.208.250 run; invalid-input text check | none |
| Port Scanner | yes | rotation, dark, font 1.3 with IME | none |
| LAN Scanner | yes | opened only | none |
| DNS Lookup | yes | NXDOMAIN run; IME checks | none |
| Wi-Fi Scanner | yes | location granted, scan results shown, search Clear | none |
| Network Topology | yes | discovery run, leave, return | none |
| TLS Inspector | yes | port 80 and 1.1.1.1 runs | none |
| WHOIS Lookup | yes | WHOIS icann.org; RDAP 1.1.1.1; RDAP ASN; Auto 8.8.8.8 | none |
| HTTP Probe | yes | cancel and rotation runs | none |
| Subnet Calculator | yes | opened only | none |
| mDNS Browser | yes | opened only | none |
| Speed Test | yes | opened only (not run, one-run limit not used) | none |
| Wake-on-LAN | yes | save, re-save, rename, delete made-up device | none |
| Network Info | yes | opened only | none |
| MAC Lookup | yes | 4+1 notations, Look up, keyboard | none |

Logcat: `AndroidRuntime:E` count 0 and no E/W lines from the app package during the smoke pass. No ANR. Logcat was cleared at the start of the smoke pass.

## NEW issues (V-prefixed; V-01 to V-08 already name other findings in VERIFY-1 and VERIFY-2)

| ID | Type | Severity | Title | Repro and evidence | Suggested fix |
|---|---|---|---|---|---|
| V-09 | UX | low | Ping: every invalid host shows "Hostname or IP cannot contain spaces" | Enter "10.109.208.25010.109.208.200" (no spaces): the supporting text is the spaces message. Traceroute shows a specific message for a bad IPv4 ("An IPv4 address needs four numbers from 0 to 255", `100-tr-1111.png`). Cause: `PingScreen.kt` line ~449 uses `R.string.error_invalid_host` for any invalid host. `24-ping-garbled.png` | Use `HostValidator.problemOf(host)?.messageRes()`, as TLS Inspector and Topology already do. |
| V-10 | BUG | low | Ping: an invalid host saved before the fix is still a Recent chip | The chip "10.109.208.25010.109.208.200" from the VERIFY-1 run is still in Recents. WP19 stops new saves but nothing removes existing ones. `25-ping-recents-scrolled.png` | Filter Recents through HostValidator when loading, or run a one-off cleanup. Add a test. |
| V-11 | UX | low | DNS: after a config change the IME reopens on the DNS server field, not the domain field | After rotation with the IME closed, the IME reopened and the cursor was on "System DNS" (`16-dns-rot-portrait-noime.png`). The domain field lost focus. Look up stayed visible, so the action is not hidden. | Keep focus on the field the user was in, or do not restore focus after recreation. Low priority. |
| V-12 | UX | low | Network Topology: large empty gap between Discover and the result card | Portrait, after a failed run: about 430 px of empty space between the Discover button and the Discovery Failed card (`32-topo-return.png`). | Place the result or an empty-state hint directly under the button. |
| V-13 | BUG (intermittent) | medium | HTTP Probe: one of 14 Cancel attempts ended "The network request timed out" with no cancel effect | Portrait, Send to https://httpbin.org/delay/10, Cancel 2 s later: state "The network request timed out." (`70-http-cancel-a1.png`). Not reproduced in 11 further runs that ended "Request canceled". This is the same symptom as VERIFY-2 V-04 and the WP20 fix did not remove it on the device. | Add logging on the cancel path. Repeat the repro from a clean app start several times. Keep the WP20 test. |

## Notes for the orchestrator

- The WP19 claims that failed on device: Wi-Fi Clear (V-07), MAC Lookup keyboard (VERIFY-2 V-01). Both still FAIL. The ToolClearButton fix is not effective on the Wi-Fi field; the MAC Look up path does not hide the keyboard.
- The WP20 device-check wording for TLS port 80 ("does not appear to speak TLS") differs from the shipped text. Update the WP21 expectation.
- Ping recents still hold the invalid value from before the fix (V-10).
- Test-harness note (not an app issue): adb `input text` appends. Two early steps were mine: a recents chip tap filled the Traceroute field, and then my typed text was appended ("1.1.1.11.1.1.1"). The app rejected it correctly.

## What worked well

- Home hint no longer overlaps the grid.
- NXDOMAIN is a neutral card with a "Domain not found" title and a clear sentence.
- Topology keeps its last state and seed on return.
- MAC Lookup merges duplicate notations with a clear count; broadcast shows "Not applicable".
- WHOIS RDAP works for IPs and ASNs (no 403); Auto mode names the protocol that answered.
- TLS gives plain-language reasons for a plaintext port and for a missing trust root.
- WoL re-save gives specific notes for keep-name and rename.
- Traceroute stops at the destination and reports unreachable LAN addresses in plain language.
- Traceroute rejects bad IPv4 input with a specific message.
- DNS and Port Scanner action buttons stay visible with the keyboard open after rotation, dark mode and font 1.3.
