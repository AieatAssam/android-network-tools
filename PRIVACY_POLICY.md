# Privacy Policy – Net Swiss Knife

**Effective date:** 2026-03-28

---

## Overview

Net Swiss Knife is a collection of local network diagnostic tools (Ping, Traceroute, Port Scanner, LAN Scanner, DNS Lookup, and Wi-Fi Scanner). This policy explains what data the app uses, why, and how it is handled.

---

## Data We Collect

### Data You Provide

When you use a diagnostic tool you enter targets such as hostnames, IP addresses, domain names, or subnet ranges. These values are used solely to run the requested network operation and are never stored after you close or reset the screen.

### Data the App Generates

| Data | Where it lives | How long |
|------|----------------|----------|
| Diagnostic results (ping statistics, open ports, DNS records, Wi-Fi channel info, etc.) | Device RAM only | Until the screen is reset or the app is closed |
| Debug log file | Private app storage on your device (`debug.log`, max 512 KB with one backup) | Until you clear it or uninstall the app |

The debug log records app events and errors with timestamps. It is used for diagnosing app problems. It never leaves your device automatically and is never transmitted to us or any third party.

You can view the full contents of the debug log at any time via the in-app **Debug Logs** screen. You can also delete it from the same screen.

---

## External Services

The app makes network requests only on your behalf to run the tools. The single exception is geolocation enrichment during **Traceroute**:

- **What is sent:** Each public IP address discovered at a traceroute hop is sent to a third-party geolocation service to retrieve approximate country, city, ISP, and ASN data for display on screen.
- **What is not sent:** Private and reserved IP address ranges (e.g. 192.168.x.x, 10.x.x.x, 172.16–31.x.x, 127.x.x.x) are filtered out and never sent.
- **Caching:** Results are cached in memory for the duration of the traceroute session to reduce the number of requests made.
- **Scope:** This only occurs when you explicitly run the Traceroute tool. No other tool sends data to any external service.

We do not control the geolocation service's own data-handling practices. The IP addresses of traceroute hops are public routing infrastructure addresses, not your personal IP address.

---

## Permissions

| Permission | Why it is needed |
|------------|-----------------|
| `INTERNET` | Required to perform all network diagnostic operations |
| `ACCESS_NETWORK_STATE` | Check whether a network connection is available before running a tool |
| `ACCESS_WIFI_STATE` | Read Wi-Fi network information for the Wi-Fi Scanner |
| `CHANGE_WIFI_STATE` | Required by Android to initiate Wi-Fi scans |
| `NEARBY_WIFI_DEVICES` *(Android 13+)* | Scan for nearby Wi-Fi access points without requiring location permission |
| `ACCESS_FINE_LOCATION` *(Android 6–12)* | Required by the Android platform to scan for Wi-Fi access points on these versions |
| `ACCESS_COARSE_LOCATION` *(Android 6–12)* | Required by the Android platform alongside fine location for Wi-Fi scanning |

Location permissions are used exclusively to satisfy Android's platform requirement for Wi-Fi scanning. The app does not determine, record, or transmit your physical location.

---

## Google Play

When you download or purchase the app through Google Play, Google collects information as described in their own policies. This includes installation data, crash reports submitted through the Play Store, and any purchase data. That data is governed by Google's terms and is separate from what Net Swiss Knife itself collects.

Google Play may collect anonymized crash and ANR (Application Not Responding) reports on our behalf to help identify stability issues. This data is processed by Google and does not contain any network targets or results you entered into the app.

---

## Data Sharing

We do not sell, rent, or share your data with any third party, with the following narrow exceptions:

- IP addresses of traceroute hops sent to the geolocation service as described above.
- Crash and stability data collected by the Google Play platform as described above.

---

## Data Retention

- **In-app results:** Held in memory only; discarded when you reset a tool screen or close the app.
- **Debug log:** Stored locally on your device. You can delete it at any time from the Debug Logs screen. It is also removed when you uninstall the app.
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
