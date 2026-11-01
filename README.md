# Net Swiss Knife – Android Networking Utilities

<a href="https://play.google.com/store/apps/details?id=net.aieat.netswissknife">
  <img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
       alt="Get it on Google Play"
       height="80">
</a>

**Net Swiss Knife** is an Android "Swiss army knife" app for network diagnostics and utilities. It provides a collection of networking tools in a clean, modern Jetpack Compose + Material 3 UI.

---

## Features

### Ping
ICMP echo and reachability round-trip latency measurement with real-time streaming results. The app resolves a hostname once per session, then uses icmpenguin ICMP echo with transparent reachability fallback when the socket is unavailable.
- Configurable probe count (1–100 via slider; default configurable in Settings) and timeout (100–30,000 ms)
- Advanced payload size (0–1,472 bytes), TTL (1–255), and interval (100–10,000 ms) controls, with a Reset to defaults action
- Rotating the device keeps a run going. A run stopped early (Stop, or the app going to the background) is labelled as stopped early, and its statistics cover only the probes that were sent
- When the host field changes after a run, the previous result is labelled as the previous result for its own host
- Per-probe RTT reporting with sequence numbers and status (SUCCESS / TIMEOUT / UNREACHABLE / ERROR), including reply IP, TTL, and bytes when available
- Live stats panel during active ping: packet loss %, min, avg, and max RTT updating after every packet
- RTT chart with Y-axis ms labels and fill gradient, rendered as results arrive
- **Continuous mode** — toggle replaces the count slider; pings indefinitely while the app is on screen, screen kept on automatically, stops when backgrounded or screen locked
  - Rolling window of the last 100 packets drives live stats and chart
  - Full session log streamed to a temp CSV file; shareable via the Share button on completion
- Tools with recent-target support save up to five entries on-device and show quick-select chips; clear recent targets from Settings
- Only a valid host is saved to recent targets. A dotted name whose last label is all digits (for example two addresses typed together, `10.109.208.25010.109.208.200`) is rejected as an invalid address and is never run
- CSV exports retain the original columns and append reply TTL and payload bytes.

### Traceroute
Network path analysis with per-hop geolocation enrichment.
- Configurable max hops (1–64), timeout (500–30,000 ms), and probes per hop (1–5)
- Dual protocol support: ICMP and UDP
- Automatic MTU discovery or custom packet size (28–1,472 bytes)
- Hops show IP address, per-probe RTTs, and min/average/max when multiple probes are requested; reverse DNS and geolocation fill in asynchronously when available
- Hop Details and Raw Output list hops in hop-number order; a timed-out hop is shown as `* * *` in both
- Host validation names the problem (spaces, IPv4 value out of range, empty or invalid hostname label, IPv6, length)
- Journey Intelligence labels GeoIP locations as approximate. When a hop's location cannot be reached within the measured RTT at about 200 km per ms (light in fibre), the distance, route, and "Speed vs light" figures are hidden and the card says why
- Backgrounding the app stops a running trace and keeps the hops found so far; a screen rotation does not stop it
- The trace stops at the first hop whose reply comes from the destination; probes already in flight for later hops are dropped
- When two or more consecutive replies all come from this device's own addresses, the trace stops early and reports "Host unreachable from this network (no route / destination host unreachable)" with Reached: No. A destination on the local subnet that does not answer is typically reported this way
- Editing the host field clears a previous "Trace failed" message
- The host and settings survive rotation and process death; leaving the tool with Back clears them

### Port Scanner
TCP port reachability scanning with service identification.
- 7 preset port groups: Common Services, Well-Known (1–1024), Web, Databases, Mail, Remote Access, and Custom range (up to 10,000 ports)
- A custom range is checked as you type: an inverted range or one over 10,000 ports shows a message and disables Scan Ports. Dragging the range slider stops at the 10,000-port cap
- Concurrent scanning (1–500 simultaneous probes), per-port timeout slider (200–10,000 ms)
- Service names come from the built-in registry; a port with no registered name shows no service name rather than a generic label
- Banner grabbing for open ports
- Invalid hosts show the specific reason (for example, a space, an out-of-range IPv4 octet or an invalid character) instead of one generic message. Editing the host clears the previous result
- A scan stopped with Stop Scan, or stopped when the app leaves the foreground, is labelled as stopped early with the number of ports scanned so far. A screen rotation does not stop a running scan
- Leaving the tool and coming back keeps the host, port range, preset, timeout, concurrency and the last finished result, but a result is shown again only for the host it scanned. After a process death the form returns and the result does not

### LAN Scanner
Local network device discovery across IPv4 subnets.
- CIDR subnet scanning (/16–/30) with automatic current-subnet detection
- Scans are capped at an estimated 10 minutes. When the detected network is too large for that at the current timeout and concurrency (for example a /16 on corporate Wi-Fi), the subnet field is pre-filled with the /24 around this device and a hint names the full network. An over-limit range entered by hand, including prefixes broader than /16, offers a one-tap scan of this device's /24 instead
- Multi-method presence detection: successful ICMP, completed TCP connections, and correlated NetBIOS NBSTAT or mDNS replies; each confirmed host shows the method(s) that found it
- Failed connects, timeouts, unreachable routes, and policy failures remain opt-in diagnostics and never inflate the confirmed device count or confirmed-host exports
- Per-host details: IP, hostname, MAC address, OUI vendor name, open ports, RTT when ICMP answers, and gateway flag from the active default route
- MAC resolution is best-effort: Android 10+ restricts the ARP source, so the UI explains when a MAC cannot be read
- Concurrent host probes (1–500) with real-time progress streaming, post-probe ARP enrichment, and final summary
- Search results by IP, hostname, or vendor; filter by gateway or hosts with open ports
- Expanded hosts include a direct **Scan ports** hand-off; the OUI registry contains 50,000+ prefixes and can be refreshed with `python3 tools/oui/update_oui.py`
- Back closes an expanded host first and keeps the results; a rotation keeps the scan running; there is no pull-to-refresh, so a swipe never discards results (use **Re-scan**)
- Leaving the tool and coming back shows the last result, subnet, timeout and concurrency again (kept while the app stays open). After a process death the subnet, timeout and concurrency return but the result does not, since results are not saved to disk
- Uncertain probe rows name the reason and port (for example "TCP probe timed out, port 80") rather than raw exception text

### DNS Lookup
Full DNS record resolution with multiple resolver options and protocol-level response details.
- 10 record types: A, AAAA, MX, TXT, CNAME, NS, SOA, PTR, SRV, CAA
- Resolver options: system default, Google (8.8.8.8), Cloudflare (1.1.1.1), OpenDNS (208.67.222.222), Quad9 (9.9.9.9), or a custom server
- PTR queries auto-reverse IPv4 addresses to `.in-addr.arpa` and IPv6 to `.ip6.arpa` form — just enter the IP
- Returns each record's actual RR type and section, RCODE, AA/AD/TC/RD/RA flags, query time, server actually used, and raw DNS response
- The domain field is checked as you type: names with spaces, empty labels or invalid characters show a plain-language message and Look up stays disabled. Names with leading underscores (SRV, TXT owner labels) are accepted
- Editing the name, record type or server clears a finished result or error so an old answer is not shown for a new query
- The name, record type and resolver survive rotation and process death; leaving the tool with Back clears them
- An NXDOMAIN answer is shown as a neutral "Domain not found" card, not as a success. A NOERROR answer keeps the success styling; server failures and other RCODEs use the error styling
- Record types scroll sideways with a fade on each cut-off edge and a chevron beside the chips while more are off-screen
- Look up is scrolled into view when the on-screen keyboard opens, so it is not covered by the keyboard
- The Additional section starts collapsed, since it usually holds EDNS OPT pseudo-records (labelled `OPT`); an MX record of `0 .` is annotated as a null MX
- System DNS is never silently replaced with Cloudflare; when Android reports no resolver, the UI explains the failure and offers an explicit Cloudflare fallback. Private DNS status is shown when available.
- IDN hostnames and trailing-dot FQDNs are normalized consistently across network tools.

### Wi-Fi Scanner
Wi-Fi environment analysis with SSID grouping and spectrum visualisation.
- Access points grouped by SSID + security; mesh/dual-band routers appear as one entry with a per-BSSID drill-down
- **Spectrum Analyser** — frequency-domain triangle chart per AP (X = MHz, Y = RSSI −100 to −30 dBm) with channel gridlines and one SSID label per network at its strongest peak. Labels are ellipsised to a fixed width and stacked upward when they would collide; a label with no free position is left off, and the network list below is the legend. Each network gets a stable accent colour derived from its SSID hash
- Band tab-row switches between detected 2.4 / 5 / 6 GHz bands; spectrum and network list update per band
- Best-channel callout recommends the least-congested of channels 1, 6, and 11 when 2.4 GHz is active. Each candidate is scored from every 2.4 GHz AP whose channel overlaps it (±4 channels), including channels with no AP of their own, so a channel is not recommended just because nothing is on it. The callout is hidden when no 2.4 GHz AP was scanned
- Search (SSID, BSSID or vendor) narrows both the network list and the spectrum; a search with no match shows a message. Clear empties the search and closes the keyboard
- Expandable network cards show each BSSID with channel, width, RSSI, and vendor; tap any BSSID for full detail sheet. Reorders from a refresh animate, and expanding a network pins the order until every card is collapsed
- Detail sheet: signal arc gauge, band/channel/width/standard, security capability tokens, live connection stats (IP, TX/RX speed) when connected. "Standard max (theoretical)" is a PHY ceiling, not this link's rate: Wi-Fi 4 shows 289 Mbps at 20 MHz and 600 Mbps at 40 MHz (4 streams); other standards show their ceiling, which needs the widest channel and the most streams. Scan results do not report spatial streams, so a per-link rate is not shown
- Each refresh requests a platform scan; auto-refresh is configurable (Off / 15 / 30 / 60 seconds, default 30 seconds). Android throttles foreground scans to four per two minutes, so the screen shows result age and when a request was throttled; Location Services must be enabled.
- Rotating the device does not cancel a scan in progress; leaving the screen or backgrounding the app does.

### TLS Inspector
SSL/TLS certificate analysis for any TCP host, without sending an HTTP request.
- Configurable host and port (default 443). The inspection timeout is fixed at 10 seconds; there is no timeout setting
- Presented peer chain: subject/issuer, validity dates, SANs, serial number, signature and public-key details, and SHA-256 fingerprint per certificate
- Checks device trust, hostname match, expiry/not-yet-valid dates, incomplete chains, self-signed certificates, weak signatures, and weak keys
- Overall verdict is "Trusted" only when the chain is trusted by this device, the hostname matches, the leaf certificate is within its validity dates, and any expected pin matches. A hostname mismatch, expiry, not-yet-valid date, or pin mismatch each makes the verdict "Untrusted", even when the chain itself verifies
- A certificate that is not in this device's trust store is reported as untrusted by this device, which can differ from other clients on older Android versions
- A port that answers with plain text rather than TLS (for example port 80) is reported as "does not appear to speak TLS". The check recognises the wording used by Android's OpenSSL/Conscrypt and by JSSE for a plaintext peer
- When the only chain finding is an issuing root that this device does not have, the finding names it: "This device's trust store does not include the issuing root (<name>)". The verdict stays untrusted
- Connection summary includes negotiated TLS version, ALPN, cipher suite, connect time, and handshake time
- Optional older-TLS-version probes, expected SHA-256 leaf-certificate pin comparison, and PEM chain sharing

### Network Topology Discovery
SNMP-based network topology discovery via BFS traversal.
- Seed IP discovery using SNMP v1, v2c, or v3 with configurable community string / credentials
- Neighbours discovered via LLDP (IEEE 802.1AB) and CDP (Cisco Discovery Protocol)
- Per-node data: sysDescr, sysName, sysLocation, uptime, vendor, model, firmware version
- Interface enumeration with speed, MAC address, and operational status (UP/DOWN)
- VLAN discovery via Cisco VTP MIB and IEEE 802.1Q standard MIB
- Interactive force-layout canvas with pan/zoom gestures and node detail bottom sheet
- Recent seed IPs are saved locally for quick reuse
- The last discovery (its seed and final state) is kept while the app is open, so leaving the tool and coming back shows the same result. A discovery still running when the tool is left is kept as a canceled, partial result. Community strings and v3 passwords are never kept
- **Discover Topology** sits below the SNMP configuration card, outside its scroll area, and the keyboard Done action on the seed IP field starts discovery; Cancel appears in the same place while a scan runs
- Configurable max hops (1–10), timeout, and SNMP v3 auth/priv protocols (MD5, SHA-1/256/512; DES, AES-128/192/256)
- Reuses one SNMP4J session per discovery and closes it on completion or cancellation
- LLDP neighbour addresses are decoded from the LLDP management-address index; CDP accepts dotted and hex-octet cache addresses

### WHOIS Lookup
Domain, IP, and ASN registration lookup using RDAP with WHOIS fallback.
- Supports domain names, IPv4, IPv6, and ASN queries
- Protocol selector: Auto tries RDAP first and falls back to WHOIS; RDAP and WHOIS modes use only the selected protocol
- Every RDAP request (bootstrap and lookup) identifies the app with a `NetSwissKnife/<version>` User-Agent, because rdap.org refuses OkHttp's default agent with HTTP 403
- When Auto falls back to WHOIS, the result shows a note naming the RDAP error (for example "RDAP could not answer (RDAP request failed with HTTP 403), so WHOIS answered instead.")
- Domain RDAP uses the [IANA DNS bootstrap](https://data.iana.org/rdap/dns.json) to choose a registry endpoint; IP and ASN requests use the [rdap.org](https://rdap.org/) redirector
- WHOIS fallback uses the IANA-to-registry-to-registrar referral chain for domains and ARIN/referring RIRs for IPs and ASNs. A registrar referral to whois.iana.org is not queried again, since IANA holds no registrar record
- Domain dates (registered, updated, expires), name servers, status codes and DNSSEC come from the registry record, never from the IANA step. Registrar and registrant fields come from the registrar record when one answers, otherwise from the registry record
- DNSSEC reads "Signed" or "Unsigned" whichever protocol answered: the WHOIS value `signedDelegation` is shown as "Signed", and any other unlisted value is shown as readable text
- Parsed fields include registrar, registration/expiry/update dates, name servers, status codes, DNSSEC, registrant organization/country, and network ranges
- Status chips show human-readable labels for both RDAP phrasing (e.g. "client transfer prohibited") and WHOIS codes (e.g. "clientTransferProhibited"), e.g. "Transfer Locked"; unlisted statuses keep their words
- Each result states which protocol answered (RDAP or WHOIS port 43) and the server used
- Live relay-chain visualiser shows the RDAP or WHOIS servers used; completed hops share one success colour
- Invalid input names the specific problem (e.g. "An IPv4 address needs four numbers from 0 to 255" for 999.1.1.1)
- The raw RDAP/WHOIS response section expands from its whole header row; the lookup keeps running across screen rotation
- The query and protocol survive rotation and process death; leaving the tool with Back clears them

### HTTP Probe
HTTPS request tester with security header analysis. Plain HTTP requests are disabled in this release.
- Supports GET, POST, PUT, PATCH, DELETE, HEAD, and OPTIONS methods
- Custom request headers: add/remove key-value pairs dynamically
- Request body editor for POST, PUT, and PATCH
- Follow-redirects toggle with each hop's status and URL; HTTPS-to-HTTP redirects are blocked before contacting the destination
- Cross-origin redirects that would resend a request body require approval for that redirect
- Response display across four tabs:
  - **Overview**: status code (color-coded 2xx/3xx/4xx/5xx), DNS/connect/TLS/server-wait/transfer phases plus total time to first byte, negotiated HTTP/1.1 or HTTP/2 protocol, final URL, redirect hops, body size, Content-Type. The setup phases (DNS, connect, TLS) are capped so they never add up above time to first byte; TCP connect excludes the TLS handshake. A root URL shows `/`; only an existing path or query is marked `[path omitted]` or `[query omitted]`. Content-Type is read case-insensitively, so it matches the Headers tab.
  - **Headers**: collapsible request and response header sections
  - **Body**: scrollable monospace response body with copy-to-clipboard, JSON validity, and optional pretty-print; response data is capped at 512 KB with a truncation notice
  - **Security**: grades HSTS strength, CSP enforcement and script directives, clickjacking protection, deprecated X-XSS-Protection, MIME sniffing, referrer and permissions policies, cross-origin opener/embedder policies, Server version disclosure, and each Set-Cookie flag set. HTTPS-to-HTTP downgrade evidence appears separately in a blocked-redirect warning.
- Copy the configured request as a shell-quoted cURL command, including method, custom headers, body, and redirect behavior. Redirect following is omitted when custom headers, a body, or a non-GET/HEAD method could diverge from the app's redirect handling; otherwise exported commands only follow HTTPS redirects to prevent downgrades. The UI explains when following is suppressed. A "cURL command copied to clipboard" confirmation is shown.
- Failures are named by cause: a DNS failure ("The DNS lookup failed."), a timeout ("The network request timed out."), a refused connection ("The server refused the connection."), or a generic network failure.
- Cancel while a request is sending ends it as "Request canceled", even when the call reports a timeout or an answer as Cancel is pressed; the button shows "Stopping" until the request has released its connection
- Opening from mDNS Browser or LAN Scanner: HTTP Probe sends HTTPS only, so the handoff prefills `https://host/` for port 443 and `https://host:8443/` for port 8443. For any other port, such as the common plain-HTTP port 80, no URL is filled in and an explanation is shown. Plain HTTP is not sent to LAN addresses either. Cleartext support would need a separate, explicit warning-based design, so it was deferred.
- The prefilled-from banner is removed as soon as the URL is edited.

### mDNS Service Browser
LAN service discovery via multicast DNS (RFC 6762 / DNS-SD RFC 6763).
- Discovers all advertised services on the local network without prior knowledge of service types
- Two-phase discovery: enumerates service types via `_services._dns-sd._udp.local.`, then instances per type, then SRV/TXT/A/AAAA records per instance
- Supported service examples: `_http._tcp`, `_airplay._tcp`, `_ipp._tcp`, `_homekit._tcp`, `_spotify-connect._tcp`, and any other DNS-SD service
- Per-service details: display name, hostname, port, IP addresses (IPv4 and IPv6), and TXT record key-value pairs
- Live streaming results: services appear as they are discovered, grouped by service type with animated list entries
- Expandable service items show full TXT record details on tap
- Fixed 8-second scan window per Scan (there is no duration control); scan can be stopped at any time. Clear removes all results
- Instance names are shown decoded: DNS-SD escapes such as `\032` (space), `\226\128\153` (UTF-8 for ’) and `\(...\)` become readable text; malformed escapes are shown exactly as received
- Search filters the list by service type, name, hostname, IP address, and TXT key or value; a search with no matches shows an empty-state message with a Clear search action
- Copy and Share export only the services currently shown (the active search is noted as a `Filter:` line)
- Per-scan caps: 64 service types, 256 services, 16 addresses or 1 KB of addresses per service, 4 KB of TXT data per service, 1024 queries, and 512 records per packet. If a cap is reached, a neutral note names it; a complete scan that stays within the caps shows no note
- Requires Wi-Fi multicast lock (`CHANGE_WIFI_MULTICAST_STATE`) for reliable reception on Android

### Subnet Calculator
IPv4 subnet calculator with binary breakdown, notation conversion, an "is this address in the subnet?" check, and equal splitting. **IPv4 only**: IPv6 input is rejected with an explanation. IPv6 support is deferred.
- Two input modes: **CIDR / Mask** and **IP Range** (finds the tightest subnet covering a given min–max IP pair)
- CIDR mode accepts CIDR (`192.168.1.0/24`), dot-decimal mask (`192.168.1.0/255.255.255.0`), space-separated mask, or bare IP (assumes `/32`)
- Input is rejected with a specific message when the prefix is missing (`192.168.1.0/`), out of range (`/33`), has a leading zero (`/08`, `010.1.1.1`, which are not guessed), or the dotted mask is not contiguous
- Computes: network address, broadcast, first/last usable host, and exact total and usable address counts (thousands separators on screen, plain digits in copied text)
- Follows RFC 3021 for `/31`: two usable hosts, with first and last host being the two addresses. `/32` is a single host whose first and last host are the address itself
- **Scope badge** for the whole subnet: Private (RFC 1918), Shared (CGNAT, 100.64.0.0/10), Loopback, Link-local, This network (0.0.0.0/8), Documentation, Benchmarking (198.18.0.0/15), Multicast, Reserved (240.0.0.0/4), Public, or Mixed when the subnet spans several blocks (for example 0.0.0.0/0)
- **Binary Breakdown card**: colour-coded bit grid distinguishing network bits (blue) from host bits (orange) for IP address, subnet mask, and network address rows. Each row is announced to screen readers as one description
- **Notation Equivalents card**: CIDR, dot-decimal mask, wildcard mask, hex mask (`0xFFFFFF00`), and binary mask
- **Address Properties card**: IP class (A/B/C/D/E), scope badge, prefix length, and host-bit count. Each info icon explains its own field
- **Is this address in the subnet?** card: type an IPv4 address to check it against the calculated subnet. The answer is yes or no, with the reason (usable host, network address, broadcast address, or the subnet range when it is outside)
- **Split into equal subnets** card: divide the subnet either into a new prefix (for example `/26`) or into a number of subnets. A count is rounded up to a power of two, and the result says so (3 requested gives 4). Lists the networks with their first and last addresses, up to 256 rows; a larger split shows the first 256 and the total
- **Copy** and **Share** buttons put a plain-text summary of the calculated subnet on the clipboard or into the share sheet
- Quick example chips for common subnets (`/8`, `/12`, `/16`, `/24`, `/30`, `/0`)
- Network alignment warning when the entered IP is not on a network boundary, showing the corrected network address
- Entered fields survive rotation and process death; a result is recalculated when you calculate again. Leaving the tool with Back clears the fields

### Speed Test
Internet connection speed test measuring latency, download, and upload throughput.
- Three-phase sequence: **latency** (10 round-trip probes), **download**, then **upload** — each streamed live as it runs
- Animated phase stepper, live circular speed gauge, and a live throughput-over-time chart per phase
- Final results: latency min/avg/max/jitter, download/upload average & peak Mbps, and total data transferred, each with its own throughput chart
- Upload is time-boxed to the phase window and counts the bytes actually written, so a slow or briefly stalled uplink still produces a result; a stalled POST is retried on a new connection until the window ends
- A stalled latency probe under load is skipped rather than failing the run
- Failure keeps the latency and download results already measured, names the phase that failed, and offers Run again. The error text is a plain-language reason, not raw exception text
- Cancel returns to the start screen with a "Test canceled" notice and keeps any finished latency and download results. There is one Start button
- Share button exports a plain-text summary of the results
- **Powered by Cloudflare** — measurement traffic is sent to and timed against `speed.cloudflare.com` (`/__down` and `/__up`), the same backend that powers Cloudflare's public speed test at <https://speed.cloudflare.com>. Net Swiss Knife is an independent app and is **not affiliated with, sponsored by, or endorsed by Cloudflare, Inc.**; "Cloudflare" and the Cloudflare logo are trademarks of Cloudflare, Inc. Full attribution is also shown in-app under Settings → Data Source Attributions.

### Wake-on-LAN
Wake sleeping or powered-down machines on the local network with a UDP magic packet.
- Accepts `AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`, `AABB.CCDD.EEFF` and `AABBCCDDEEFF` (surrounding spaces are ignored). One notation per address: mixed separators such as `AA:BB-CC:DD:EE:FF` are rejected
- Targets must be unicast. Multicast, broadcast (`FF:FF:FF:FF:FF:FF`) and all-zero MACs are rejected
- Real-time validation with a specific inline message for each problem: wrong length, invalid characters, mixed separators, multicast, broadcast or all-zero address
- Advanced options: broadcast address (default `255.255.255.255`; any dotted-decimal IPv4 address such as a subnet-directed `192.168.1.255` is accepted; host names and IPv6 are rejected inline) and UDP port (default 9, valid 1-65535). The panel opens automatically when one of these fields is invalid
- Sends 3 duplicate packets per request for reliability over lossy UDP
- The MAC, broadcast address and port survive rotation and process death; leaving the tool with Back clears them. The saved-device name field is not kept
- Success card reports the send and states that Wake-on-LAN is fire-and-forget: no delivery confirmation is returned. It confirms target MAC, broadcast address, port and packet count
- Errors are specific: no local Wi-Fi or Ethernet network, a local-network permission denial, an invalid broadcast address, or a general send failure. Each has a Retry action
- Saved devices (stored on this device only): save the current MAC under an optional name, use a saved device to fill the form, edit a saved device's name or MAC, and delete it. A MAC is stored once, whichever notation it was entered in; an edit that would make two entries share a MAC is refused
- Saving a MAC that is already saved never silently renames it. With the name field left blank the saved name is kept; with a new name the device is renamed. Either way a note says what happened (for example "Already saved as QA dummy. Renamed to dup.")
- In-app help explains BIOS/OS requirements

### Network Info
Shows the device's active network, its IP configuration, and an optional public IP check. Read-only and local, except for the public IP check you start yourself.
- Active network: transports (Wi-Fi, cellular, Ethernet, VPN, Bluetooth), and whether the network is metered or roaming
- Wi-Fi link (Wi-Fi only): signal strength (dBm), link speed (Mbps), and frequency with band and channel. The network name is shown only when location permission has already been granted for the Wi-Fi Scanner; otherwise it reads "Unavailable". This tool requests no new permission
- IP configuration: every address on the active interface, with scope (private, shared, link-local, unique local, global, multicast, or special-purpose), prefix length, and the IPv4 subnet mask. IPv6 shows the prefix length only. Also gateways (default routes), DNS servers, MTU, search domains, and proxy settings
- MAC address: shown for Wi-Fi and Ethernet only when Android provides it. Android 10 and newer hide hardware addresses from apps, so the value is shown as unavailable there rather than a placeholder. Locally administered (randomized) addresses are labelled
- Public IP: runs only when you tap Check public IP, never on screen entry or refresh. It requests `api4.ipify.org` (IPv4) and `api6.ipify.org` (IPv6) over HTTPS. A network without IPv6 reports "No public IPv6 address" rather than an error. Each address found is then looked up for reverse DNS and approximate location and ASN. Results are cleared when the network changes
- Refresh re-reads the local network. The screen also updates when the default network changes or its addresses or routes change
- Copy any value with its copy icon or by pressing and holding its row. Copy all and Share produce a plain-text summary

### MAC Lookup
Identifies the vendor behind a MAC address or an OUI prefix, using the IEEE registry bundled with the app. Fully offline: nothing is sent to any service.
- Input: paste one or many, one per line or separated by commas, semicolons, or spaces. Colon, hyphen, Cisco dotted, and bare hex are accepted in any letter case. A three-byte OUI prefix such as `B8-27-EB` is looked up on its own. Multicast and broadcast addresses are accepted and labelled as such
- Per address: the normalized address in colon, hyphen, Cisco dotted, and bare hex (upper or lower case, each with a copy button); the vendor name, or "Unknown vendor" when the registry has no block for it; the block size (MA-L, MA-M, or MA-S) when the registry lists it; the type (unicast, multicast, or broadcast); and whether the address is locally administered
- Locally administered (randomized or private) addresses are labelled, and no vendor is shown for them, because the vendor code does not identify the hardware. Unicast addresses only: the locally administered flag reads "Not applicable" for broadcast and multicast, because the bit is not an owner's assignment for a group address. Broadcast addresses show no vendor
- The same address entered in several notations (for example `00:1A:2B:3C:4D:5E` and `001a2b3c4d5e`) is one card, marked "Entered N times, merged into one card". Copy all and Share carry the same count
- Malformed entries stay in the results with the reason they were rejected. A paste with no valid entry shows an error
- Paste from clipboard fills the field and looks it up at once. Recent lookups are kept on this device (the last five), and each can be removed or all cleared
- Look up, Paste and choosing a recent lookup close the keyboard, and the results are scrolled into view
- Copy all and Share produce a plain-text summary of every entry. The first 100 entries of a paste are analysed; the rest are reported as not looked up

## Networking behavior

LAN-directed sockets use the selected Wi-Fi or Ethernet network when the destination is within that network's subnet. This lets local discovery and device queries reach the LAN when a VPN is active; destinations outside the local subnet continue to use Android's normal route. mDNS joins the selected interface and can use an ephemeral query port when port 5353 is unavailable.

Tool screens show a status banner when internet access or a local Wi-Fi/Ethernet network is unavailable. LAN tools also explain when a VPN is active. On Android 16 (API 36) and newer, Android's Local Network Protections can require `NEARBY_WIFI_DEVICES` access before local-network operations. If access is denied, the tool shows a Grant action; grant access and retry the operation explicitly.

Settings, pinned tools, and recent targets are stored in app-private preferences. Android cloud backup and device transfer exclude this preference file; clear recent targets from Settings when you want to remove them sooner.

---

## Module Layout

```
android-network-tools/
├── app/                     # Android app module (Compose UI, ViewModels, Navigation, Hilt)
├── core-domain/             # Pure Kotlin – use cases / orchestration (depends on core-network)
├── core-network/            # Pure Kotlin – networking primitives, protocols, utilities
├── .github/
│   └── workflows/
│       ├── ci.yml                  # Standard build & test CI
│       └── release.yml             # Sign & publish release APK/AAB
├── claude/
│   └── tool_instructions.md        # Instructions for Claude when adding new tools
└── README.md
```

### `:core-network`
Pure Kotlin module (no Android SDK dependency). Contains:
- Network result wrappers (`NetworkResult`)
- Host/IP validation utilities (`HostValidator`)
- Repository interfaces, models, and protocol implementations for each tool
- All TDD unit tests

### `:core-domain`
Pure Kotlin module that depends on `:core-network`. Contains:
- Use cases that orchestrate `:core-network` logic
- `ValidateHostUseCase` and similar helpers
- Unit-tested independently

### `:app`
Android module (Jetpack Compose, Material 3, Hilt). Contains:
- Single-Activity architecture (`MainActivity`)
- Navigation Compose with a bottom navigation bar and animated transitions
- Screens and ViewModels for every tool
- Hilt dependency injection wiring

---

## Tech Stack

| Layer | Technology | Notes |
|-------|-----------|-------|
| Language | Kotlin 2.4.x | JDK 21, Kotlin DSL everywhere |
| UI | Jetpack Compose + Material 3 | Animated, high-fidelity UI |
| Navigation | Navigation Compose 2.10.x | Bottom nav + animated transitions |
| DI | Hilt 2.60.x | `@HiltViewModel`, `@AndroidEntryPoint` |
| Async | Coroutines + Flow | `viewModelScope`, `StateFlow` |
| Quality | Android Lint + CodeQL | Required in CI; dependency update report is uploaded as an artifact |
| Testing | JUnit 5 + MockK | TDD (Red → Green → Refactor) |
| Build | AGP 9.x, Gradle 9.x Kotlin DSL + Version Catalog | `gradle/libs.versions.toml` |
| Min SDK | 26 (Android 8.0) | Compile / Target SDK 37 |

---

## How to Build and Test

### Prerequisites
- JDK 21
- Android SDK (API level 37)

### Run unit tests (all modules)
```bash
./gradlew test
```

### Run unit tests for a specific module
```bash
./gradlew :core-network:test
./gradlew :core-domain:test
```

### Build the debug APK
```bash
./gradlew :app:assembleDebug
```

### Build release APK / AAB
```bash
./gradlew :app:assembleRelease --no-daemon   # unsigned APK for local R8 verification
./gradlew :app:bundleRelease
```

The release APK is unsigned unless signing properties are supplied. After an
unsigned release build, verify that R8 retained the SNMP4J security classes:

```bash
apkanalyzer dex packages app/build/outputs/apk/release/app-release-unsigned.apk \
  | grep -E 'org\.snmp4j\.security\.(AuthSHA|PrivAES128|USM)\b'
```

### Coverage (Kover)
```bash
./gradlew :app:koverVerify        # enforce 90% on the pure, non-Compose classes in the app Kover include list
./gradlew :app:koverHtmlReport    # scoped :app report
./gradlew :core-network:koverHtmlReport :core-domain:koverHtmlReport
```

---

## Available Tools

| Route | Tool | Status |
|-------|------|--------|
| `home` | Home / Overview | Implemented |
| `ping` | Ping | Implemented |
| `traceroute` | Traceroute | Implemented |
| `ports` | Port Scanner | Implemented |
| `lan` | LAN Scanner | Implemented |
| `dns` | DNS Lookup | Implemented |
| `wifi_scan` | Wi-Fi Scanner | Implemented |
| `topology` | Network Topology Discovery | Implemented |
| `tls` | TLS Inspector | Implemented |
| `whois` | WHOIS Lookup | Implemented |
| `httprobe` | HTTP Probe | Implemented |
| `subnet` | Subnet Calculator | Implemented |
| `mdns` | mDNS Service Browser | Implemented |
| `speedtest` | Speed Test | Implemented |
| `wol` | Wake-on-LAN | Implemented |
| `netinfo` | Network Info | Implemented |
| `oui` | MAC Lookup | Implemented |

---

## CI & Automation

### Standard CI (`ci.yml`)
Runs on every push to `main` and every PR targeting `main`. The `build-and-test` job runs the repository quality checks, Android Lint, unit tests, Kover verification, and a debug APK build; CodeQL analyzes Java and Kotlin separately.

The `release-smoke` job builds the unsigned, minified release APK and checks that R8 retained the SNMP and native traceroute classes, the arm64 native library, and the OUI and public-suffix resources. It uploads the unsigned APK and R8 `mapping.txt` for seven days. This is a packaging smoke check; SNMP reflective loading still needs the documented release-build functional check.

Firebase Test Lab runs on pushes to `main`. To opt a trusted pull request from a branch in this repository into Test Lab, apply the `run-instrumented` label. Fork pull requests cannot receive the Firebase service-account secret and therefore do not run that job. Newer Test Lab runs cancel older runs for the same Git ref. The job uses the `FIREBASE_SERVICE_ACCOUNT_KEY` Actions secret; the workflow does not print it.

### Release (`release.yml`)
Triggered by a new `vYYYY.MM.DD.N` tag (where `N` is 1–99) or manual dispatch. Manual dispatch allocates the next unused daily suffix and reserves the immutable tag before building. The version code is `YYYYMMDD × 100 + N`. When all four release-signing secrets are configured, the workflow signs and publishes the APK and AAB to GitHub Releases. If none are configured, it publishes unsigned artifacts; a partial signing configuration fails before the build.

Required GitHub Actions secrets:

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

---

## Adding a New Tool

See [`claude/tool_instructions.md`](claude/tool_instructions.md) for the step-by-step guide.
