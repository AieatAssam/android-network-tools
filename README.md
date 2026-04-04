# Net Swiss Knife – Android Networking Utilities

**Net Swiss Knife** is an Android "Swiss army knife" app for network diagnostics and utilities. It provides a collection of networking tools in a clean, modern Jetpack Compose + Material 3 UI.

---

## Features

### Ping
ICMP round-trip latency measurement with real-time streaming results.
- Configurable probe count (1–100), timeout (100–30,000 ms), and packet size (1–65,507 bytes)
- Per-probe RTT reporting with sequence numbers and status (SUCCESS / TIMEOUT / ERROR)

### Traceroute
Network path analysis with per-hop geolocation enrichment.
- Configurable max hops (1–64), timeout (500–30,000 ms), and probes per hop (1–5)
- Dual protocol support: ICMP and UDP
- Automatic MTU discovery or custom packet size (28–1,472 bytes)
- Each hop shows IP address, reverse-DNS hostname, RTT, and geographic location

### Port Scanner
TCP port reachability scanning with service identification.
- 7 preset port groups: Common Services, Well-Known (1–1024), Web, Databases, Mail, Remote Access, and Custom range (up to 10,000 ports)
- Concurrent scanning (1–500 simultaneous probes), per-port timeout (100–30,000 ms)
- Service name resolution and banner grabbing for open ports

### LAN Scanner
Local network device discovery across IPv4 subnets.
- CIDR subnet scanning (/16–/30) with automatic current-subnet detection
- Per-host details: IP, reverse-DNS hostname, MAC address, OUI vendor name, open ports, RTT, gateway flag
- Concurrent host probes (1–500) with real-time progress streaming and final summary

### DNS Lookup
Full DNS record resolution with multiple resolver options.
- 10 record types: A, AAAA, MX, TXT, CNAME, NS, SOA, PTR, SRV, CAA
- Resolver options: system default, Google (8.8.8.8), Cloudflare (1.1.1.1), or custom server
- Returns resolved records, query time, and raw DNS response

### Wi-Fi Scanner
Wi-Fi environment analysis and channel optimization.
- Access point discovery with RSSI signal strength, sorted by signal
- Per-channel congestion analysis across 2.4 GHz, 5 GHz, and 6 GHz bands
- Connected network live info and best-channel recommendations (least-congested channels 1, 6, or 11 on 2.4 GHz)

### TLS Inspector
SSL/TLS certificate chain analysis for any TCP host.
- Configurable host, port (default 443), and timeout (500–30 000 ms)
- Full certificate chain: leaf, intermediates, and root
- Per-certificate: subject/issuer CN & org, validity dates, SANs, serial number, signature algorithm, public key algorithm & bit length, SHA-256 fingerprint
- Connection summary: TLS version, cipher suite, handshake time, chain trust status
- Highlights expired certificates and self-signed certs
- Works with any TCP host, not just HTTPS — does not send an HTTP request

### Network Topology Discovery
SNMP-based network topology discovery via BFS traversal.
- Seed IP discovery using SNMP v1, v2c, or v3 with configurable community string / credentials
- Neighbours discovered via LLDP (IEEE 802.1AB) and CDP (Cisco Discovery Protocol)
- Per-node data: sysDescr, sysName, sysLocation, uptime, vendor, model, firmware version
- Interface enumeration with speed, MAC address, and operational status (UP/DOWN)
- VLAN discovery via Cisco VTP MIB and IEEE 802.1Q standard MIB
- Interactive force-layout canvas with pan/zoom gestures and node detail bottom sheet
- Configurable max hops (1–10), timeout, and SNMP v3 auth/priv protocols (MD5/SHA, DES/AES128)

### WHOIS Lookup
Domain and IP registration lookup via three-hop WHOIS referral chain.
- Supports domain names, IPv4, IPv6, and ASN queries
- Three-hop chain for domains: IANA referral → TLD registry → registrar
- Two-hop chain for IPs/ASNs: ARIN → referred RIR if needed
- Static TLD fallback map for common TLDs (.com, .net, .org, .io, .co.uk, .de, .fr, .app, .dev)
- Parsed fields: registrar, registration/expiry/update dates, name servers, WHOIS status codes, DNSSEC, registrant org & country
- Human-readable status code labels (e.g. "clientTransferProhibited" → "Transfer Locked")
- Live relay-chain visualiser: animates each server node PENDING → QUERYING → DONE as the chain progresses
- Optional raw response per hop for power users

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
│       ├── release.yml             # Sign & publish release APK/AAB
│       └── claude_add_tool.yml     # Claude-driven "add tool" workflow
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
| Language | Kotlin 1.9.x | JDK 21, Kotlin DSL everywhere |
| UI | Jetpack Compose + Material 3 | Animated, high-fidelity UI |
| Navigation | Navigation Compose 2.7.x | Bottom nav + animated transitions |
| DI | Hilt 2.51.x | `@HiltViewModel`, `@AndroidEntryPoint` |
| Async | Coroutines + Flow | `viewModelScope`, `StateFlow` |
| Testing | JUnit 5 + MockK | TDD (Red → Green → Refactor) |
| Build | Gradle 8.x Kotlin DSL + Version Catalog | `gradle/libs.versions.toml` |
| Min SDK | 26 (Android 8.0) | Target SDK 34 |

---

## How to Build and Test

### Prerequisites
- JDK 21
- Android SDK (API level 34)

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

### Build release APK / AAB (requires signing secrets)
```bash
./gradlew :app:assembleRelease
./gradlew :app:bundleRelease
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

---

## CI & Automation

### Standard CI (`ci.yml`)
Runs on every push to `main` and every PR targeting `main`:
1. Sets up JDK 21 (Temurin)
2. Caches Gradle
3. Runs `./gradlew test`
4. Runs `./gradlew :app:assembleDebug`

### Release (`release.yml`)
Triggered by a `v*.*.*` tag push or manual dispatch. Signs and publishes the release APK and AAB to GitHub Releases.

Required GitHub Actions secrets:

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

### Claude "Add Tool" Workflow (`claude_add_tool.yml`)
Triggered manually via **Actions → Claude – Add Tool → Run workflow**:
1. Accepts a `tool_prompt` describing the networking tool to add.
2. Creates a date-stamped branch (`tool-YYYYMMDD-HHMMSS`).
3. Invokes Claude Code to implement the tool following TDD.
4. Runs tests and build to verify.
5. Opens a PR to `main` for review.

---

## Adding a New Tool

See [`claude/tool_instructions.md`](claude/tool_instructions.md) for the step-by-step guide.

To trigger Claude automatically, go to **GitHub Actions → Claude – Add Tool → Run workflow** and enter your tool description.
