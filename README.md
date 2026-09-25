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
- Configurable probe count (1–50 via slider; default configurable up to 100 in Settings) and timeout (100–30,000 ms)
- Advanced payload size (0–1,472 bytes), TTL (1–255), and interval (100–10,000 ms) controls
- Per-probe RTT reporting with sequence numbers and status (SUCCESS / TIMEOUT / UNREACHABLE / ERROR), including reply IP, TTL, and bytes when available
- Live stats panel during active ping: packet loss %, min, avg, and max RTT updating after every packet
- RTT chart with Y-axis ms labels and fill gradient, rendered as results arrive
- **Continuous mode** — toggle replaces the count slider; pings indefinitely while the app is on screen, screen kept on automatically, stops when backgrounded or screen locked
  - Rolling window of the last 100 packets drives live stats and chart
  - Full session log streamed to a temp CSV file; shareable via the Share button on completion
- Tools with recent-target support save up to five entries on-device and show quick-select chips; clear recent targets from Settings
- CSV exports retain the original columns and append reply TTL and payload bytes.

### Traceroute
Network path analysis with per-hop geolocation enrichment.
- Configurable max hops (1–64), timeout (500–30,000 ms), and probes per hop (1–5)
- Dual protocol support: ICMP and UDP
- Automatic MTU discovery or custom packet size (28–1,472 bytes)
- Hops show IP address, per-probe RTTs, and min/average/max when multiple probes are requested; reverse DNS and geolocation fill in asynchronously when available

### Port Scanner
TCP port reachability scanning with service identification.
- 7 preset port groups: Common Services, Well-Known (1–1024), Web, Databases, Mail, Remote Access, and Custom range (up to 10,000 ports)
- Concurrent scanning (1–500 simultaneous probes), per-port timeout (100–30,000 ms)
- Service name resolution and banner grabbing for open ports

### LAN Scanner
Local network device discovery across IPv4 subnets.
- CIDR subnet scanning (/16–/30) with automatic current-subnet detection
- Multi-method presence detection: successful ICMP, completed TCP connections, and correlated NetBIOS NBSTAT or mDNS replies; each confirmed host shows the method(s) that found it
- Failed connects, timeouts, unreachable routes, and policy failures remain opt-in diagnostics and never inflate the confirmed device count or confirmed-host exports
- Per-host details: IP, hostname, MAC address, OUI vendor name, open ports, RTT when ICMP answers, and gateway flag from the active default route
- MAC resolution is best-effort: Android 10+ restricts the ARP source, so the UI explains when a MAC cannot be read
- Concurrent host probes (1–500) with real-time progress streaming, post-probe ARP enrichment, and final summary
- Search results by IP, hostname, or vendor; filter by gateway or hosts with open ports
- Expanded hosts include a direct **Scan ports** hand-off; the OUI registry contains 50,000+ prefixes and can be refreshed with `python3 tools/oui/update_oui.py`

### DNS Lookup
Full DNS record resolution with multiple resolver options and protocol-level response details.
- 10 record types: A, AAAA, MX, TXT, CNAME, NS, SOA, PTR, SRV, CAA
- Resolver options: system default, Google (8.8.8.8), Cloudflare (1.1.1.1), OpenDNS (208.67.222.222), Quad9 (9.9.9.9), or a custom server
- PTR queries auto-reverse IPv4 addresses to `.in-addr.arpa` and IPv6 to `.ip6.arpa` form — just enter the IP
- Returns each record's actual RR type and section, RCODE, AA/AD/TC/RD/RA flags, query time, server actually used, and raw DNS response
- System DNS is never silently replaced with Cloudflare; when Android reports no resolver, the UI explains the failure and offers an explicit Cloudflare fallback. Private DNS status is shown when available.
- IDN hostnames and trailing-dot FQDNs are normalized consistently across network tools.

### Wi-Fi Scanner
Wi-Fi environment analysis with SSID grouping and spectrum visualisation.
- Access points grouped by SSID + security; mesh/dual-band routers appear as one entry with a per-BSSID drill-down
- **Spectrum Analyser** — frequency-domain triangle chart per AP (X = MHz, Y = RSSI −100 to −30 dBm) with SSID labels and channel gridlines; each network gets a stable accent colour derived from its SSID hash
- Band tab-row switches between detected 2.4 / 5 / 6 GHz bands; spectrum and network list update per band
- Best-channel callout recommends the least-congested channel (1, 6, or 11) when 2.4 GHz is active
- Expandable network cards show each BSSID with channel, width, RSSI, and vendor; tap any BSSID for full detail sheet
- Detail sheet: signal arc gauge, band/channel/width/standard/speed, security capability tokens, live connection stats (IP, TX/RX speed) when connected
- Each refresh requests a platform scan; auto-refresh is configurable (Off / 15 / 30 / 60 seconds, default 30 seconds). Android throttles foreground scans to four per two minutes, so the screen shows result age and when a request was throttled; Location Services must be enabled.

### TLS Inspector
SSL/TLS certificate analysis for any TCP host, without sending an HTTP request.
- Configurable host, port (default 443), and timeout (500–30 000 ms)
- Presented peer chain: subject/issuer, validity dates, SANs, serial number, signature and public-key details, and SHA-256 fingerprint per certificate
- Checks device trust, hostname match, expiry/not-yet-valid dates, incomplete chains, self-signed certificates, weak signatures, and weak keys
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
- Configurable max hops (1–10), timeout, and SNMP v3 auth/priv protocols (MD5, SHA-1/256/512; DES, AES-128/192/256)
- Reuses one SNMP4J session per discovery and closes it on completion or cancellation
- LLDP neighbour addresses are decoded from the LLDP management-address index; CDP accepts dotted and hex-octet cache addresses

### WHOIS Lookup
Domain, IP, and ASN registration lookup using RDAP with WHOIS fallback.
- Supports domain names, IPv4, IPv6, and ASN queries
- Protocol selector: Auto tries RDAP first and falls back to WHOIS; RDAP and WHOIS modes use only the selected protocol
- Domain RDAP uses the [IANA DNS bootstrap](https://data.iana.org/rdap/dns.json) to choose a registry endpoint; IP and ASN requests use the [rdap.org](https://rdap.org/) redirector
- WHOIS fallback uses the IANA-to-registry-to-registrar referral chain for domains and ARIN/referring RIRs for IPs and ASNs
- Parsed fields include registrar, registration/expiry/update dates, name servers, status codes, DNSSEC, registrant organization/country, and network ranges
- Human-readable status code labels (e.g. "clientTransferProhibited" → "Transfer Locked")
- Live relay-chain visualiser shows the RDAP or WHOIS servers used; raw responses are available for inspection

### HTTP Probe
Full HTTP/HTTPS request tester with security header analysis.
- Supports GET, POST, PUT, PATCH, DELETE, HEAD, and OPTIONS methods
- Custom request headers: add/remove key-value pairs dynamically
- Request body editor for POST, PUT, and PATCH
- Follow-redirects toggle with each hop's status and URL; HTTPS-to-HTTP redirects are blocked before contacting the destination
- Cross-origin redirects that would resend a request body require approval for that redirect
- Response display across four tabs:
  - **Overview**: status code (color-coded 2xx/3xx/4xx/5xx), response time, negotiated HTTP protocol, final URL, redirect hops, body size, Content-Type
  - **Headers**: collapsible request and response header sections
  - **Body**: scrollable monospace response body with copy-to-clipboard; truncated at 512 KB with notice
  - **Security**: per-header pass/warn/fail ratings for HSTS, CSP, X-Frame-Options, X-Content-Type-Options, Referrer-Policy, Permissions-Policy, Cross-Origin-Opener-Policy, Cross-Origin-Embedder-Policy, and Server header information disclosure

### mDNS Service Browser
LAN service discovery via multicast DNS (RFC 6762 / DNS-SD RFC 6763).
- Discovers all advertised services on the local network without prior knowledge of service types
- Two-phase discovery: enumerates service types via `_services._dns-sd._udp.local.`, then instances per type, then SRV/TXT/A/AAAA records per instance
- Supported service examples: `_http._tcp`, `_airplay._tcp`, `_ipp._tcp`, `_homekit._tcp`, `_spotify-connect._tcp`, and any other DNS-SD service
- Per-service details: display name, hostname, port, IP addresses (IPv4 and IPv6), and TXT record key-value pairs
- Live streaming results: services appear as they are discovered, grouped by service type with animated list entries
- Expandable service items show full TXT record details on tap
- Configurable scan duration (8-second window); scan can be stopped at any time
- Requires Wi-Fi multicast lock (`CHANGE_WIFI_MULTICAST_STATE`) for reliable reception on Android

### Subnet Calculator
IPv4 subnet calculator with visual binary breakdown and multi-notation conversion.
- Two input modes: **CIDR / Mask** and **IP Range** (finds the tightest subnet covering a given min–max IP pair)
- CIDR mode accepts CIDR (`192.168.1.0/24`), dot-decimal mask (`192.168.1.0/255.255.255.0`), space-separated mask, or bare IP (assumes `/32`)
- Computes: network address, broadcast, first/last usable host, total and usable host counts
- **Binary Breakdown card**: colour-coded bit grid distinguishing network bits (blue) from host bits (orange) for IP address, subnet mask, and network address rows
- **Notation Equivalents card**: CIDR, dot-decimal mask, wildcard mask, hex mask (`0xFFFFFF00`), and binary mask
- **Address Properties card**: IP class (A/B/C/D/E), private/public scope badge (RFC 1918 + loopback + link-local), prefix and host bit counts
- Quick example chips for common subnets (`/8`, `/12`, `/16`, `/24`, `/30`, `/0`)
- Network alignment warning when the entered IP is not on a network boundary, showing the corrected network address

### Speed Test
Internet connection speed test measuring latency, download, and upload throughput.
- Three-phase sequence: **latency** (10 round-trip probes), **download**, then **upload** — each streamed live as it runs
- Animated phase stepper, live circular speed gauge, and a live throughput-over-time chart per phase
- Final results: latency min/avg/max/jitter, download/upload average & peak Mbps, and total data transferred, each with its own throughput chart
- Share button exports a plain-text summary of the results
- **Powered by Cloudflare** — measurement traffic is sent to and timed against `speed.cloudflare.com` (`/__down` and `/__up`), the same backend that powers Cloudflare's public speed test at <https://speed.cloudflare.com>. Net Swiss Knife is an independent app and is **not affiliated with, sponsored by, or endorsed by Cloudflare, Inc.**; "Cloudflare" and the Cloudflare logo are trademarks of Cloudflare, Inc. Full attribution is also shown in-app under Settings → Data Source Attributions.

### Wake-on-LAN
Wake sleeping or powered-down machines on the local network with a UDP magic packet.
- Accepts all common MAC notations: `AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`, `AABB.CCDD.EEFF`, and bare `AABBCCDDEEFF`
- Real-time MAC validation with inline error feedback
- Advanced options: custom broadcast address (default `255.255.255.255`) and UDP port (default 9)
- Sends 3 duplicate packets per request for reliability over lossy UDP
- Success card confirms target MAC, broadcast address, port, and packet count; in-app help explains BIOS/OS requirements

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

---

## CI & Automation

### Standard CI (`ci.yml`)
Runs on every push to `main` and every PR targeting `main`:
1. Sets up JDK 21 (Temurin)
2. Caches Gradle
3. Runs `./gradlew test`
4. Runs `./gradlew :app:assembleDebug`

### Release (`release.yml`)
Triggered by a new `vYYYY.MM.DD.N` tag (where `N` is 1–99) or manual dispatch. Manual dispatch allocates the next unused daily suffix and reserves the immutable tag before building. The version code is `YYYYMMDD × 100 + N`. The workflow signs and publishes the release APK and AAB to GitHub Releases.

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
