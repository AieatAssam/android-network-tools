# Brag Plan — Net Swiss Knife (Stacking Montage variant)

## 9-question rubric

1. **What is this project?** Net Swiss Knife — a production-quality Android
   networking diagnostics app. 14 tools (Ping, Traceroute, Port Scanner, LAN
   Scanner, DNS Lookup, Wi-Fi Scanner, Network Topology Discovery, TLS
   Inspector, WHOIS Lookup, HTTP Probe, Subnet Calculator, mDNS Browser,
   Speed Test, Wake-on-LAN), all "Implemented" per the README's Available
   Tools table. Package `net.aieat.netswissknife`, MIT-licensed
   (`LICENSE`), min SDK 26, Jetpack Compose + Material 3.
2. **Who is it for?** Developers, sysadmins, home-lab tinkerers, and anyone
   who wants real network diagnostics on their phone without an account,
   a subscription, or ad trackers.
3. **What's the single most impressive/interesting thing about it?** The
   breadth — 14 distinct, fully-implemented network tools in one clean,
   consistent Compose UI, and the fact that it's completely free, ad-free,
   and open source (MIT license, verified in repo root; no analytics/ads
   SDKs anywhere in `app/build.gradle.kts` or the version catalog).
4. **What does the UI/product actually look like?** Real captured
   screenshots from the running app on a Pixel-class emulator: Home tool
   grid, Ping (live + chart), Traceroute, Port Scanner, DNS Lookup, Wi-Fi
   Spectrum, Network Topology, TLS Inspector, WHOIS, HTTP Probe, Subnet
   Calculator (binary breakdown), mDNS Browser, Speed Test, Wake-on-LAN,
   More-tools sheet. 13 of 14 tool screens represented with real UI.
5. **What's the proof/craft signal?** Every tool screen shares the same
   polished Material 3 language (ElevatedCard result panels, consistent
   typography scale, dark-mode-safe color tokens) — it reads as one
   coherent product, not fourteen bolted-together utilities.
6. **Tone.** User-specified direction: "polished contemporary" — mapped to
   the `polished` preset (serious, elegant, mixed-case, slow 0.6-0.8s
   crossfades, restraint) but with a **stacking/fly-in card layout** as the
   signature visual device for the tool-count scene, per the user's
   explicit brief: "screenshots stacking up with new features flying in
   from the right." A dedicated closing beat states privacy / no ads /
   free / open source as an on-screen fact card, not a montage item.
7. **Audio.** Reuse `happy-beats-business-moves-vol-12-by-ende-dot-app.mp3`
   (steady/clean bed, already validated for `polished` tone in this
   project) at `data-volume` ~0.28. Sparse SFX: one soft interface tone on
   the hook, one soft card-slide "whoosh" per stack arrival (light, not
   per-item clicky), one soft confirm tone on the privacy/open-source card.
8. **Share caption:**
   "Net Swiss Knife: 14 network tools in one app. No ads. No tracking.
   100% free and open source."
9. **CTA / outro.** App name lock-up + "Free. Private. Open source." tag.

## Storyboard (target ~23s, 5 scenes, polished stacking device)

**1. Hook — 0.00–3.2s (3.2s)**
Full-bleed dark gradient. Mixed-case, generous letter-spacing headline,
slow fade/scale-in: **"One app. Every network question."** Soft interface
chime.

**2. Stack Reveal — 3.2–6.6s (3.4s)**
Home tool grid screenshot settles center-stage (slow crossfade in per
`polished` rules), small caption beneath: **"14 tools, one clean app."**
Establishes the real product before the montage.

**3. Feature Stack Montage — 6.6–16.6s (10.0s, signature scene)**
The core of the brief: a phone-card screenshot stack builds up center-left
while individual **tool-name chips fly in from the right** and dock beside
their matching card, one at a time, ~0.71s per beat (13 items across
9.2s of the scene). Each new card gently overlaps/offsets the previous
one (a "stacking deck" — increasing z-index, slight y-offset per card) so
by the end of the scene the viewer sees a tall fanned stack of real app
screens with all 13 tool-name labels docked to the right, communicating
breadth at a glance. Tools in order: Ping → Traceroute → Port Scanner →
DNS Lookup → Wi-Fi Scanner → Network Topology → TLS Inspector → WHOIS →
HTTP Probe → Subnet Calculator → mDNS Browser → Speed Test → Wake-on-LAN.
A running counter ticks up in the corner: "1 / 14" → "13 / 14", landing on
a bold **"14 tools."** as the scene resolves.
Soft slide whoosh on each arrival (light, sparse — not chaotic-tone
clicky).

**4. Privacy / Free / Open Source card — 16.6–20.6s (4.0s)**
Hard focus shift: the stack fades back and a single, calm full-bleed fact
card animates in (slow scale + fade, per `polished` restraint), three
short lines revealed in sequence with generous spacing:
```
No ads.
No tracking.
100% free — MIT-licensed, open source.
```
Soft confirm tone on this card's arrival. This is the scene the user
explicitly asked for as its own animation frame, kept separate from the
tool montage so it reads as a standalone claim, not another feature bullet.

**5. Outro — 20.6–23.0s (2.4s)**
App name lock-up, centered, mixed-case: **"Net Swiss Knife"** with tagline
**"Free. Private. Open source."** underneath. Slow fade to hold.

## Music cue guidance

Track: `happy-beats-business-moves-vol-12-by-ende-dot-app.mp3` (already
used for the `polished` and `cinematic` variants in this project; steady,
clean, low-key beat suitable for the slow crossfade pacing this scene
needs). No hard beat-locking required — the stacking montage arrivals are
spaced by design (~0.71s) for readability, not by the track's beat grid.
Cue JSON not required; if the bundled cue file for vol-12 exists, note it
but do not force the fly-in timings onto specific beats — story and
readability stay primary per the tone's restraint rule.

## Audio

- Music: vol-12 at `data-volume` ~0.28 throughout, slight swell (0.28 →
  0.34) under the Feature Stack Montage scene since it's the density
  peak, settling back to 0.28 for the Privacy card and Outro.
- SFX: `interface/bong_001.ogg` (hook), a soft slide/whoosh per card
  arrival in the montage (reuse `interface/select_008.ogg` or similar —
  confirm availability in the bundled SFX library at compose time), and
  `impact/impactSoft_medium_000.ogg` on the Privacy card's arrival and
  the Outro. Deliberately no per-card *hard* click — this is the
  `polished` tone, restraint over density.
