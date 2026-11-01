# Device verification: GROUP 4, WP22 items (round 3)

Device: U8BBB20806201256 (Android 8.0, 1200x1920, density 2). `net.aieat.netswissknife.debug` built from HEAD 1d1312c (WP22, branch `plan/ux-remediation-2026-10-31`), installed 2026-11-01 10:07.
Evidence folder: `/tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/verD` (file names below are relative to that folder). Helper scripts are in `verD/bin`.
IME state read with `adb shell dumpsys input_method | grep mInputShown`.
Status legend: PASS, FAIL, PARTIAL, NOT-REPRODUCIBLE, UNVERIFIED. Source not edited. No `am kill-all`. Settings restored at the end: accelerometer_rotation 1, user_rotation 0, font_scale 1.0, night off (verified).

## Verification table (WP22 items)

| Finding | Result | Evidence |
|---|---|---|
| (a) VERIFY-3 V-07 Wi-Fi Scanner: Clear keeps keyboard closed | FAIL (3 of 3) | Typed "Net1/Net2/Net3", tapped Clear (1120,657). After every Clear: `mInputShown=true`, the search field kept focus and the keyboard covered the results. `wifi-clear-1.png`, `wifi-clear-2.png`, `wifi-clear-3.png` (the Currently Connected card is under the keyboard). WP22 said the fix was not device-verified, and it does not hold. |
| (b) VERIFY-3 V-01 MAC Lookup: Look up hides keyboard | FAIL (3 of 3) | Typed a MAC with the keyboard open, tapped Look up (944,524 with IME open): `mInputShown=true` after each tap, the field kept focus, and the result card was under the keyboard (`mac-a1.png`, `mac-a2.png`, `mac-a3.png`). Inputs: 00:1A:2B:3C:4D:5E, F0:9F:C2:11:22:33, 00-50-56-C0-00-01. |
| (b) MAC Lookup: keyboard Search action hides keyboard and shows results | PASS (3 of 3) | Same three MACs, pressed the Search key on the IME (1108,1776). `mInputShown=false` after each, and the vendor card and notations are visible (`mac-s1.png`, `mac-s2.png`, `mac-s3.png`). Note: the hardware Enter key (keyevent 66) inserts a newline in this field and does not trigger the action, so the IME key was used. |
| (b) MAC Lookup: Paste from clipboard hides keyboard | FAIL (3 of 3) | Copied a MAC from the field with select all and Copy, then with the keyboard open tapped Paste from clipboard. The value was pasted, but `mInputShown=true` after each paste (`paste-p1.png`, `paste-p2.png`, `paste-p3.png`). |
| (b) MAC Lookup: recent select hides keyboard | FAIL (3 of 3) | With the keyboard open, tapped the Recent chip F0:9F:C2:11:22:33 (828,640). The value was filled and `mInputShown=true` after each tap (`recent-r1.png`, `recent-r2.png`, `recent-r3.png`). Only the keyboard-open case was tested. |
| (c) Ping: 10.109.208.25010.109.208.200 shows a specific message | PASS | Enter: "An IPv4 address needs four numbers from 0 to 255". Start Ping disabled, no run, no chip. `ping-g1.png` |
| (c) Ping: 999.1.1.1 shows a specific message | PASS | Enter: "An IPv4 address needs four numbers from 0 to 255" (range message). No chip. `ping-g2.png` |
| (c) Ping: "a b" shows a specific message | PASS | Enter: "Hostname or IP cannot contain spaces". No chip. `ping-g3.png` |
| (c) Invalid Ping hosts are not saved to Recents | PASS | After the three invalid entries the Ping Recent row held only 10.109.208.200, 1.1.1.1 and 10.109.208.250. |
| (c) Old garbled chip gone from Ping Recents | PASS | No "10.109.208.25010..." chip (VERIFY-3 chip absent). |
| (c) Old garbled chip gone from Traceroute Recents | PASS | Recents: 10.109.208.250, 1.1.1.1. `traceroute-recents.png` |
| (c) Old garbled chip gone from Port Scanner Recents | PASS | Recents: 1.1.1.1, 10.109.208.1. `ports-recents.png` |
| (c) Old garbled chip gone from TLS Inspector Recents | PASS | Recents: 1.1.1.1 only. `tls-recents.png` |
| (c) Old garbled chip gone from Topology | UNVERIFIED | The seed field shows no Recent chips at all (before and after a failed run against 10.109.208.1). There is nothing to check, and it is not known whether Topology saves seeds to Recents. `topo-focus.png` |
| (d) DNS: IME closed, rotate to landscape, then portrait | FAIL | IME was closed (`mInputShown=false`). After the rotation the IME was open again (`mInputShown=true`, `mShowExplicitlyRequested=true`) and the Domain field had focus (portrait too). Look up was visible. `dns-land-noime.png`, `dns-port-after-rot1.png` |
| (d) DNS: IME closed, dark mode on | FAIL | IME closed with Back, then `cmd uimode night yes`: IME reopened, Domain field focused. `dns-dark-after.png` |
| (d) DNS: IME closed, font 1.3 | FAIL | IME closed with Back, then font_scale 1.3: IME reopened, Domain field focused, Look up visible at (706,1092). `dns-font13-after.png` |
| (d) DNS: IME open, rotate, Look up visible | PASS | IME stayed open after rotation to landscape and back to portrait. Look up visible above the keyboard in landscape (label readable at the keyboard edge) and in portrait. `dns-open-rot-land.png`, `dns-open-rot-port.png` |
| (d) Port Scanner: IME closed, rotate | FAIL | Closed with Back, rotated: IME reopened, field focused, Scan Ports visible. `ports-closed-land.png`, `ports-closed-port2.png` |
| (d) Port Scanner: IME closed, dark mode / font 1.3 | FAIL | Both reopened the IME with the field focused. `ports-dark-after.png`, `ports-font13-after.png` |
| (d) Port Scanner: IME open, rotate, Scan visible | PASS | IME stayed open, Scan Ports visible in landscape and portrait. `ports-open-land.png`, `ports-open-port.png` |
| (d) Port Scanner: IME on screen entry | UNVERIFIED (new, see V-14) | On opening Port Scanner from the rail the IME was already shown (`mInputShown=true`) before any touch. |
| (e) Topology: card directly under Discover | PASS | After a failed discovery ("Discovery Failed", SNMP timeout), the card starts about 40 px below the Discover button (was about 430 px). `topo-result1.png` |
| (f) HTTP Probe Cancel: 19 controlled attempts | PASS (19 of 19 "Request canceled") | See the attempt table below. |
| (f) HTTP Probe Cancel: uncontrolled warm-up attempt W0 | FAIL (1 of 20 total) | W0 ended "Request failed" and "The network request timed out." The Cancel tap at (912,1240) did not change the state (Sending... and Cancel still shown 4 s later). Send and Cancel were in different commands, so the gap is not recorded. `w1.png` (final state), `logcat-attempt0.txt` (last 80 lines before the next run; no app-level error lines). Same symptom as V-13. |
| (g) MAC Lookup dedupe | PASS | 00:1A:2B:3C:4D:5E, 00-1A-2B-3C-4D-5E, 001A.2B3C.4D5E, 001a2b3c4d5e: "Entered 4 times, merged into one card", summary "2 entries". `reg-mac-4.png`, `open_MAC_Lookup_.png` |
| (g) MAC Lookup broadcast FF:FF:FF:FF:FF:FF | PASS | "Not applicable: broadcast address", Type Broadcast (all devices), Locally administered "Not applicable". `reg-mac-4.png` |
| (g) Open all 16 tools, no crash | PASS | Each tool was opened from the grid or rail with its title or content visible (`open_*.png`). Speed Test was opened only. `FATAL EXCEPTION` count 0 in logcat after the run. Only uiautomator and system lines matched `AndroidRuntime`. |
| (g) Logcat clean | PASS | No FATAL EXCEPTION, no ANR, no app E/W lines in the runs. |

## HTTP Probe Cancel attempt table (https://httpbin.org/delay/10, portrait unless noted)

Send tap (706,1240). Cancel tap (912,1240, the Cancel label on the right half of the row while Sending). The "send_to_cancel" time includes the adb call overhead, so the real delay is slightly longer than the nominal one.

| # | Nominal delay | Pre-step | send_to_cancel | Final state | Evidence |
|---|---|---|---|---|---|
| W0 | not controlled | none | not controlled | "Request failed" + "The network request timed out." (FAIL) | `w1.png`, `logcat-attempt0.txt` |
| A1 | 0.3 s | none (uiautomator dump before Cancel) | 2477 ms | Request canceled | `http_A1.png` |
| A2 | 1 s | none (dump before Cancel) | 3120 ms | Request canceled | `http_A2.png` |
| A3 | 2 s | none (dump before Cancel) | 4166 ms | Request canceled | `http_A3.png` |
| B1 | 0.3 s | none | 1717 ms | Request canceled | `http_B1.png` |
| B2 | 4 s | none | 5414 ms | Request canceled | `http_B2.png` |
| B3 | 1 s | none | 2358 ms | Request canceled | `http_B3.png` |
| B4 | 2 s | none | 3427 ms | Request canceled | `http_B4.png` |
| B5 | 0 s (immediately after Send) | none | 1428 ms | Request canceled | `http_B5.png` |
| B6 | 1 s | none | 2364 ms | Request canceled | `http_B6.png` |
| B7 | 0.3 s | none | 1708 ms | Request canceled | `http_B7.png` |
| C1 | 1 s | rotated to landscape and back to portrait, 3 s wait | 2397 ms | Request canceled | `http_C1.png` |
| C2 | 0 s | same as C1 | 1475 ms | Request canceled | `http_C2.png` |
| C3 | 2 s | same as C1 | 3400 ms | Request canceled | `http_C3.png` |
| C4 | 0.3 s | same as C1 | 1759 ms | Request canceled | `http_C4.png` |
| D1 | 1 s | URL field tapped, IME closed with Back, then Send | 2371 ms | Request canceled | `http_D1.png` |
| D2 | 2 s | same as D1 | 3388 ms | Request canceled | `http_D2.png` |
| E1 | 4 s | none | 5440 ms | Request canceled | `http_E1.png` |
| E2 | 3 s | rotated and back, 3 s wait | 4406 ms | Request canceled | `http_E2.png` |
| E3 | 0.5 s | none | 1913 ms | Request canceled | `http_E3.png` |

Summary: 19 of 19 controlled attempts ended "Request canceled". Only W0, the uncontrolled attempt, did not. The W0 failure is still open: it reproduces V-13 on the device, and it was not reproduced under controlled timing. The rotation-only landscape-then-Cancel case from VERIFY-2 was not repeated here.

## NEW issues (V-prefixed; V-01 to V-13 are in VERIFY-1 to VERIFY-3)

| ID | Type | Severity | Title | Repro and evidence | Suggested fix |
|---|---|---|---|---|---|
| V-14 | UX | medium | Port Scanner opens with the keyboard already shown | Tap the Ports rail item. Before any touch `mInputShown=true` and the Port field has focus, so the keyboard covers the Scan Ports button on entry in portrait (`ports-closed-portrait.png` shows the state after Back). | Do not request focus for the port field on entry. Let the user tap the field. |
| V-15 | UX | low | DNS landscape: scrolled content runs under the status bar | In landscape with the keyboard open, the record-type chips scroll under the clock and battery icons (`dns-open-rot-land.png`). | Apply status bar insets to the scroll container, or use a top-inset padding in landscape. |
| V-16 | BUG (intermittent) | medium | HTTP Probe: Cancel did not take effect in one attempt | Send on https://httpbin.org/delay/10, then Cancel at (912,1240). 4 s later the state still read "Sending..." with Cancel. The request ended "Request failed" + "The network request timed out.". Not reproduced in 19 controlled attempts (see table). Same symptom as V-13. | Add logging on the Cancel tap and on the timeout branch, then repeat until the failure appears with timing and logcat. |

## Notes for the orchestrator

- WP22 claimed fixes for V-07 (Wi-Fi Clear), VERIFY-2 V-01 and VERIFY-3 V-01 (MAC Look up keyboard), and V-11 (IME after restore). On the device these are FAIL. V-07 and V-01 still fail on all tries. The MAC Search key path is the only keyboard path that works.
- V-11 (restore reopens IME) is FAIL for DNS and Port Scanner after rotation, dark mode and font change, when the IME was closed. The IME reopens with the Domain field (DNS) or the port field (Port Scanner) focused. When the IME was open, rotation is fine.
- Ping error messages (V-09) and invalid recents (V-10) pass on the device.
- Topology layout (V-12) passes on the device.
- Settings restored (verified): accelerometer_rotation 1, user_rotation 0, font_scale 1.0, night off.

## What worked well

- Ping gives a specific message for an IPv4 out-of-range value, a concatenated IP and a host with spaces.
- Invalid Ping values are not saved to Recents, and the old garbled chip is gone from Ping, Traceroute, Port Scanner and TLS Inspector.
- MAC Lookup: the keyboard Search key hides the keyboard and shows the results (3 of 3).
- MAC Lookup dedupe and broadcast "Not applicable" are correct.
- HTTP Probe Cancel shows "Request canceled" in 19 of 19 controlled attempts across 0 s to 5 s delays, including right after rotation.
- Topology's failure card sits right under Discover.
- With the keyboard open, rotation keeps the keyboard and the DNS and Port Scanner action buttons visible.

## WP24 disposition (2026-11-01)

Build: the WP24 commit on `plan/ux-remediation-2026-10-31`, installed on U8BBB20806201256. Full results and the device check table are in `plan/plan.yaml` (WP24, WP22 `closed_as_unproven`).

| Item | Disposition |
|---|---|
| V-01 / V-07 (MAC Look up, Paste, recent select; Wi-Fi Clear keyboard) | Fixed by the focus sink plus the shared dismissal. Re-run on the final APK: MAC 9 of 9, Wi-Fi Clear 3 of 3 with the IME closed. |
| V-11 (IME reopens after rotation, dark mode or font change with the IME closed) | Fixed. DNS, Ports, Subnet, Ping, Wi-Fi and MAC all stay closed. The guard that was meant for this was removed as redundant. |
| V-14 (Port Scanner shows the keyboard on entry) | Not reproduced on the final APK (opened from the rail, IME closed, no focused field). |
| V-15 (DNS landscape content under the status bar) | Fixed app-wide: the status-bar inset is reserved in `MainActivity`. Verified on DNS landscape. |
| V-16 (HTTP Probe Cancel, one timeout in the uncontrolled attempt W0) | Closed as unproven. 19 of 19 controlled attempts passed; see WP22 `closed_as_unproven`. |

Behaviour change to note: with the IME open, rotation now closes the IME and no field keeps focus (WP24 deferred). The primary action stays visible, which is the check that was required.
