# Brag Plan — Net Swiss Knife

## 9-question rubric

1. **What is the app?**
   Net Swiss Knife — an Android app that bundles 14 professional-grade network
   diagnostic tools (Ping, Traceroute, Port Scanner, LAN Scanner, DNS Lookup,
   Wi-Fi Scanner with spectrum analysis, TLS Inspector, SNMP Topology
   Discovery, WHOIS, HTTP Probe, mDNS Browser, Subnet Calculator, Speed Test,
   Wake-on-LAN) into one clean, modern Jetpack Compose / Material 3 app.

2. **The funniest/most impressive claim.**
   The app's own onboarding copy, verbatim, screenshotted live from the
   emulator: *"No accounts, no ads, and nothing leaves your phone unless a
   tool explicitly sends it somewhere, like a DNS or WHOIS query."* That's an
   unusually direct, confident claim for a free utility app — it earns the
   reaction. Backed by the core numeric claim: **14 tools, one app.**

3. **The visual hook.**
   Subnet Calculator's colour-coded binary bit-grid (blue = network bits,
   pink = host bits) — nothing else in this genre of app looks like this. Close
   second: Wi-Fi Scanner's pink/purple gradient spectrum-analyser triangle
   chart.

4. **What should be shown from the actual UI.**
   All real emulator screenshots (see Assets below), not recreated mockups:
   the Home tool grid, the More sheet's categorized tool list, the Wi-Fi
   spectrum chart, the Subnet Calculator's binary breakdown, and a **live**
   ping to 8.8.8.8 with real RTT numbers and the gradient RTT chart.

5. **Shortest satisfying video.**
   20 seconds. Hook, grid reveal, three real tool highlights, punchline — no
   room or need for more.

6. **Tone.**
   Preset: **app-store** (smooth, feature-card clean, corporate but not
   boring). Creative direction: *"snap open a new tool every beat"* — lean
   into the Swiss Army knife naming with a quick, confident, single-blade-per-
   beat rhythm rather than a joke framing (this is an earnest, deep utility,
   not an absurd project).

7. **Audio.**
   Clean, minimal-tech music bed (subtle electronic pulse, no cheese), with
   crisp UI tap/whoosh SFX synced to each card/chart reveal. No voice
   narration (not requested).

8. **Share caption.**
   "14 professional network tools. Zero accounts, zero ads. Net Swiss Knife —
   ping, Wi-Fi spectrum analysis, subnet math, and more, right in your
   pocket."

9. **User flow worth showing.**
   Entry → key action → result, three times over: open Wi-Fi Scanner → see
   the live spectrum chart render; open Subnet Calculator → compute a CIDR →
   see the binary breakdown; open Ping → send 10 real packets to 8.8.8.8 →
   see the live RTT chart. Each beat is a real, working screenshot of the
   tool actually doing its job.

## Visual identity (from Color.kt / Theme.kt)

- Primary: deep green `#1B8A5E`
- Primary container (light mint): `#B3F0D8`
- Secondary teal: `#4A6359`
- Light background: `#F5FBF7`
- Dark background: `#0F1512`
- Material 3, no hardcoded gradients beyond in-app hero cards (pink/purple
  gradients appear inside specific tool screens — Wi-Fi header, Subnet
  Calculator result card — and are drawn straight from the real screenshots)

## Assets (all real emulator screenshots, `screenshots/`)

| File | What it shows |
|---|---|
| `home_grid.png` | Home screen — tool card grid, hero header, tagline |
| `more_sheet.png` | "All Tools" sheet — categorized list (Diagnostics / Wi-Fi & LAN / Security / Utilities), pin-to-nav-bar affordance |
| `wifi_spectrum.png` | Wi-Fi Scanner — pink/purple gradient header, spectrum-analyser triangle chart, best-channel callout |
| `subnet_empty.png` | Subnet Calculator — empty state, CIDR/Mask vs IP Range toggle, preset chips |
| `subnet_result.png` | Subnet Calculator — computed result summary (network/broadcast/hosts) with gradient header |
| `subnet_binary.png` | Subnet Calculator — **hero visual**: colour-coded binary bit-grid breakdown |
| `ping_empty.png` | Ping — empty state, count/timeout controls |
| `ping_live.png` | Ping — **live**, mid-run against 8.8.8.8, 0% loss, real timing |
| `ping_chart.png` | Ping — **hero visual**: completed run, gradient RTT line chart + per-packet gradient bars |
| `topology_config.png` | Network Topology — SNMP config card (supporting only; no live graph available without a real switch on this network) |

## Storyboard (target ~20s)

**1. Hook — 0:00–0:02.5 (2.5s)**
Dark background (`#0F1512`), app icon (mint network-node glyph) snaps in
with a sharp mechanical "click" SFX — like a knife blade opening. Big
wordmark punches in: **"14 tools."** beat, **"One app."** Fast, confident,
no fade.

**2. Reveal — 0:02.5–0:06.5 (4s)**
Cut to `home_grid.png`. Cards enter with the same staggered
scale/fade rhythm the real app uses (confirmed in `HomeScreen.kt`). Overlay
label: "Net Swiss Knife." Whoosh + soft tap SFX per card stagger, 3-4 taps.

**3. Highlight 1 — 0:06.5–0:10.5 (4s)**
Cut to `wifi_spectrum.png`. Slow push-in on the spectrum triangle chart.
Label: "Live Wi-Fi spectrum analysis." Subtle audio-reactive shimmer on the
chart line synced to the music bed.

**4. Highlight 2 — 0:10.5–0:14.5 (4s)**
Cut to `subnet_binary.png` (hero shot). Bit-grid cells light up left-to-right
in a quick ripple, synced to a light arpeggio/tick in the music. Label:
"Binary breakdown, decoded instantly."

**5. Highlight 3 — 0:14.5–0:18 (3.5s)**
Cut to `ping_live.png` → `ping_chart.png` (quick two-shot). Label: "Real
diagnostics. Real numbers." RTT chart line draws on with a whip-pan/wipe.

**6. Punchline / outro — 0:18–0:20.5 (2.5s)**
Cut to dark background. Wordmark + tagline settle: **"Net Swiss Knife —
No accounts. No ads. Just tools."** Final logo hit SFX, hold half a beat,
end.

## Music cue guidance

Clean minimal-tech/electronic bed, moderate tempo (not aggressive). Cue
music hits to: icon-snap (0:00), first card stagger (0:03), bit-grid ripple
(0:11), RTT chart draw-on (0:15.5), final logo hit (0:19.5). Story/pacing
stays primary; cues are timing guidance only, detected/aligned at
composition time per `references/audio.md`.
