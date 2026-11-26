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
- Configurable probe count (1–100 via slider) and timeout (100–30,000 ms). Count and timeout follow the Settings defaults until you change them on this screen; a change made in Settings while Ping is open reaches it, except during a run, when it applies once the run ends
- A host that does not resolve shows a "Ping failed" error with the name and a Retry action, not a packet-loss figure. Such a host is not kept in recents
- Advanced payload size (0–1,472 bytes), TTL (1–255), and interval (100–10,000 ms, in 100 ms steps) controls, with a Reset to defaults action. The count, timeout, payload, TTL and interval sliders keep their values when you leave and return (in memory for the app session, and through saved state across rotation and process death); ping results are not kept
- Rotating the device keeps a run going. A run stopped early (Stop, or the app going to the background) is labelled as stopped early, and its statistics cover only the probes that were sent
- When the host field changes after a run, the previous result is labelled as the previous result for its own host
- Per-probe RTT reporting with sequence numbers and status (SUCCESS / TIMEOUT / UNREACHABLE / ERROR), including reply IP, TTL, and bytes when available The finished result names the measuring engine; reachability-fallback runs do not apply the payload size or TTL, and the raw output says so.
- Live stats panel during active ping: packet loss %, min, avg, and max RTT updating after every packet
- RTT chart with Y-axis ms labels and fill gradient, rendered as results arrive
- **Continuous mode** — toggle replaces the count slider; pings indefinitely while the app is on screen, screen kept on automatically, stops when backgrounded or screen locked
  - Rolling window of the last 100 packets drives live stats and chart
  - Full session log streamed to a temp CSV file; shareable via the Share button on completion
- Tools with recent-target support save up to five entries on-device and show quick-select chips; clear recent targets from Settings
- Only a valid host is saved to recent targets. A dotted name whose last label is all digits (for example two addresses typed together, `10.109.208.25010.109.208.200`) is rejected as an invalid address and is never run. Recents saved by an earlier version that fail this rule are removed the first time the tool opens, and are never shown
- CSV exports retain the original columns and append reply TTL and payload bytes.
- Copy (CSV) and the raw output copy put the text on the clipboard and confirm with "Copied to clipboard"

### Traceroute
Network path analysis with per-hop geolocation enrichment.
- Configurable max hops (5 to 64, whole numbers), timeout per hop (0.5 to 10 s in 0.5 s steps), and probes per hop (1 to 5, default 3)
- Dual protocol support: ICMP and UDP
- Automatic MTU discovery or a packet size picked from 28, 56, 64, 128, 256, 512, 576, 1,024, 1,280 or 1,472 bytes (56 by default)
- Once a trace has a result, the configuration folds to one line (host, hops, probes, timeout, protocol and packet size or MTU discovery) with a Show settings action; while a trace started from that line runs, the action is Stop. Hop details come before route geography
- Every hop count from 1 to the destination has a row, in hop order, even when an earlier hop's timeout arrives after the destination has answered. A hop that sent no reply is shown as `* * *`; two or more silent hops in a row fold into one "Hops 4 to 6" row. Silent hops after the last reply of a finished trace are marked as probed but possibly not on the route
- Hops past the destination are dropped. With several probes per hop, each hop stays one row that updates as its probes come back
- The **Trace results** card shows **Hops replied** as "replied of probed", where probed is the destination's hop number when it answered, plus total time and whether the destination was reached. Below that it quotes one RTT, labelled with what it is ("median of 3"): the destination's RTT, or the last reply's when the destination did not answer. When replies stop before the destination it says after which hop, and which hops were probed without an answer. No RTT is averaged across hops, and no timeout rate is shown
- The result shows **Resolved IP** only when the name resolved to a different address; an IP target is not repeated
- A trace that stops because you left the screen reads "Trace stopped when you left the screen" with its partial hops; a trace stopped with Stop reads "Trace canceled"
- A hop's RTT is the median of its answered probes, labelled "median of N" (or "1 reply"), and the same figure is used in the hop list, the summary, the map colours, the map's speed and the callouts. Hops show IP address, per-probe RTTs, and min/average/max when multiple probes are requested; reverse DNS and geolocation fill in asynchronously when available, and are looked up once per hop
- Host validation names the problem (spaces, IPv4 value out of range, empty or invalid hostname label, IPv6, length)
- Errors use plain, translated copy: host not found, host lookup took too long (no address within 10 s), no network, local network permission, trace too long for the 20 minute limit, timed out, interrupted, and no hop replied
- **Route geography** lists distance, countries and networks for hops with a GeoIP location, labelled as approximate. When a hop's location cannot be reached within the measured RTT at about 200 km per ms (light in fibre), the distance and country route are hidden and the card says why
- Three tabs, **Hop details**, **Map** and **Raw output**, are available while the trace runs and stay on the same tab when it finishes. The chosen tab is kept with the result when you leave the tool and come back, and the next trace opens on it. A stopped (partial) trace has the same tabs
- **Map** draws the route on an offline world map (Natural Earth 1:110m land, public domain, bundled as a 20 KB asset; no tiles or network requests). Each hop with a GeoIP location is a numbered marker; consecutive hops at the same coordinates share one marker. The route starts at the first located hop, because the device's own location is unknown, and the card says so
  - The map grows while the trace runs. Each new arc draws in once, including one that appears when a late location arrives; arcs already drawn stay drawn when the trace finishes. The view moves smoothly to keep new stops in frame until you pan or zoom, after which it stays put and a reset button returns to the fit. A pulsing ring marks the newest located hop while the trace runs and is removed when it ends. Under the map, one exact sentence per reason lists the hops after the first marker that are not on the drawn route, for example "Hops 18 to 30 sent no reply.", "Hops 15 and 16 have no known location." (or "are not located yet" while running) and "Hop 17 has a disputed location, so it is not joined into the route."
  - Segments are arcs that bow to the left of the direction of travel, higher for longer hops, so a hop out and back stays visible as two arcs. A hop across the Pacific takes the shorter way over the antimeridian
  - The map is at least 1.6 times as wide as it is tall, and on a tall window takes up to 45% of the window height (never squarer than 1.1 to 1, 220 to 640 dp)
  - **Colour** is the median RTT of the hop the arc arrives at, with the hop list's colours and thresholds: under 50 ms, 50 to 149 ms, 150 to 299 ms, 300 ms or more
  - **Width** is speed: the arc's great-circle distance divided by half the median RTT increase from the previous marker, compared with light in fibre (200 km per ms). Thick at 50% or more, medium from 20% to 49%, thin under 20%. Arcs shorter than 300 km, arcs whose RTT grows by less than 5 ms (each median is rounded to whole milliseconds), and arcs whose figure would be faster than light in fibre get the medium width and make no speed claim; the callout says the speed cannot be measured
  - **Line pattern** has one meaning per pattern: solid when every probe answered and jitter (slowest minus fastest probe) is low; dotted when a probe was lost, or jitter is 20 ms or more and at least 25% of the median RTT; dash and dot when stability was not measured: one probe was sent, the hop has not reported all its probes yet, or the trace was stopped and the hop's missing replies all came after its last answered probe, so they may have been cut short (a missing reply followed by an answered one is still a loss, and high jitter among the replies that did come is still dotted); grey dashes when the arc crosses hops that did not answer, have no location, or have a disputed location. A grey dashed arc makes no latency, speed or stability claim. The legend lists exactly the colours, widths, patterns and markers on the map, each with a sample drawn the way the map draws it, plus the highlight a tap adds, read from the same model the map draws from, and with 1 probe per hop says that stability needs 2 or more probes. It is collapsible, and uses two columns on a card 440 dp or wider
  - A hop whose location the measured RTT rules out (the plausibility check uses each hop's fastest probe, a physical lower bound) is drawn as a hollow marker off the route, and its callout says "Location disputed (possibly anycast)". A destination placed in Brisbane 25 ms after London hops is one such case. The destination's marker has a double ring
  - Markers closer on screen than a marker's width share one marker label ("3-5"), which splits again as you zoom in. Each label is placed to the right, left, above or below its marker, inside the map and clear of other markers, labels and the zoom buttons; a label that fits nowhere is left out, and its marker stays tappable
  - Pinch, double tap, or use the 48 dp zoom in and zoom out buttons to zoom; once zoomed in past the automatic fit, drag to move; a one-finger drag scrolls the page instead when the map is not zoomed in or cannot move further in the drag's main direction. The reset button is enabled only when the view differs from the automatic fit. Tapping a marker, or its label (a target at least 48 dp tall), lists every hop under it with its IP, median RTT, location, replies and jitter, the same values as the hop list. Tapping an arc shows its two hops, the RTT it arrives with, the RTT it adds, its distance, its speed as a share of light in fibre and how steady the replies were in plain words; an unknown path names the hops in between instead. The details appear right under the map and scroll into view on a tap; tapping the same marker or arc again clears them. The legend is text with samples, and the map has a spoken summary of the stops in order with their RTTs, which while running also says the trace is in progress, the newest located hop and which hops are not located yet
  - With no located hop the tab shows the same message as Route geography, or while running says that no hop is located yet
- Backgrounding the app stops a running trace and keeps the hops found so far; a screen rotation does not stop it
- The trace ends once the destination has answered and every earlier hop has replied or timed out; replies for hops past the destination are dropped
- When two or more consecutive replies all come from this device's own addresses, the trace stops early and reports "Host unreachable from this network (no route / destination host unreachable)" with Reached: No. A destination on the local subnet that does not answer is typically reported this way
- Editing the host field clears a previous "Trace failed" message
- The host and settings survive rotation and process death. Leaving the tool with Back keeps the host, settings and last finished result for the rest of the app session (in memory only, never on disk). A trace still running when you leave is not restored as running
- Copy and Share at the foot of the Trace results card send the same text: the hop list (the raw output). Raw output fills in live while the trace runs, one line per hop with each probe's RTT or `*`

### Port Scanner
TCP port reachability scanning with service identification.
- 7 preset port groups: Common Services, Well-Known (1–1024), Web, Databases, Mail, Remote Access, and Custom range (up to 10,000 ports)
- The range is set with the Start and End fields only (no range slider). An inverted range or one over 10,000 ports shows a message and disables Scan Ports. The selected range and port count are shown next to Scan Ports
- Concurrency is chosen from fixed steps (1, 2, 5, 10, 25, 50, 100, 250, 500 simultaneous probes) and the per-port timeout is a slider (200–10,000 ms)
- The worst-case duration is estimated from the port count, timeout and concurrency. Scan Ports stays disabled, with the estimate shown, when that worst case would exceed the 15-minute operation limit
- Service names come from the built-in registry; a port with no registered name shows no service name rather than a generic label
- Banner grabbing for open ports. Multi-line replies (for example HTTP headers) keep their lines, joined with " | ", up to 200 characters
- Copy and Share at the foot of the summary card send a plain-text report (host, IP, port counts, duration, open ports with banners and TLS subject). Copy confirms with "Copied to clipboard". The host line shows the resolved IP only when it differs from the host, and the Open section does not repeat the Open badge on each row
- Invalid hosts show the specific reason (for example, a space, an out-of-range IPv4 octet or an invalid character) instead of one generic message. Editing the host clears the previous result
- A scan stopped with Stop Scan, or stopped when the app leaves the foreground, is labelled as stopped early with the number of ports scanned so far. A screen rotation does not stop a running scan
- Leaving the tool and coming back keeps the host, port range, preset, timeout, concurrency and the last finished result, but a result is shown again only for the host it scanned. After a process death the form returns and the result does not

### LAN Scanner
Local network device discovery across IPv4 subnets.
- CIDR subnet scanning (/16–/30) with automatic current-subnet detection. The location icon at the start of the subnet field fills in the detected subnet again at any time. Until a scan runs, the empty-state card names the subnet that **Scan Network** will scan
- Scans are capped at an estimated 10 minutes. When the detected network is too large for that at the current timeout and concurrency (for example a /16 on corporate Wi-Fi), the subnet field is pre-filled with the /24 around this device and a hint names the full network. An over-limit range entered by hand, including prefixes broader than /16, offers a one-tap scan of this device's /24 instead. On a fresh form, a saved concurrency too low to scan the detected subnet within the limit is raised to the lowest slider value that fits, and a hint under the slider names the saved value
- Multi-method presence detection: an ICMP echo with the configured timeout, completed TCP connections on 10 common ports (at most 400 ms each), correlated NetBIOS NBSTAT or mDNS replies (at most 300 ms each), then a second ICMP echo with the configured timeout. The second echo finds devices whose first reply was lost to ARP resolution or Wi-Fi power saving. An expanded device shows a **Found by** row (Ping reply, Open TCP port, NetBIOS reply, mDNS reply) from the probes that confirmed it
- Failed connects, timeouts, unreachable routes, and policy failures remain opt-in diagnostics and never inflate the confirmed device count or confirmed-device exports
- Per-host details: IP, hostname, MAC address, OUI vendor name, open ports, the gateway flag from the active default route, and a **This device** label on the row of the phone or tablet running the scan. Latency is the single ICMP reply measured during the scan, so it can be higher or lower than a later Ping run (a Wi-Fi device in power saving answers at very different speeds), and it is shown only when an ICMP reply was measured (a reply under 1 ms reads **Latency under 1 ms**); a host confirmed by TCP, NetBIOS or mDNS shows **No ping reply** instead of a value
- MAC resolution is best-effort: Android 10+ restricts the ARP source, so the UI explains when a MAC cannot be read. Whether MACs can be read is known from the start of the scan, not only once it finishes
- Concurrent host probes (1, 2, 5, 10, 25, 50, 100, 250 or 500, the Port Scanner steps) with real-time progress streaming (one circular indicator that fills with the share of addresses checked, and a **Stop Scan** button on the progress card), post-probe ARP enrichment, and a final summary
- Result summary: a status line (**Scan complete**, **Scan paused** or **Scan stopped**), one headline with the confirmed device count, and one meta line with the subnet, the scanned address count and the scan duration. Re-scan is the primary action; Copy, Share and Clear sit below it. While results are shown, the scan configuration card shows only timeout and concurrency, so the subnet appears once
- Uncertain probes are a single count line in the summary. Tapping it opens a bottom sheet that explains which checks the address failed and lists the first TCP result (reason and port) for each uncertain address in address order, up to 1,024 rows, so every address of a /22 or smaller is listed; beyond that a footer states how many are shown; the sheet opens fully expanded, scrolls as one list, and keeps its last row above the system navigation bar. The sheet does not grow the summary
- Devices under a **Devices** heading with a **List / Map** toggle (default List). Only one view is shown at a time, and both show the same search, filter and sort result. The heading always shows `N of M`. The List or Map choice is kept while the app stays open. Search matches IP, hostname, vendor or MAC, ignoring spaces around the query; filters are All, Gateway and Has Ports, tapping the selected filter again returns to All, and the chosen filter is saved like the sort order; **Sort by** offers IP, latency, hostname, vendor and port count. Hosts without an ICMP reply sort after measured hosts when sorting by latency
- Map view (**Map** in the toggle): one node per visible device, showing its icon and its hostname on up to two lines (or the last IP octet when no hostname is known). A line under the title says that tapping a device opens its details in the list. The map follows the search, filter and sort, uses as many columns as the card width allows, and draws the first 24 nodes followed by a **+N more** control; each tap reveals the next 24. The map is a grid of devices in scan order, not a network topology. Tapping a node switches to the List view, scrolls to that device and expands it
- Expanded devices show hostname, MAC, vendor and open ports (or **No open ports among the 16 common ports checked**), with Ping host, Scan ports and Wake device actions. Probe HTTP appears only when port 80 or 8080 is open, and Inspect TLS appears only for open TLS ports among the 16 probed ports (443 and 8443 in practice); the header keeps the IP, gateway tag and latency. The device icon marks only the gateway with a router; other devices share one neutral icon. The OUI registry contains 50,000+ prefixes and can be refreshed with `python3 tools/oui/update_oui.py`
- Back closes an expanded host first and keeps the results; a rotation keeps the scan running; there is no pull-to-refresh, so a swipe never discards results (use **Re-scan**)
- An error card stays only until the subnet, timeout or concurrency that produced it changes; then the form returns to its ready state
- Leaving the tool and coming back shows the last result, subnet, timeout and concurrency again (kept while the app stays open). A scan still running when you left the screen is kept as partial results marked as paused. After a process death the subnet, timeout and concurrency return but the result does not, since results are not saved to disk
- Uncertain probe rows name the reason and port (for example "TCP probe timed out, port 80") rather than raw exception text
- Copy and Share send the confirmed devices as plain text: a title line with the subnet, then the same device headline and address count and duration as on screen (for example `53 devices found` and `254 addresses · 1.3 s`), then one line per device

### DNS Lookup
Full DNS record resolution with multiple resolver options and protocol-level response details.
- 10 record types: A, AAAA, MX, TXT, CNAME, NS, SOA, PTR, SRV, CAA
- Resolver options: system default, Google (8.8.8.8), Cloudflare (1.1.1.1), OpenDNS (208.67.222.222), Quad9 (9.9.9.9), or a custom server. Presets are read-only choices; Custom opens an empty address field that must hold an IPv4 or IPv6 address before Look up is enabled
- PTR queries auto-reverse IPv4 addresses to `.in-addr.arpa` and IPv6 to `.ip6.arpa` form — just enter the IP
- An IP address looked up as another type (for example A) that returns no records gets a note that an address has no such records of its own, with a **Look up PTR** button that switches the type to PTR and runs the lookup
- Returns each record's actual RR type and section, RCODE, AA/AD/TC/RD/RA flags, query time, server actually used, and raw DNS response
- The domain field is checked as you type: names with spaces, empty labels or invalid characters show a plain-language message and Look up stays disabled. Names with leading underscores (SRV, TXT owner labels) are accepted
- Editing the name, record type or server clears a finished result or error so an old answer is not shown for a new query
- The name, record type and resolver survive rotation and process death. Leaving the tool with Back keeps them and the last finished result for the app session (in memory only); a lookup still running when you leave is not restored as running, and Stop or clearing the results is not restored either
- An NXDOMAIN answer is shown as a neutral "Domain not found" card, not as a success. A NOERROR answer keeps the success styling; server failures and other RCODEs use the error styling. The domain appears once, in the summary card; the card subtitles do not repeat it
- Record types scroll sideways with a fade on each cut-off edge and a chevron button beside the chips while more are off-screen; tapping it scrolls to the last types
- Look up is scrolled into view when the on-screen keyboard opens, so it is not covered by the keyboard. After rotation, dark mode or a font change the keyboard stays closed. If the keyboard is open when the screen rotates, the rotation closes it and focus is not restored; tap the field to reopen the keyboard
- Answer records are listed first under an **Answer** label. The Authority and Additional sections follow them, and the Additional section starts collapsed, since it usually holds EDNS OPT pseudo-records (labelled `OPT`, with no TTL shown, because their TTL field carries EDNS flags); an MX record of `0 .` is annotated as a null MX
- System DNS is never silently replaced with Cloudflare; when Android reports no resolver, the UI explains the failure and offers an explicit Cloudflare fallback. Private DNS status is shown when available.
- IDN hostnames and trailing-dot FQDNs are normalized consistently across network tools.
- Copy and Share at the foot of the result card send a plain-text summary: type, domain, server, query time, response code, and every record with its TTL. Copy confirms with "Copied to clipboard". The result header names the server used once; each record shows its type once, in its badge. Per-record and raw-response copies confirm the same way
- Answer owner names are shown only when they differ from the queried name. Record type descriptions are listed in the help sheet rather than on the screen.

### Wi-Fi Scanner
Wi-Fi environment analysis with SSID grouping and spectrum visualisation.
- A hidden network is named `<Hidden: BSSID>` once; no separate Hidden badge repeats it
- Each BSSID is listed once. A hidden-SSID copy of a named access point (same BSSID) is folded into the named entry rather than counted as another network
- The hero states the access-point count; the scan is started by a labelled **Scan** button, and the time of the last scan is in the Updated line
- Access points grouped by SSID + security; mesh/dual-band routers appear as one entry with a per-BSSID drill-down
- **Spectrum Analyser** (collapsed by default; its Show spectrum toggle sits under the search box) — frequency-domain triangle chart of the chosen band's APs (X = MHz, Y = RSSI −100 to −30 dBm) with channel gridlines and one SSID label per network at its strongest peak. Labels are ellipsised to a fixed width and stacked upward when they would collide; a label with no free position is left off, and the network list below is the legend. Each network gets a stable accent colour derived from its SSID hash
- Band tabs sit directly under the search box and start on **All**; a tab appears once its band is seen. Choosing a detected 2.4 / 5 / 6 GHz band narrows the spectrum and network list to that band. In the All view each network row shows its band badge. The hero names the band only when a single band is detected, so each band is named once on the screen
- Best-channel callout recommends the least-congested of channels 1, 6, and 11 when 2.4 GHz is active. Each candidate is scored from every 2.4 GHz AP whose channel overlaps it (±4 channels), including channels with no AP of their own, so a channel is not recommended just because nothing is on it. The callout is hidden when no 2.4 GHz AP was scanned
- Search (SSID, BSSID or vendor) narrows both the network list and the spectrum; a search with no match shows a message with a Clear search action. Clear empties the search and closes the keyboard
- Expandable network cards show each BSSID with channel, width, RSSI, and vendor; tap any BSSID for full detail sheet. Reorders from a refresh animate, and expanding a network pins the order until every card is collapsed
- Detail sheet: signal in dBm with a level label (excellent to poor), band/channel/width/standard, security capability tokens. When connected, a Connected card lists the link facts once (BSSID, band and channel, signal, link speed, TX/RX speed, IP, IPv6, gateway, DNS). "Standard max (theoretical)" is a PHY ceiling, not this link's rate: Wi-Fi 4 shows 289 Mbps at 20 MHz and 600 Mbps at 40 MHz, each labelled with its 4 spatial streams; other standards show their ceiling, which needs the widest channel and the most streams. Scan results do not report spatial streams, so a per-link rate is not shown
- The Gateway row shows the IPv4 default gateway when the link has one, so it stays the same across refreshes; an IPv6 gateway is shown only when no IPv4 default gateway exists
- Each refresh requests a platform scan; auto-refresh is configurable (Off / 15 / 30 / 60 seconds, default 30 seconds). Android throttles foreground scans to four per two minutes, so the screen shows result age (kept up to date while the screen is visible) and when a request was throttled; Location Services must be enabled.
- Rotating the device does not cancel a scan in progress; leaving the screen or backgrounding the app does.

### TLS Inspector
SSL/TLS certificate analysis for any TCP host, without sending an HTTP request.
- Configurable host and port (default 443). The inspection timeout is fixed at 10 seconds; there is no timeout setting
- Cancel ends the inspection in a neutral "Inspection canceled" card with a Retry action; a timeout reads "The server did not answer in time. Check the host and port."
- A host that does not resolve reads "Could not find an address for <host>. Check the name, then try again.", the same wording as Ping and Traceroute
- Presented peer chain: subject/issuer, validity dates, SANs, serial number, signature and public-key details, and SHA-256 fingerprint per certificate
- Checks device trust, hostname match, expiry/not-yet-valid dates, incomplete chains, self-signed certificates, weak signatures, and weak keys
- Each certificate is labelled Leaf, Intermediate or Root. Root marks a self-signed certificate wherever the server sends it; a certificate that is not self-signed, including a cross-signed copy of a root, is labelled Intermediate
- Chain findings are badged **Fails check** when they block trust (untrusted chain, expiry, not yet valid, hostname mismatch, pin mismatch) and **Advisory** otherwise. The connection card has a Hostname row that says whether the host matches the certificate
- Overall verdict is "Trusted" only when the chain is trusted by this device, the hostname matches, the leaf certificate is within its validity dates, and any expected pin matches. A hostname mismatch, expiry, not-yet-valid date, or pin mismatch each makes the verdict "Untrusted", even when the chain itself verifies. A line under the verdict states what it rests on, and the certificate findings list what failed
- Each certificate's header chip shows days left (or EXPIRED / NOT YET VALID) coloured by state: error when expired or not yet valid, warning within 30 days, OK beyond that. The validity end date is shown once, in the card's Valid To row. The connection card does not repeat the host and port you entered
- A certificate that is not in this device's trust store is reported as untrusted by this device, which can differ from other clients on older Android versions
- A port that answers with plain text rather than TLS (for example port 80) is reported as "does not appear to speak TLS". The check recognises the wording used by Android's OpenSSL/Conscrypt and by JSSE for a plaintext peer
- When the only chain finding is an issuing root that this device does not have, the finding names it: "This device's trust store does not include the issuing root (<name>)". The verdict stays untrusted
- A server that answers the device's hello with a `handshake_failure` alert (for example a gateway with a P-521 certificate, which Android's built-in TLS cannot offer) is retried with a TLS 1.2 compatibility probe that only reads the unencrypted certificates. The result is shown as Untrusted with a note that the handshake was not completed, and protocol probes are skipped. If the certificates cannot be read either, the error explains that the server refused the handshake
- Connection summary includes negotiated TLS version, cipher suite, connect time, and handshake time, plus ALPN when the server agrees an application protocol and Android reports it (Android 10 and newer)
- Optional older-TLS-version probes, expected SHA-256 leaf-certificate pin comparison, and PEM chain sharing
- Older-TLS-version rows show "Not offered by this Android version" for a version this device cannot negotiate. When the server accepts TLS 1.0 or 1.1, an advisory row says they are deprecated
- A URL typed into the host field gets its own message asking for a host name or IP address without `http://` or `https://`
- Clearing recent hosts asks for confirmation first
- Copy and Share sit at the foot of the results and send the connection summary, each certificate from leaf to root, and the chain findings as plain text. Share PEM still sends the PEM chain, next to them

### Network Topology Discovery
SNMP-based network topology discovery via BFS traversal.
- Seed IP discovery using SNMP v1, v2c, or v3 with configurable community string / credentials
- Neighbours discovered via LLDP (IEEE 802.1AB) and CDP (Cisco Discovery Protocol)
- Per-node data: sysDescr, sysName, sysLocation, uptime, vendor, model, firmware version
- Interface enumeration with speed, MAC address, and operational status (UP/DOWN)
- VLAN discovery via Cisco VTP MIB and IEEE 802.1Q standard MIB
- Interactive hop-ring canvas: the seed sits at the centre, each further hop sits on an outer ring, and devices that do not respond to SNMP are placed on the outermost ring. Pan and pinch to zoom, tap a node for its detail sheet, and use the fit button to reset the view to the whole graph
- Filled nodes are queried devices, outlined circles are reported neighbours that were not queried, and a dashed ring marks a device whose SNMP did not respond. A legend in the corner explains the markers that are on screen
- Recent seed IPs are saved locally for quick reuse
- The last discovery (its seed and final state) is kept while the app is open, so leaving the tool and coming back shows the same result. A discovery still running when the tool is left is kept as a canceled, partial result. Community strings and v3 passwords are never kept
- **Discover Topology** sits below the SNMP configuration card, outside its scroll area, and the keyboard Done action on the seed IP field starts discovery; Cancel appears in the same place while a scan runs. The failure or result card starts directly under Discover
- Configurable max hops (1–10) and SNMP v3 auth/priv protocols (MD5, SHA-1/256/512; DES, AES-128/192/256). The per-request SNMP timeout is fixed at 3 seconds with one retry, and discovery is refused up front if its worst-case duration would exceed 10 minutes
- Reuses one SNMP4J session per discovery and closes it on completion or cancellation
- The collapsed SNMP Configuration header names the target and SNMP version, so the folded card still shows what was scanned
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
- Each result states which protocol answered (RDAP or WHOIS port 43); the query chain names the server used
- A name the registry has no record of shows a "No registration found" card above the raw response
- IP and ASN results use the organisation name as the heading, with the registry handle beneath it
- Live relay-chain visualiser shows the RDAP or WHOIS servers used; completed hops share one success colour
- Invalid input names the specific problem (e.g. "An IPv4 address needs four numbers from 0 to 255" for 999.1.1.1)
- The raw RDAP/WHOIS response section expands from its whole header row (one chevron shows its state; there is no second switch); the lookup keeps running across screen rotation. A single ASN or address range is shown once (for example `15169`), not as `15169-15169`
- Copy and Share sit at the foot of the results and send the parsed fields (registrar, dates as calendar dates, name servers, status) and the raw WHOIS text
- The query is checked as you type: an invalid query shows an inline message, Look up stays disabled and nothing is sent, so there is no Retry for an input error
- The query and protocol survive rotation and process death. Leaving the tool with Back keeps the query, protocol and the finished result for the query it was run for (in memory only); a lookup still running when you leave is not restored as running. Request headers are not involved

### HTTP Probe
HTTP and HTTPS request tester with security header analysis. Plain HTTP requests are allowed; the screen shows a warning that they are not encrypted.
- The result tabs (Overview, Headers, Body, Security) scroll sideways with a fade on the cut-off edge; on a narrow screen with a large font the last tab may still need a swipe
- Recent URLs hide the entry that is already in the URL field, so the row never repeats the typed value; the row is hidden when nothing else is left. Only HTTPS origins are kept in Recent; a plain-HTTP URL is not saved, since it usually points at a device on the local network
- Supports GET, POST, PUT, PATCH, DELETE, HEAD, and OPTIONS methods
- Custom request headers: add/remove key-value pairs dynamically
- Request body editor for POST, PUT, and PATCH
- Follow-redirects toggle with each hop's status and URL; HTTPS-to-HTTP redirects are blocked before contacting the destination
- Cross-origin redirects that would resend a request body require approval for that redirect
- Response display across four tabs:
  - **Overview**: status code (color-coded 2xx/3xx/4xx/5xx), DNS/connect/TLS/server-wait/transfer phases plus total time to first byte, negotiated HTTP/1.1 or HTTP/2 protocol, final URL, redirect hops, body size, Content-Type. The setup phases (DNS, connect, TLS) are capped so they never add up above time to first byte; TCP connect excludes the TLS handshake. A root URL shows `/`; only an existing path or query is marked `[path omitted]` or `[query omitted]`. Content-Type is read case-insensitively, so it matches the Headers tab.
  - **Headers**: collapsible request and response header sections. The request section lists the headers the client sent on the final hop, including any it added itself, not only the custom headers entered
  - **Body**: scrollable monospace response body with copy-to-clipboard. The JSON validity row and pretty-print appear only for JSON responses (`application/json`, `text/json`, or a `+json` type). Bodies longer than 32,000 characters show only the first 32,000 with a truncation notice and skip pretty-print and the JSON check. Pretty-printing runs off the main thread. Response data is capped at 512 KB with a truncation notice; a truncated, compressed response reads its size as "at least"
  - **Security**: grades HSTS strength, CSP enforcement and script directives, clickjacking protection, deprecated X-XSS-Protection, MIME sniffing, referrer and permissions policies, cross-origin opener/embedder policies, Server version disclosure, and each Set-Cookie flag set. On non-HTML responses (for example JSON), a missing CSP, X-Frame-Options, COOP or COEP is rated Info, because browsers apply those headers only to documents. An Info chip in the summary counts Info results. HTTPS-to-HTTP downgrade evidence appears separately in a blocked-redirect warning.
- Copy the configured request as a shell-quoted cURL command, including method, custom headers, body, and redirect behavior. Redirect following is omitted when custom headers, a body, or a non-GET/HEAD method could diverge from the app's redirect handling; otherwise exported commands only follow HTTPS redirects to prevent downgrades. The UI explains when following is suppressed. A "cURL command copied to clipboard" confirmation is shown. A result shown again after leaving the tool has no Copy as cURL action.
- Failures are named by cause: a DNS failure ("The DNS lookup failed."), a timeout ("The network request timed out."), a refused connection ("The server refused the connection."), or a generic network failure.
- Cancel while a request is sending ends it as "Request canceled", even when the call reports a timeout or an answer as Cancel is pressed; the button shows "Stopping" until the request has released its connection
- Opening from mDNS Browser or LAN Scanner: the handoff prefills `https://host/` for port 443, `https://host:8443/` for port 8443, and `http://host[:port]/` for any other port (for example 80 or 8080). Nothing is sent until you press Send, and a warning is shown while the URL is plain HTTP. HTTPS redirects are never followed to plain HTTP.
- The prefilled-from banner is removed as soon as the URL is edited.
- Leaving the tool with Back keeps the URL, method, redirect flag and the last finished result for the app session (in memory only, never on disk). Request headers and the request body are never kept, so a result shown again has no request headers or body, and its Request headers section says the details were not kept. A request still running when you leave is cancelled and not restored as running
- Copy and Share sit at the foot of the response card, with Copy as cURL, and send a plain-text summary: status, final URL, redirects, response headers, size, and security checks. Cookie, Set-Cookie, Authorization and Proxy-Authorization values are omitted, and request headers and the request body are never included. Share text reduces redirect addresses to their scheme and host, marking any path or query as omitted. The result screen shows the final URL, each redirect and any Location header with their path and query, but never the credentials or fragment of a URL

### mDNS Service Browser
LAN service discovery via multicast DNS (RFC 6762 / DNS-SD RFC 6763).
- Discovers all advertised services on the local network without prior knowledge of service types
- Two-phase discovery: enumerates service types via `_services._dns-sd._udp.local.`, then instances per type, then SRV/TXT/A/AAAA records per instance
- Supported service examples: `_http._tcp`, `_airplay._tcp`, `_ipp._tcp`, `_homekit._tcp`, `_spotify-connect._tcp`, and any other DNS-SD service
- Per-service details: display name, hostname, port, IP addresses (IPv4 and IPv6), and TXT record key-value pairs
- Live streaming results: services appear as they are discovered, grouped by service type (friendly name and icon, such as Printer or AirPlay; unknown types show their raw name only) or, with the **By device** toggle, by device (hostname, else first address, else instance name)
- Only services with TXT records expand on tap; the first 5 entries show, with a **Show all N** control for the rest. Services without TXT records have no expand control
- Fixed 8-second scan window per Scan (there is no duration control); scan can be stopped at any time, and a progress bar with elapsed seconds shows how far the window has run
- Results accumulate across scans: each Scan adds to the list, and a service seen again updates its existing entry instead of appearing twice. Clear removes every result and is available when no scan is running
- A change of network clears every listed service and stops a scan still running on the old network, so results never mix networks; a note says so until the next scan or Clear
- Copy and Share are hidden when the search matches nothing
- Multicast discovery runs only on the selected Wi-Fi or Ethernet network, never on a VPN or cellular path. If no such network is available, the scan stops with a plain message saying a VPN or mobile data cannot discover local devices
- If the network changes during a scan after services were found, the scan stops, keeps those services, and shows a note that the network changed; with no services found, it shows a plain message and a retry path
- Instance names are shown decoded: DNS-SD escapes such as `\032` (space), `\226\128\153` (UTF-8 for ’) and `\(...\)` become readable text; malformed escapes are shown exactly as received
- Search filters the list by service type, name, hostname, IP address, and TXT key or value; a search with no matches shows an empty-state message with a Clear search action; while a filter is active the count reads "N of M services"
- Copy and Share sit above the groups, next to the service count, and export only the services currently shown (the active search is noted as a `Filter:` line)
- Unscoped IPv6 link-local addresses (`fe80::/10` without a `%` interface suffix) are listed but get no Ping chip, because the announcement does not say which interface they belong to
- Per-scan caps: 64 service types, 256 services, 16 addresses or 1 KB of addresses per service, 4 KB of TXT data per service, 1024 queries, and 512 records per packet. If a cap is reached, a neutral note names it and says whether all services found are shown; a complete scan that stays within the caps shows no note
- Once services are found, the hero folds to its title and counts so the list starts near the top of the screen. The whole screen scrolls, including the results (there is no pull-to-refresh; Scan starts a new scan), so the list is never confined to a short inner window in landscape or at large font sizes
- A failed scan shows a plain-language reason with **Retry** (starts a new scan) and **Dismiss** (clears only the error; services already listed are kept)
- Requires Wi-Fi multicast lock (`CHANGE_WIFI_MULTICAST_STATE`) for reliable reception on Android

### Subnet Calculator
IPv4 subnet calculator with binary breakdown, notation conversion, an "is this address in the subnet?" check, and equal splitting. **IPv4 only**: IPv6 input is rejected with an explanation. IPv6 support is deferred.
- Two input modes: **CIDR / Mask** and **IP Range** (finds the tightest subnet covering a given min–max IP pair)
- IP Range mode with a minimum above the maximum is flagged on the maximum field with a specific message, and Find subnet is disabled until the bounds are corrected
- CIDR mode accepts CIDR (`192.168.1.0/24`), dot-decimal mask (`192.168.1.0/255.255.255.0`), space-separated mask, or bare IP (assumes `/32`)
- Input is rejected with a specific message when the prefix is missing (`192.168.1.0/`), out of range (`/33`), has a leading zero (`/08`, `010.1.1.1`, which are not guessed), or the dotted mask is not contiguous
- Computes: network address, broadcast, first/last usable host, and exact total and usable address counts (thousands separators on screen, plain digits in copied text)
- Follows RFC 3021 for `/31`: two usable hosts, with first and last host being the two addresses. `/32` is a single host whose first and last host are the address itself
- The broadcast row is hidden for `/31` (no broadcast address under RFC 3021), and the first and last host rows are hidden for `/32`
- **Scope badge** for the whole subnet: Private (RFC 1918), Shared (CGNAT, 100.64.0.0/10), Loopback, Link-local, This network (0.0.0.0/8), Documentation, Benchmarking (198.18.0.0/15), Multicast, Reserved (240.0.0.0/4), Public, or Mixed when the subnet spans several blocks (for example 0.0.0.0/0)
- **Binary Breakdown card**: colour-coded bit grid distinguishing network bits (blue) from host bits (orange) for IP address, subnet mask, and network address rows. On screens narrower than 600 dp the octets wrap to two rows of two so each bit cell stays readable. Each row is announced to screen readers as one description
- **Notation Equivalents card**: CIDR, dot-decimal mask, wildcard mask, hex mask (`0xFFFFFF00`), and binary mask
- **Address Properties card**: IP class (A/B/C/D/E) and scope badge. The prefix length and host-bit count are shown once, in the binary breakdown legend. Each info icon explains its own field
- **Is this address in the subnet?** card: type an IPv4 address to check it against the calculated subnet. The answer is yes or no, with the reason (usable host, network address, broadcast address, or the subnet range when it is outside)
- **Split into equal subnets** card: divide the subnet either into a new prefix (for example `/26`) or into a number of subnets. A count is rounded up to a power of two, and the result says so (3 requested gives 4). Lists the networks with their first and last addresses, up to 256 rows; a larger split shows the first 256 and the total
- **Copy** and **Share** sit together directly under the result and put a plain-text summary of the calculated subnet on the clipboard or into the share sheet. The CIDR appears once, as the result heading; the usable host count is shown once, under it
- Quick example chips for common subnets (`/8`, `/12`, `/16`, `/24`, `/30`, `/0`)
- Network alignment warning when the entered IP is not on a network boundary, showing the corrected network address
- Entered fields survive rotation and process death. Leaving the tool with Back keeps the fields and the last calculated result for the app session (in memory only)

### Speed Test
Internet connection speed test measuring latency, download, and upload throughput.
- Three-phase sequence: **latency** (10 round-trip probes), **download**, then **upload** — each streamed live as it runs
- Animated phase stepper, live circular speed gauge, and a live throughput-over-time chart per phase
- Start screen: download and upload stream counts are two equal buttons (1–8 and 1–4) that open a list of values
- Final results: latency min/avg/max/jitter, download/upload avg & peak Mbps, and total data transferred, each with its own throughput chart. Chart axes use round ticks (for example 0, 10, 20, 30 Mbps)
- Upload is time-boxed to the phase window and counts the bytes actually written, so a slow or briefly stalled uplink still produces a result; a stalled POST is retried on a new connection until the window ends
- A stalled latency probe under load is skipped rather than failing the run
- Failure keeps the latency and download results already measured, names the phase that failed, and offers Run again. The error text is a plain-language reason, not raw exception text
- Cancel returns to the start screen with a "Test canceled" notice and keeps any finished latency and download results. There is one Start button
- The CIDR entry is checked as you type: an invalid entry shows an inline message and Calculate stays disabled, so there is no Retry for an input error
- Copy and Share sit together at the foot of the results and export the same plain-text summary; Run again sits below them
- **Cloudflare attribution** — shown once, in the disclaimer card at the foot of the screen. Measurement traffic is sent to and timed against `speed.cloudflare.com` (`/__down` and `/__up`), the same backend that powers Cloudflare's public speed test at <https://speed.cloudflare.com>. Net Swiss Knife is an independent app and is **not affiliated with, sponsored by, or endorsed by Cloudflare, Inc.**; "Cloudflare" and the Cloudflare logo are trademarks of Cloudflare, Inc. Full attribution is also shown in-app under Settings → Data source attributions.

### Wake-on-LAN
Wake sleeping or powered-down machines on the local network with a UDP magic packet.
- Accepts `AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`, `AABB.CCDD.EEFF` and `AABBCCDDEEFF` (surrounding spaces are ignored). One notation per address: mixed separators such as `AA:BB-CC:DD:EE:FF` are rejected
- Targets must be unicast. Multicast, broadcast (`FF:FF:FF:FF:FF:FF`) and all-zero MACs are rejected
- Real-time validation with a specific inline message for each problem: wrong length, invalid characters, mixed separators, multicast, broadcast or all-zero address
- Advanced options: broadcast address (default `255.255.255.255`; any dotted-decimal IPv4 address such as a subnet-directed `192.168.1.255` is accepted; host names and IPv6 are rejected inline) and UDP port (default 9, valid 1-65535). The panel opens automatically when one of these fields is invalid
- Sends 3 duplicate packets per request for reliability over lossy UDP
- The MAC, broadcast address and port survive rotation and process death. Leaving the tool with Back keeps them, the saved-device name being typed, and the last finished send (Success or Error) for the app session, in memory only; a send still in progress is not restored as running
- The success title names the saved device when the send came from one, and a repeated send from the same screen shows how many packets have been sent
- Success card reports the send and states that Wake-on-LAN is fire-and-forget: no delivery confirmation is returned. It confirms target MAC, broadcast address, port and packet count
- Errors are specific: no local Wi-Fi or Ethernet network, a local-network permission denial, an invalid broadcast address, or a general send failure. Each has a Retry action
- Saved devices (stored on this device only) sit in the same card as the MAC form, so entering, sending and saving use one flow. Save the current MAC under an optional name, use a saved device to fill the form, edit a saved device's name or MAC, and delete it (deleting asks for confirmation). A MAC is stored once, whichever notation it was entered in; an edit that would make two entries share a MAC is refused
- Each saved device stores the broadcast address and port it was saved with, so Wake sends to it the same way every time. Entries saved before this setting existed use the defaults. Use fills the form with the device's MAC, broadcast address, port and name without sending; Wake sends to it in one tap; Edit and Delete work as before. A device with a non-default target shows that target under its name. Saving is refused while the MAC, broadcast address or port is invalid
- Send again on the success or error card repeats the last send with the same form values. Changing the MAC, broadcast address or port clears a finished result so it is never shown for a different target. Retyping the same MAC in another notation keeps it
- Saving a MAC that is already saved never silently renames it. With the name field left blank the saved name is kept; with a new name the device is renamed. Either way a note says what happened (for example "Already saved as QA dummy. Renamed to dup.")
- In-app help explains BIOS/OS requirements
- After a send, Copy and Share put a plain-text report on the clipboard or in the share sheet: target MAC, broadcast address, UDP port, packets sent, and the time the text was generated. The report states that delivery is not confirmed

### Network Info
Shows the device's active network, its IP configuration, and an optional public IP check. Read-only and local, except for the public IP check you start yourself.
- Active network: transports (Wi-Fi, cellular, Ethernet, VPN, Bluetooth), and whether the network is metered or roaming
- Wi-Fi link (Wi-Fi only): signal strength (dBm), link speed (Mbps), and frequency with band and channel. The network name is shown only when location permission has already been granted for the Wi-Fi Scanner; otherwise it reads "Unavailable". This tool requests no new permission
- IP configuration: when more than three addresses are listed, the rest sit behind a **Show all N addresses** toggle; every address on the active interface, addresses in use first and deprecated ones (for example expired IPv6 temporary addresses) after them with a **Deprecated** label, with scope (private, shared, link-local, unique local, global, multicast, or special-purpose), prefix length, and the IPv4 subnet mask. IPv6 shows the prefix length only. Also gateways (default routes), DNS servers, MTU, search domains, and proxy settings
- MAC address: shown for Wi-Fi and Ethernet only when Android provides it. Android 10 and newer hide hardware addresses from apps, so the value is shown as unavailable there rather than a placeholder. Locally administered (randomized) addresses are labelled
- Public IP: runs only when you tap Check public IP, never on screen entry or refresh. It requests `api4.ipify.org` (IPv4) and `api6.ipify.org` (IPv6) over HTTPS. A network without IPv6 reports "No public IPv6 address" rather than an error. Each address found is then looked up for reverse DNS and approximate location and ASN. Reverse DNS always has a row and reads "None found" when the address has no name. When IPv4 and IPv6 share the same location and ASN they are shown once, below both addresses. Results are cleared when the network changes
- Re-read local network re-reads the local network and confirms it with a short message. The screen also updates when the default network changes or its addresses or routes change
- Copy any value with its copy icon or by pressing and holding its row; both confirm with "Copied to clipboard". Copy and Share at the bottom produce a plain-text summary of every detail, as the hint under them says

### MAC Lookup
Identifies the vendor behind a MAC address or an OUI prefix, using the IEEE registry bundled with the app. Fully offline: nothing is sent to any service.
- Input: paste one or many, one per line or separated by commas, semicolons, or spaces. Colon, hyphen, Cisco dotted, and bare hex are accepted in any letter case. A three-byte OUI prefix such as `B8-27-EB` is looked up on its own. Multicast and broadcast addresses are accepted and labelled as such
- The result count in the header is the number of entries that were recognised and are listed. Entries that are not a MAC address or OUI prefix are counted separately as not recognised, and the input message names each rejected entry
- Per address: the normalized address in colon form as the card title with its own copy button, then hyphen, Cisco dotted, and bare hex (upper or lower case, each with a copy button); the vendor name, or "Unknown vendor" when the registry has no block for it; the block size (MA-L, MA-M, or MA-S) when the registry lists it; the type (unicast, multicast, or broadcast); and whether the address is locally administered
- Locally administered (randomized or private) addresses are labelled, and no vendor is shown for them, because the vendor code does not identify the hardware. Unicast addresses only: the locally administered flag reads "Not applicable" for broadcast and multicast, because the bit is not an owner's assignment for a group address. Broadcast addresses show no vendor
- The same address entered in several notations (for example `00:1A:2B:3C:4D:5E` and `001a2b3c4d5e`) is one card, marked "Entered N times, merged into one card". Copy all and Share carry the same count
- Malformed entries stay in the results with the reason they were rejected. A paste with no valid entry shows an error
- Paste from clipboard fills the field and looks it up at once. Recent lookups are kept on this device (the last five), and each can be removed or all cleared
- Look up, Paste and choosing a recent lookup close the keyboard, and the results are scrolled into view. Edit input moves focus back to the input field above the results. Changing the lookup text clears the shown results; changing only case or surrounding spaces keeps them. Clear results removes the results and keeps the input
- Copy and Share omit the `Input` line when the pasted text is already the normalized address; it is kept when it is written differently (for example with hyphens)
- Copy all and Share produce a plain-text summary of every entry. The first 100 entries of a paste are analysed; the rest are reported as not looked up

### Layout, keyboard and actions

- **Keyboard**: the focused field scrolls into view above the on-screen keyboard, with its supporting and error text. The keyboard does not cover the field or the Recent row on any tool.
- **Short landscape screens** (under 480dp high, such as a phone in landscape): the Home and tool hero headers collapse to a compact header, so the first tools or the tool's input start above the fold. Taller windows keep the full hero. Tablets keep the 640dp content cap.
- **Home**: tools are grouped under Diagnostics, Wi-Fi & LAN, Security and Utilities, each section laid out in rows of equal-height tiles (two columns on a phone, three on a wide tablet).
- **Retry**: every error card uses the same filled Retry button with a refresh icon.
- **Result tables**: cipher suite names and HTTP security header names wrap at `_` and `-`, not mid-word. Exported text is unchanged.
- **Ping**: when a host is typed but not yet run, the idle card says "Ready to ping" instead of asking for a host.

## Networking behavior

LAN-directed sockets use the selected Wi-Fi or Ethernet network when the destination is within that network's subnet. This lets local discovery and device queries reach the LAN when a VPN is active; destinations outside the local subnet continue to use Android's normal route. mDNS joins the selected interface and can use an ephemeral query port when port 5353 is unavailable. mDNS requires a Wi-Fi or Ethernet selection: cellular or VPN-only connectivity cannot carry local multicast, so the scan reports that instead of failing with a raw socket error.

Tool screens show a status banner when internet access or a local Wi-Fi/Ethernet network is unavailable. LAN tools also explain when a VPN is active. On Android 16 (API 36) and newer, Android's Local Network Protections can require `NEARBY_WIFI_DEVICES` access before local-network operations. If access is denied, the tool shows a Grant action; grant access and retry the operation explicitly.

Settings, pinned tools, and recent targets are stored in app-private preferences. Android cloud backup and device transfer exclude this preference file; clear recent targets from Settings when you want to remove them sooner.

Tool defaults in Settings move in steps every tool that reads them accepts, with the current value and both ends shown: ping count 1, 3, 5, 10, 20, 50 or 100; timeout 1, 2, 3, 5 or 10 s (the same choices as Ping's timeout chips); concurrency 1, 2, 5, 10, 25, 50, 100, 250 or 500 (the Port Scanner steps). Settings → About links to the [privacy policy](PRIVACY_POLICY.md).

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
