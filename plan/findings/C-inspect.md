# QA findings – Group C-inspect

Tools: TLS Inspector, WHOIS Lookup, HTTP Probe, Subnet Calculator
Device: Android 8.0 tablet, serial U8BBB20806201256, 1200x1920 at 320 dpi, Wi-Fi with internet
Build: net.aieat.netswissknife.debug 1.0.0-debug (installed from main)
Screenshots and UI dumps: /tmp/claude-1000/-home-dev-android-network-tools/6c563765-8559-49b8-a00c-5c809e24f314/scratchpad/qaC/ (file names given per finding)
Independent checks: Python 3 `ipaddress` (subnet), Python `ssl`/`socket`/`urllib` from the host (TLS, HTTP headers, body size), raw WHOIS on port 43 of whois.verisign-grs.com and whois.iana.org, RDAP at rdap.verisign.com.

Device settings: user_rotation, font_scale and night mode were restored. accelerometer_rotation was set to 0 during the run and is now 1 (Android default, assumed original; not verified).

Severity: critical / high / medium / low. Type: BUG, UX, A11Y, PERF, FEATURE, GAP. Where a finding is not confirmed, the type reads e.g. "BUG (UNVERIFIED)".

---

## Summary

| ID | Tool | Type | Severity | Title |
|---|---|---|---|---|
| TLS-01 | TLS Inspector | BUG | high | Wrong-host certificate shows "Connection: Trusted" |
| WHOIS-01 | WHOIS Lookup | BUG | high | Domain status chips render empty |
| WHOIS-02 | WHOIS Lookup | BUG | medium | WHOIS-mode data differs from the registry record: Registered date from IANA, Updated and Expires missing |
| SUB-01 | Subnet Calculator | BUG | medium | /31 reports 0 usable hosts (RFC 3021: 2), and contradicts its own first/last host rows |
| SUB-02 | Subnet Calculator | BUG | medium | Previous result stays on screen after the input is cleared |
| HTTP-01 | HTTP Probe | BUG | medium | Overview shows Content-Type "N/A" while the Headers tab has it |
| HTTP-02 | HTTP Probe | BUG | low | Timing breakdown phases sum to 381 ms, above the 334 ms response time (BUG, UNVERIFIED which phase is wrong) |
| TLS-02 | TLS Inspector | UX | medium | "Trusted" status pill uses error (pink) colouring |
| TLS-03 | TLS Inspector | UX | medium | SHA-256 pin mismatch does not change the overall verdict |
| TLS-04 | TLS Inspector | BUG | medium | 1.1.1.1 shows "Untrusted / chain not trusted" while the host trust store verifies it (UNVERIFIED: may be the Android 8 trust store) |
| HTTP-03 | HTTP Probe, TLS Inspector | UX | medium | Trailing space in a typed host or URL is not trimmed; the field then errors |
| HTTP-04 | HTTP Probe | UX | medium | Keyboard stays open after Enter submits and covers the results; Send button sits under the keyboard |
| SUB-03 | Subnet Calculator | A11Y | medium | Info icons are 28 px (14 dp); binary bit cells have no content description; all field info icons share the description "Help" |
| SUB-04 | Subnet Calculator | UX | medium | Tablet layout: single column stretched to full width in landscape |
| SUB-05 | Subnet Calculator | GAP | medium | No copy or share of results |
| TLS-05 | TLS Inspector | UX | low | Plain-HTTP port (80) gives a generic "Handshake failed", no hint |
| TLS-06 | TLS Inspector | UNVERIFIED | low | self-signed.badssl.com connect time 5202 ms on one run (other hosts 16-235 ms) |
| WHOIS-03 | WHOIS Lookup | UX | low | Raw RDAP/WHOIS section opens only via its Switch; the label and chevron do not toggle |
| WHOIS-04 | WHOIS Lookup | UX | low | "999.1.1.1" gets a generic "without spaces" error |
| WHOIS-05 | WHOIS Lookup | UX | low | Query chain colours: IANA node pink while the other successful nodes are green |
| WHOIS-06 | WHOIS Lookup | UNVERIFIED | low | Auto mode for an IP or ASN shows one RIR step; protocol used is not clear |
| HTTP-05 | HTTP Probe | UX | low | Final URL shows "[path omitted]" even for a root URL |
| HTTP-06 | HTTP Probe | UX | low | DNS failure and request timeout both show the generic "Network request failed" |
| HTTP-07 | HTTP Probe | UX | low | Selected GET chip is blue while the other selected chips are green |
| SUB-06 | Subnet Calculator | UX | low | Abbreviated counts (16.78M, 4294.97M, 65.5K, 1.0K) instead of exact values |
| SUB-07 | Subnet Calculator | UX | low | "Prefix / host bits" row shows "8 / 32" (prefix / total), duplicating "Host bits" |
| SUB-08 | Subnet Calculator | UX | low | Labels: 100.64.0.0/10 (shared CGNAT) and 0.0.0.0/0 shown as "Public" |
| SUB-09 | Subnet Calculator | UX | low | Keyboard opens on screen entry (the only one of the four tools that does) and covers results; plain text keyboard |
| SUB-10 | Subnet Calculator | UX | low | Input leading icon is the Wi-Fi/ping glyph, not a subnet glyph |
| SUB-11 | Subnet Calculator | UX | low | "Invalid prefix:" with nothing after the colon for "192.168.1.0/" |
| SUB-12 | Subnet Calculator | GAP | low | IPv6 rejected (README says IPv4 only) |
| SUB-13 | Subnet Calculator | UX | low | "Network addr" label truncated in Binary Breakdown |
| SUB-14 | Subnet Calculator | UX | low | "1.2.3.4/08" silently read as /8 |
| SUB-15 | Subnet Calculator | UX | low | Plural errors: "1 usable hosts", "0 usable hosts" |
| APP-01 | Cross-tool | UX | low | "Scroll for more tools" hint covers last tile descriptions on Home; content can sit under the system nav bar |
| APP-02 | Cross-tool | UX | low | Recent chip row clips its first chip at the left edge |
| APP-03 | Cross-tool | UX | low | Tool named "Subnet Calc" in the All Tools sheet, "Subnet Calculator" on its screen and in README |

---

## TLS Inspector

### TLS-01 – Wrong-host certificate shows "Connection: Trusted" (BUG, high)
- Repro: TLS Inspector, host wrong.host.badssl.com, port 443, Inspect.
- Expected: the top verdict reflects the hostname failure ("Untrusted" or "Hostname mismatch"), consistent with the "Hostname | Mismatch" row and the HOSTNAME_MISMATCH finding.
- Actual: badge "Connection | Trusted".
- Independent check: Python default context fails with "Hostname mismatch, certificate is not valid for 'wrong.host.badssl.com'".
- Screenshot: tls_wronghost_top.png
- Fix: compute the verdict from all hard failures (hostname, expiry, untrusted chain, pin mismatch).

### TLS-02 – "Trusted" pill uses error colouring (UX, medium)
- The pill is pink with dark red text, the same tint used for errors. The "Leaf" chip beside it is green.
- Repro: any successful inspection, e.g. example.com:443. Screenshots: tls_land.png, share_pem.png
- Fix: success role for Trusted; error role only for failures.

### TLS-03 – Pin mismatch does not change the verdict (UX, medium)
- Repro: example.com:443 with pin 00112233...FF (64 hex, valid format, wrong value).
- Actual: row "Expected pin | Mismatch"; badge still "Trusted"; no finding card.
- Expected: a pin mismatch is a security failure and should be a finding and change the verdict.
- Correct pin in uppercase without colons (85CA6AB0...0DF07A) shows "Expected pin | Match". The colon form and lowercase form were not cleanly re-tested (UNVERIFIED).
- Screenshot: pin_bad_top.png

### TLS-04 – 1.1.1.1 reported "Untrusted" (BUG, UNVERIFIED, medium)
- Repro: host 1.1.1.1, port 443.
- App: Connection "Untrusted"; finding "UNTRUSTED – The certificate chain is not trusted". Hostname "Match" (IP SAN present). Chain: cloudflare-dns.com, then SSL.com SSL Intermediate CA ECC R2.
- Host Python with the default trust store verifies the chain.
- example.com is "Trusted" on the same device, so the device trust store (Android 8 without the SSL.com root or intermediate) is the likely cause. Not confirmed on the device. If it is the device store, the UI should say "not in this device's trust store", not imply a bad server.
- Screenshot: tls_ip_top.png

### TLS-05 – Plain-HTTP port gives a generic failure (UX, low)
- Repro: example.com, port 80, Inspect.
- Actual: "Inspection failed / Handshake failed / Retry". Host Python: "wrong version number".
- Fix: map plaintext responses to "Port 80 does not appear to speak TLS".
- Screenshot: tls_port80_top.png

### TLS-06 – Connect time outlier (UNVERIFIED, low)
- self-signed.badssl.com showed connect 5202 ms with handshake 390 ms on one run. Other hosts: 16-235 ms. Not reproduced; re-test before filing.
- Screenshot: tls_selfsigned_top.png

### TLS – checked and correct
- Probe older TLS versions: example.com shows TLSv1, TLSv1.1 and TLSv1.2 "Supported", TLSv1.3 "Not testable on this device". Checked with a SECLEVEL=0 Python client: the server accepts TLSv1 and TLSv1.1, so the result is right. (A default Python client refuses them locally; that would have looked like a false positive.) Screenshot probe_top.png
- example.com:443: leaf SHA-256 85:CA:6A:B0:68:E9:BC:CE:88:B6:C4:AA:3C:47:F7:D1:72:28:13:4A:45:7F:87:0D:38:00:E6:22:3A:0D:F0:7A matches the host; subject, issuer, validity (2026-09-26 to 2026-12-25) and SANs match. Negotiated TLSv1.2 on device, TLSv1.3 on the host (Android 8 has no TLS 1.3 client). Screenshot tls_example_top.png
- expired.badssl.com: "Untrusted", EXPIRED and UNTRUSTED findings; host Python "certificate has expired". Screenshot tls_expired_top.png
- self-signed.badssl.com: "Untrusted", SELF_SIGNED finding, hostname Match; host Python "self-signed certificate". Screenshot tls_selfsigned_top.png
- tls-v1-2.badssl.com:1012 (non-default port): works, "Port (default 443)" keeps 1012, Trusted, TLSv1.2. Screenshot tls_port1012_top.png
- badssl.com:8443 (timed out from host too): loading shows "Inspecting… / Cancel"; result "Inspection failed / TLS inspection timed out / Retry". Good, specific message. Screenshots tls_8443_top.png, tls_8443_done.png
- Port 99999: "Port must be a number from 1 to 65535" inline, Inspect blocked. Good.
- Host with a space: "Hostname or IP cannot contain spaces" inline. Accurate, see HTTP-03.
- Empty host: Inspect disabled, empty state shown. Good.
- Share PEM opens the Android share sheet. Screenshot share_pem.png
- Rotation to landscape after a result keeps the results. Screenshot tls_land.png (rotation during a run NOT tested)
- Dark mode and font scale: NOT tested on this screen.

### TLS – not tested or not confirmed
- Cancel during a run: the Cancel control exists in the loading state (see 8443 run), but a cancel was not exercised on a fast run.
- Trash icon in Recent: not tapped (N).
- https:// in the host field: not cleanly reproduced (the field held "hthttps://" from an earlier entry on a different screen; UNVERIFIED).
- Keyboard IME action on host field: not separately checked.

### TLS – feature gaps
- No days-remaining countdown on expiry (dates only).
- No revocation (OCSP/CRL) status.
- No cipher-suite strength summary beyond "weak key" checks.
- No export of findings (only PEM share).
- A copy action for the leaf SHA-256 fingerprint would help pinning.

---

## WHOIS Lookup

### WHOIS-01 – Domain status chips are empty (BUG, high)
- Repro: example.com, Auto, scroll to "Domain Status".
- Expected: three labelled chips (RDAP: clientDeleteProhibited, clientTransferProhibited, clientUpdateProhibited; README promises human labels such as "Transfer Locked").
- Actual: three empty rounded squares. The UI tree has no text or content description on them, though they are clickable.
- Independent check: rdap.verisign.com returns those three statuses.
- Screenshot: whois_b.png (chips around y 1218-1314), whois_chip.png
- Fix: bind the label to each status chip.

### WHOIS-02 – WHOIS mode gives different dates from the registry record (BUG, medium)
- Repro: example.com, protocol WHOIS.
- Chain: whois.iana.org (IANA) -> whois.verisign-grs.com (REGISTRY) -> whois.iana.org (REGISTRAR).
- Actual: Registered 1992-01-01; Updated "–"; Expires "–".
- Independent check: raw WHOIS from whois.verisign-grs.com: Creation Date 1995-08-14, Updated Date 2026-08-14, Registry Expiry Date 2027-08-13. The 1992-01-01 value is the IANA "created" date for the .com zone entry, not the domain registration.
- Expected: take the registry step's dates (creation, updated, expiry) and show them; do not fill the lifecycle from the IANA step.
- Screenshot: whois_mode2_top.png, whois_raw2.png
- RDAP mode, by contrast, shows 1995-08-14 / 2026-08-14 / 2027-08-13 and matches Verisign (see checked list).

### WHOIS-03 – Raw response opens only via the Switch (UX, low)
- The "Raw RDAP/WHOIS Responses" label, the chevron and the "Show raw response" row do not toggle; the Switch at the right edge (about x 1028, y 1806 on this layout) does. Once on, the raw Verisign WHOIS text is shown. Earlier assumption (section did not expand) was wrong.
- Fix: make the whole row toggle, or hide the separate "Show raw response" row, which duplicates it.
- Screenshots: whois_switch_after.png, whois_switch_scrolled.png

### WHOIS-04 – Wrong error text for invalid IP (UX, low)
- Repro: "999.1.1.1", Look up.
- Actual: "Lookup failed – Enter a valid domain, IP address, or ASN without spaces".
- Expected: "999.1.1.1 is not a valid IPv4 address".
- Screenshot: whois_bad1_top.png. "not a domain!!" gets the same message and that one is right.

### WHOIS-05 – Query chain colour (UX, low)
- In WHOIS mode, the IANA node is a pink check circle while REGISTRY and REGISTRAR nodes are green. Pink reads as failure.
- Screenshot: whois_chip.png

### WHOIS-06 – Auto mode for IP or ASN shows one RIR step (UNVERIFIED, low)
- 8.8.8.8 and AS15169 show a single step "whois.arin.net – RIR". README says Auto tries RDAP first for IP and ASN via rdap.org. The chain does not say which protocol answered.
- Screenshot: whois_ip_top.png

### WHOIS – checked and correct
- example.com in Auto: chain rdap.verisign.com RDAP 1174 ms. Registered 1995-08-14, Updated 2026-08-14, Expires 2027-08-13, name servers ELLIOTT.NS.CLOUDFLARE.COM and HERA.NS.CLOUDFLARE.COM, DNSSEC Signed, all match the host RDAP record. Screenshot whois_run2.png, whois_b.png
- 8.8.8.8: range 8.8.8.0 – 8.8.8.255, Google LLC, US (not checked against ARIN directly).
- AS15169: Google LLC, US.
- Loading state: spinner in the hero, "Querying rdap.verisign.com…" and a "Stop lookup" control. Good. Screenshot whois_run1.png
- Protocol chips update the helper text. Note: the description text also contains the word WHOIS, so select chips by label.
- Recent chips persist.

### WHOIS – not tested
- Rotation during a lookup; Dark mode and font scale; RDAP-only mode for an IP; the Domain/IP/ASN example chips.

### WHOIS – feature gaps
- No bulk lookup; no lookup history beyond Recent chips; no copy of individual fields (only share of the whole result).

---

## HTTP Probe

### HTTP-01 – Content-Type "N/A" in Overview (BUG, medium)
- Repro: https://example.com, GET, Send.
- Overview "Content-Type | N/A". Headers tab on the same response lists content-type: text/html; charset=utf-8.
- Independent check: host returns text/html; charset=utf-8.
- Screenshots: http_ex_b.png (Overview), http_headers.png (Headers)
- Fix: read Content-Type case-insensitively from the same header map as the Headers tab.

### HTTP-02 – Timing phases do not add up (BUG, UNVERIFIED which phase, low)
- example.com: DNS 59 + Connect 209 + TLS 105 + Server wait 0 + Transfer 8 = 381 ms, against response time 334 ms and TTFB 319 ms. Connect probably includes TLS, which would make the sum 276 ms; either way the breakdown does not reconcile.
- Screenshot: http_ex_b.png

### HTTP-03 – Trailing space not trimmed (UX, medium; cross-tool)
- Repro: HTTP Probe type "https://google.com " (trailing space), Send. Actual: inline "The URL could not be parsed."
- Same pattern in TLS Inspector: host "example.com " gives "Hostname or IP cannot contain spaces".
- Expected: trim before validating. Suggested-word insertion from the keyboard (SwiftKey adds a trailing space) will hit real users.
- Screenshot: http_post.png (inline error style is good)

### HTTP-04 – Keyboard and Send button (UX, medium)
- After typing a URL the keyboard covers the Send Request button (about y 1220-1260). Pressing the IME Enter key does send, but the keyboard then stays open and covers the Overview and Security results.
- Expected: submit dismisses the keyboard; results remain visible.
- Screenshot: http_security.png

### HTTP-05 – "[path omitted]" on a root URL (UX, low)
- Final URL reads "https://example.com/[path omitted]" for a request with no path. Looks like a bug. If the redaction is intended, show the host and only mark a path that exists.
- Screenshot: http_ex_b.png

### HTTP-06 – Generic network error (UX, low)
- nonexistent-host.invalid: "Request failed / Network request failed / Retry" (no DNS wording).
- Timeout (badssl.com:8443, about 25 s): same generic message, no timeout wording.
- Screenshots: http_err_httpsnonexistenthostinvalid.png, http_8443_done.png

### HTTP-07 – Selected chip colour (UX, low)
- GET selected is blue; POST selected is green (theme). Screenshots: http_idle.png (GET), http_post.png (POST).

### HTTP – checked and correct
- http://example.com is blocked: "Plain HTTP requests are disabled in this release. Use an HTTPS URL." Screenshot http_plain_top.png
- "https://" gives "The URL could not be parsed." (inline, good)
- https://google.com follows one redirect to https://www.google.com, returns 200, reports "1 redirect". Screenshot http_security.png (overview is in http_g_top.png, but that capture was taken before the request completed; use http_security.png)
- example.com body size 577 B matches the host (577 bytes).
- Body tab: raw body with Pretty JSON toggle; "Not valid JSON" for HTML. Good.
- Headers tab: request and response headers, sorted.
- Security tab for google.com: 3 pass, 3 warn, 2 fail. HSTS and X-Content-Type-Options FAIL match the host response (absent). CSP shown INFO because only Content-Security-Policy-Report-Only is present (matches). X-XSS-Protection "0" shown INFO (deprecated). Good.
- Loading state: "Sending…" spinner in the result card with a Cancel button. Good. Screenshot http_8443_loading.png
- Enter on the URL field submits.
- No FATAL exceptions in logcat during the session.

### HTTP – not tested
- POST with a body (only the body editor is seen); PUT, PATCH, DELETE, HEAD, OPTIONS.
- Custom header add and remove.
- Copy as cURL (button present; clipboard not inspected).
- Cancel during a request (Cancel control exists, not exercised); rotation during a request.
- Follow Redirects OFF.
- Dark mode and font scale on this screen.

### HTTP – feature gaps
- No re-run control beside results; no saved requests or header presets; no export of the response body to a file.

---

## Subnet Calculator

Reference: Python `ipaddress`, script ref/ref.py in qaC. Network, broadcast, first/last host, total, usable, CIDR, wildcard, hex mask, class and scope were checked for all 18 valid CIDR inputs below. Range results were checked by computing the smallest single prefix whose network contains both ends.

### Valid CIDR inputs (app vs Python)
| Input | Network / broadcast | Usable (app) | Result |
|---|---|---|---|
| 192.168.1.0/24 | .0 / .255 | 254 | match |
| 10.0.0.0/8 | 10.0.0.0 / 10.255.255.255 | 16.78M (16777214) | match, abbreviated (SUB-06) |
| 172.16.0.0/12 | 172.16.0.0 / 172.31.255.255 | 1.05M (1048574) | match, abbreviated |
| 192.168.1.1/30 | not on boundary; 192.168.1.0/30, bcast .3 | 2 | match, warning correct |
| 10.0.0.0/31 | .0 / .1 | 0 | BUG (SUB-01) |
| 10.0.0.5/32 | single host | 1 | match, "1 usable hosts" plural (SUB-15) |
| 10.1.2.3 (bare) | /32 | 1 | match |
| 192.168.1.0/255.255.255.0 | match | 254 | match, dotted mask accepted |
| 192.168.1.0 255.255.255.0 | match | 254 | match, space form accepted |
| 0.0.0.0/0 | 0.0.0.0 / 255.255.255.255 | 4294.97M (4294967294) | match, abbreviated; hex 0x00000000 correct; scope label wrong (SUB-08) |
| 8.8.8.8/16 | not on boundary; 8.8.0.0/16, bcast 8.8.255.255 | 65.5K (65534) | match, Public correct (global) |
| 169.254.1.1/16 | 169.254.0.0/16 | 65.5K | match, Private (link-local) |
| 127.0.0.1/8 | 127.0.0.0/8 | 16.78M | match, Private |
| 224.0.0.1/4 | 224.0.0.0/4, bcast 239.255.255.255 | 268.44M | match, class D, Public |
| 240.0.0.0/4 | 240.0.0.0/4 | 268.44M | match, class E (reserved, labelled Public; app rules) |
| 255.255.255.255/32 | single host | 1 | match, class E |
| 192.168.1.0/23 | not on boundary; 192.168.0.0/23, bcast 192.168.1.255 | 510 | match |
| 100.64.0.0/10 | 100.64.0.0 / 100.127.255.255 | 4.19M (4194302) | match; labelled Public but is RFC 6598 shared space (SUB-08) |

### IP Range (app vs Python)
| Range | App | Expected | Result |
|---|---|---|---|
| 10.0.0.1 – 10.0.0.10 | 10.0.0.0/28, usable 14 | 10.0.0.0/28 | match |
| 192.168.1.0 – 192.168.1.255 | 192.168.1.0/24 | same | match |
| 10.0.0.5 – 10.0.0.5 | 10.0.0.5/32 | same | match |
| 0.0.0.0 – 255.255.255.255 | 0.0.0.0/0 | same | match |
| 192.168.1.10 – 192.168.2.20 | 192.168.0.0/22 | 192.168.0.0/22 | match, usable 1.0K (1022) |
| 1.1.1.1 – 1.1.1.2 | 1.1.1.0/30 | 1.1.1.0/30 | match |
| 10.0.0.10 – 10.0.0.1 | "Minimum IP must be ≤ maximum IP" | error | match |
| abc – 1.1.1.1 | "Invalid IP address: abc" | error | match |

### Invalid CIDR inputs
- 256.1.1.1/24: "Octet out of range in: 256.1.1.1" (good)
- 192.168.1.0/33: "Prefix length must be 0–32, got 33" (good)
- 192.168.1.0/: "Invalid prefix:" with nothing after (SUB-11)
- abc: "Invalid IP address: abc" (good)
- 1.2.3: "Invalid IP address: 1.2.3" (good)
- 1.2.3.4/08: accepted as /8 without notice (SUB-14)
- 10.0.0.0/-1: "Prefix length must be 0–32, got -1" (good)
- 2001:db8::/32: "Invalid IP address: 2001:db8::" (SUB-12)
- 192.168.1.0/255.0.255.0 and /255.255.255.1: "Non-contiguous subnet mask" (good)
- Spaces around the slash (10.0.0.0 /8): accepted, correct result.
- Screenshots: ibad2_top.png, ibad10_top.png, ibad3_top.png, ibad9_top.png, ibad6_top.png, c31_top.png

### SUB-01 – /31 reports 0 usable hosts (BUG, medium)
- Repro: 10.0.0.0/31.
- Actual: hero "0 usable hosts"; Usable hosts 0; Total 2; First host 10.0.0.0; Last host 10.0.0.1. The rows contradict each other.
- Expected: RFC 3021 point-to-point; both addresses usable (2). At minimum the rows must agree.
- Source: core-network/.../subnet/SubnetModels.kt line 33 documents "0 for /31" on purpose, which conflicts with RFC 3021 and with the first/last rows.
- Screenshot: c31_top.png
- Fix: usableHosts = 2 for /31, or hide first/last and say "point-to-point". Same for IP Range results whose tightest subnet is /31.

### SUB-02 – Stale result after clearing the input (BUG, medium)
- Repro: calculate 192.168.1.0/24, then clear the field (I used delete keys; the clear X works too).
- Expected: result cleared, or marked stale, when the input is empty.
- Actual: the 192.168.1.0/24 card stays beside an empty field with Calculate disabled.
- Screenshot: empty2.png

### SUB-03 – Accessibility of info icons and binary grid (A11Y, medium)
- The info icons next to Network address, Broadcast, First host, Last host and Usable hosts are 28 px (14 dp at 2x). The dump does not show whether a larger clickable parent exists, so the touch area is UNVERIFIED.
- Binary Breakdown bit cells are separate text nodes "1", "0", "." with no content description; TalkBack reads digits with no context.
- All field info icons have the content description "Help" (not specific).
- Expected: 48 dp touch area; specific descriptions ("Explain Network address"); one description for each binary row.
- Note: the Clear (X) button's clickable area is 48 dp; OK.
- Screenshot: subnet_24_nokb.png (UI dump lists the grid cells)

### SUB-04 – Tablet layout (UX, medium)
- Landscape 1920x1200: one column stretched to the full width (about 1580 px). Fields, Calculate and chips stretch; result labels sit far from values. No master/detail use of the width.
- Suggested: left pane with input and quick chips, right pane with results, binary and notation.
- Screenshot: land1.png

### SUB-05 – No copy or share of results (GAP, medium)
- Long-press and tap on a value give no feedback and no copy. There is no share or export of the summary. The other tools have copy or share (HTTP cURL, TLS PEM, WHOIS share).
- Suggested: long-press to copy any value; share the full summary; copy for the notation card.
- Screenshot: lp_copy.png

### SUB-06 – Abbreviated counts (UX, low)
- Total addresses and usable hosts read "16.78M", "1.05M", "4294.97M", "65.5K", "1.0K". Engineers want exact values (16777216 / 16777214). The hero repeats the count.
- Screenshot: dark_font2.png (abbreviated), subnet_24.png (exact 256 / 254)

### SUB-07 – Mislabelled bits row (UX, low)
- "Prefix / host bits" shows "8 / 32" (prefix length over address length). The label says host bits, and the row below, "Host bits 24 bits", is the real host count.
- Screenshot: sub_10_8_scroll / help_netaddr.png (bottom of result, see c10_8_bot.png)

### SUB-08 – Scope labels (UX, low)
- 100.64.0.0/10 (RFC 6598 shared address space) is labelled "Public". Suggested: "Shared (CGNAT)".
- 0.0.0.0/0 is labelled "Public". Python: not global, not private. Suggested: "Unspecified" or no badge.
- Screenshot: cgnat_bot.png

### SUB-09 – Keyboard on entry (UX, low)
- The calculator field takes focus on screen entry, so the keyboard covers the result area immediately. TLS, WHOIS and HTTP idle screens show no keyboard (tls_idle.png, whois_idle.png, http_idle.png), so this is inconsistent.
- Plain text keyboard; a numeric/dot keyboard would help IP entry. IME Enter does calculate (good).
- Screenshot: subnet_idle.png

### SUB-10 – Icon (UX, low)
- The leading icon is the Wi-Fi/ping glyph used by the Ping tool. Use a subnet/mask glyph.
- Screenshot: subnet_idle.png

### SUB-11 – Empty prefix error (UX, low)
- "192.168.1.0/" shows "Invalid prefix:" with nothing after it. Suggested: "Enter a prefix length after /, for example /24".

### SUB-12 – No IPv6 (GAP, low)
- 2001:db8::/32 rejected. README says IPv4 only, so this is scope, not a regression.

### SUB-13 – Label truncation (UX, low)
- Binary Breakdown row label reads "Network addr" (full label is "Network address").

### SUB-14 – Leading zero accepted (UX, low)
- "1.2.3.4/08" is read as /8 with no notice. Decimal reading is probably right; a note would prevent octal confusion.

### SUB-15 – Plurals (UX, low)
- "1 usable hosts" (10.0.0.5/32, 10.1.2.3) and "0 usable hosts" (/31). Use singular forms.
- Screenshot: cbare_top.png

### Subnet – checked and correct (summary)
- Misaligned-network warning with the actual network address and an Azure hint.
- Binary breakdown colours (network bits vs host bits) match the spec.
- Notation card (CIDR, dotted, wildcard, hex, binary) correct for every valid case.
- Dark mode and font scale 1.3 render without clipping in the upper section (dark_font2.png).
- Landscape keeps the result and input (land1.png).
- Help sheet content is clear and accurate (help1.png).
- Back from the root of the calculator returns to Home (one observation, kbcheck.png).

### Subnet – not tested
- TalkBack spoken output (UI tree only); calculations are instantaneous, so cancel is N/A.

### Subnet – feature gaps (product owner)
- VLSM / subnet split (divide a block into N subnets of a given size).
- Supernet / summarise a list of networks.
- "Is this IP in this subnet?" check.
- Host enumeration or a host list for small subnets.
- IPv6.
- Copy and share (SUB-05).

---

## Cross-tool observations

### APP-01 – Home hint overlaps tile text; content under nav bar (UX, low)
- On Home, the "Scroll for more tools" hint sits on top of the WHOIS and HTTP Probe tile descriptions. Screenshot kbcheck.png
- On dark subnet results the bottom Binary Breakdown row sits under the system nav bar. Screenshot dark_font2.png. Add navigation-bar padding to scroll containers.

### APP-02 – Recent chip row clips its first chip (UX, low)
- The Recent row is a horizontal scroller; its first chip is cut at the left edge ("ned.badssl.com" in pin_focus.png, ".com" in whois_raw2.png) with no fade or overflow hint.

### APP-03 – Tool name mismatch (UX, low)
- More > All Tools lists "Subnet Calc"; the screen title, the hero and README say "Subnet Calculator".

---

## Tested checklist

Legend: [Y] exercised and checked; [P] partly; [N] not tested; [U] unverified.

### TLS Inspector
- [Y] Idle state
- [Y] example.com:443 (checked against host)
- [Y] expired.badssl.com
- [Y] self-signed.badssl.com
- [Y] wrong.host.badssl.com
- [Y] Non-default port 1012
- [Y] Plain-HTTP port 80
- [Y] badssl.com:8443 (timeout; loading state with Cancel visible)
- [Y] Port 99999 (validation)
- [Y] Host with space (validation)
- [Y] IP target 1.1.1.1
- [Y] Pin match and mismatch
- [Y] Probe older TLS versions toggle
- [Y] Share PEM
- [Y] Recent chips (add, show)
- [P] Host clear X: used indirectly
- [P] Rotation after a result (not during a run)
- [N] Dark mode and font scale
- [N] Trash icon in Recent
- [N] Cancel during a run (fast runs)
- [U] https:// in host field
- [N] IME Enter on host field
- [N] TalkBack labels (not inspected)

### WHOIS Lookup
- [Y] Idle state
- [Y] example.com Auto (checked against RDAP)
- [Y] example.com WHOIS mode (checked against raw WHOIS: date mismatch, WHOIS-02)
- [Y] 8.8.8.8 Auto
- [Y] AS15169 Auto
- [Y] 999.1.1.1 (message)
- [Y] "not a domain!!" (message)
- [Y] Protocol chips
- [Y] Recent chips
- [Y] Domain status chips (defect)
- [Y] Raw response Switch: toggles and shows raw text (WHOIS-03)
- [Y] Loading state with Stop lookup
- [Y] Share icon present (not exercised)
- [N] RDAP-only mode
- [N] Rotation during a lookup
- [N] Dark mode and font scale

### HTTP Probe
- [Y] Idle state
- [Y] https://example.com GET (body size and headers checked against host)
- [Y] https://google.com with 1 redirect
- [Y] http:// blocked
- [Y] https:// alone and malformed URL (inline error)
- [Y] Unresolvable host
- [Y] badssl.com:8443 timeout (loading with Cancel visible)
- [Y] Method chips (GET, POST selected)
- [Y] Overview, Headers, Body, Security tabs
- [Y] Security grading against host headers
- [Y] Recent chips
- [Y] IME Enter submit
- [Y] Follow Redirects toggle present (ON)
- [P] Custom headers section (present, not exercised)
- [N] POST with body (send)
- [N] PUT, PATCH, DELETE, HEAD, OPTIONS
- [N] Copy as cURL (clipboard not inspected)
- [N] Cancel during a request; rotation during a request
- [N] Follow Redirects OFF
- [N] Dark mode and font scale

### Subnet Calculator
- [Y] Idle state; all six example chips present; CIDR and IP Range tabs
- [Y] 18 valid CIDR inputs checked against Python
- [Y] Dotted mask and space-separated mask
- [Y] Bare IP (/32)
- [Y] Misaligned-network warning
- [Y] 12 invalid CIDR inputs
- [Y] Empty input (Calculate disabled, stale result – SUB-02)
- [Y] 6 IP Range valid cases checked; 2 invalid
- [Y] Quick chip (172.16.0.0/12)
- [Y] Help sheet
- [Y] Field info icons tapped: no visible explanation (no tooltip seen, short and long press)
- [Y] Clear X visible
- [Y] IME Enter calculates
- [Y] Landscape with result shown
- [Y] Dark mode and font scale 1.3
- [Y] Back from root to Home
- [Y] Long-press or tap on a value: no copy feedback
- [N] TalkBack spoken output

---

## Notes
- Device left in portrait, light theme, font scale 1.0, user rotation 0. accelerometer_rotation was 1 at start (assumed) and was set to 0 during the run and restored to 1.
- Network targets used: badssl.com test hosts from the brief, example.com, google.com, 1.1.1.1, the RDAP/IANA/Verisign/ARIN servers the app itself contacts. No LAN scan, no speed test.
- Harness note: scripted typing was flaky (IME drops keys, stray text left in fields). Results were only reported when the field value was checked in the UI tree first.
