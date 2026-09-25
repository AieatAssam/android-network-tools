# Privacy Policy - Net Swiss Knife

**Effective date:** 2026-09-25

---

## Overview

Net Swiss Knife provides 14 network tools: Ping, Traceroute, Port Scanner, LAN Scanner, DNS Lookup, Wi-Fi Scanner, Network Topology, TLS Inspector, WHOIS Lookup, HTTP Probe, Subnet Calculator, mDNS Browser, Speed Test, and Wake-on-LAN. This policy explains what data the app uses, where it is sent, and how it is handled.

---

## Data We Collect

### Data You Provide

When you use a diagnostic tool you enter targets such as hostnames, IP addresses, domain names, URLs, MAC addresses, or subnet ranges. The app uses them to run the requested operation. Most target and result data stays in memory, but tools with recent selections keep up to five recent targets per tool in private on-device preferences so you can select them again. You can remove recent entries in a tool or clear recent targets in Settings; uninstalling the app or clearing its storage also removes them. HTTP Probe stores only a credential-free origin for its recent list, not the URL path, query, or fragment.

### Data the App Generates

| Data | Where it lives | How long |
|------|----------------|----------|
| Diagnostic results (ping statistics, open ports, DNS records, Wi-Fi channel info, etc.) | Device RAM only | Until the screen is reset or the app is closed |
| Recent target entries (up to five for each tool with recent selections) | App-private on-device preferences | Until removed in the tool, cleared in Settings, or app storage is removed |
| Settings and pinned tools | App-private on-device preferences | Until changed, app storage is cleared, or the app is uninstalled |
| Debug log | Android Logcat and app-private files in debug builds only | Android manages Logcat retention; the Debug Logs screen clears the files, which otherwise remain until app storage is cleared or the app is uninstalled |

Settings and recent targets are excluded from Android cloud backup and device transfer. The Debug Logs screen and file logging are available only in debug builds, not the Play build. Debug messages are also written to Android Logcat; clearing the in-app log deletes the files but not Logcat, whose retention is managed by Android.

---

## External Services

The app has no Net Swiss Knife account, analytics, or diagnostic-result server. It sends requests to the destinations required by the tool you choose; those destinations may receive your public IP address and ordinary connection metadata such as request time. Settings includes a link to the [public GitHub repository](https://github.com/AieatAssam/android-network-tools) and a Cloudflare attribution link; those sites are opened only if you tap the links.

### DNS, local-network tools, and Wi-Fi Scanner

DNS Lookup sends the queried name or IP address to the selected resolver. This may be Android's configured system resolver or the public/custom resolver you choose (Google `8.8.8.8`, Cloudflare `1.1.1.1`, OpenDNS `208.67.222.222`, Quad9 `9.9.9.9`, or a custom server). The resolver can see the query and your network address. When Android's system resolver is selected, Android's configured Private DNS behavior may also apply.

Ping, Traceroute, Port Scanner, and TLS Inspector contact the target you enter. LAN Scanner sends local ICMP/TCP probes and may send NetBIOS name queries (UDP/137) or mDNS multicast queries (UDP/5353). mDNS Browser sends multicast DNS service-discovery queries. Network Topology sends SNMP requests to seed devices you select and may discover neighboring devices. Wake-on-LAN sends a UDP magic packet to the selected broadcast address. These operations are addressed to your chosen destination or local network; their results are shown in the app and are not uploaded to a Net Swiss Knife server. Subnet Calculator performs its calculations locally.

Wi-Fi Scanner reads nearby access-point and connection details through Android's Wi-Fi APIs. Android requires location-related permissions for scanning, but the app does not determine or transmit your physical location. Scan results remain on the device.

### TLS Inspector and HTTP Probe

TLS Inspector opens a TLS connection to the host and port you enter to retrieve its certificate chain and connection details. It does not send an HTTP request. The target host receives the connection and TLS handshake, including normal connection metadata such as your IP address and timestamp.

HTTP Probe sends the URL, selected method, custom headers, and any request body you enter to that URL. Redirects are followed only when the option is enabled; an HTTPS-to-HTTP redirect is blocked before the HTTP destination is contacted. Before a cross-origin redirect resends a request body, the app asks you to approve that specific destination. Only enter credentials or other sensitive data when you intend to send them to the target.

### WHOIS and RDAP

Auto mode tries RDAP first. For domain lookups, the app may fetch the [IANA RDAP DNS bootstrap document](https://data.iana.org/rdap/dns.json), then sends the registrable domain to the selected registry RDAP service. For IP address and ASN lookups, it sends the query to the [rdap.org](https://rdap.org/) redirector, which directs the client to a registry service. The app prefers HTTPS RDAP endpoints when they are advertised. If RDAP cannot provide a result in Auto mode, it can query WHOIS servers; domain lookups may follow IANA, registry, and registrar referrals, while IP/ASN lookups may query ARIN and a referred regional registry. WHOIS may contact `whois.iana.org`, `whois.arin.net`, and TLD/registrar servers, including `whois.verisign-grs.com`, `whois.pir.org`, `whois.nic.io`, `whois.nic.uk`, `whois.denic.de`, `whois.nic.fr`, `whois.educause.edu`, and `whois.nic.google`. The classic WHOIS protocol uses TCP port 43 and does not encrypt the query.

These services receive the domain, IP address, or ASN needed to answer the lookup and ordinary network metadata such as your IP address and request time. RDAP and WHOIS requests may reach IANA, `rdap.org`, registry services, and, for WHOIS referrals, registrar WHOIS servers; these services are operated by their respective organizations, not by Net Swiss Knife. Their own logging and data-handling practices apply. The protocol selector can restrict a lookup to RDAP or WHOIS instead of Auto mode.

### Cloudflare Speed Test

When you run Speed Test, the app contacts Cloudflare's public speed test service at `speed.cloudflare.com` to measure latency, download throughput, and upload throughput. The app sends and receives synthetic measurement traffic for this purpose. It does not upload your entered hostnames, IP addresses, DNS results, Wi-Fi details, scan results, or other user-generated diagnostic data to Cloudflare. The upload phase uses randomly generated bytes, not your files, messages, or other personal content.

Cloudflare may receive the network and request metadata normally associated with an HTTPS request, such as your IP address and timestamp. Cloudflare's handling of those requests is described in [Cloudflare's privacy policy](https://www.cloudflare.com/privacypolicy/).

### Traceroute geolocation

- **What is sent:** Traceroute IPv4 hop addresses that pass the app's local filter may be sent to `ipinfo.io` to retrieve approximate country, city, ISP, and ASN data for display. This can include special-purpose addresses that the filter does not recognize.
- **What is filtered:** The current precheck skips RFC 1918 private, loopback, link-local, 0/8, and 240/4 IPv4 ranges. IPv6 hops are not sent for geolocation by the current implementation.
- **Caching:** Results are cached in memory to reduce repeated requests.
- **Scope:** This only occurs when you explicitly run the Traceroute tool.

We do not control the geolocation service's own data-handling practices. Traceroute hop addresses describe network routing and can reveal part of your network path.

---

## Permissions

| Permission | Why it is needed |
|------------|-----------------|
| `INTERNET` | Required to perform all network diagnostic operations |
| `ACCESS_NETWORK_STATE` | Check whether a network connection is available before running a tool |
| `ACCESS_WIFI_STATE` | Read Wi-Fi network information for the Wi-Fi Scanner |
| `CHANGE_WIFI_STATE` | Required by Android to initiate Wi-Fi scans |
| `NEARBY_WIFI_DEVICES` *(Android 13+)* | Access nearby Wi-Fi devices while the Wi-Fi Scanner requests a platform scan; on Android 16+ it may also be required for local-network operations when Local Network Protections are enforced |
| `ACCESS_FINE_LOCATION` | Required by Android's `startScan()` and `getScanResults()` APIs for Wi-Fi scanning |
| `ACCESS_COARSE_LOCATION` | Declared for Android compatibility; the scanner requests fine location at runtime |

Location permissions and the Location Services toggle are used exclusively to satisfy Android's platform requirements for Wi-Fi scanning. The app does not determine, record, or transmit your physical location. On Android 13 and newer, the scanner requests both fine location and Nearby Wi-Fi access because the scan APIs still require fine location. The `NEARBY_WIFI_DEVICES` permission can additionally gate local-network sockets on Android 16 and newer when Local Network Protections are enabled; it is used to let the selected tool communicate with local-network devices.

---

## Google Play

When you download or purchase the app through Google Play, Google collects information as described in their own policies. This includes installation data, crash reports submitted through the Play Store, and any purchase data. That data is governed by Google's terms and is separate from what Net Swiss Knife itself collects.

Google Play may collect crash and ANR (Application Not Responding) reports on our behalf to help identify stability issues. Google processes those reports under its own policies; Net Swiss Knife does not operate that reporting service.

---

## Data Sharing

We do not sell your data. When you use a tool, the target and request details are sent to the destination or supporting public service needed to perform that operation, as described above. These services can receive ordinary network metadata such as your IP address and request time. In particular:

- Synthetic measurement traffic and ordinary HTTPS request metadata are sent to Cloudflare only when you run Speed Test.
- DNS queries are sent to the resolver selected in DNS Lookup. RDAP/WHOIS queries are sent to IANA, `rdap.org`, registries, and any WHOIS referral servers needed to answer the query.
- Target and request details are sent to the destinations selected or entered for network tools. IPv4 addresses found on a traceroute path that pass the app's local filter may be sent to `ipinfo.io` when you run Traceroute.
- Crash and stability reports may be collected by the Google Play platform as described above.

---

## Data Retention

- **In-app results:** Held in memory only; discarded when you reset a tool screen or close the app.
- **Recent targets and settings:** Stored in app-private preferences until you remove or change them, clear app storage, or uninstall the app. Android cloud backup and device transfer exclude this preferences file.
- **Debug logs:** Debug builds write log messages to Android Logcat and a private app file. The Debug Logs screen clears the files, not Logcat; Android manages Logcat retention. The screen and file logging are not available in the Play build.
- **No account, no server-side storage:** We do not operate any servers that store user data.

---

## Children

This app is not directed at children under the age of 13 and does not knowingly collect personal information from children.

---

## Changes to This Policy

If we materially change this policy, we will update the effective date at the top of this file and release a new app version containing the updated policy.

---

## Contact

If you have questions about this privacy policy, open an issue on the project repository.
