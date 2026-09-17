# Hyperframes Composition Brief: Net Swiss Knife

## Objective
Create a short launch-style brag video for Net Swiss Knife, an Android
networking-diagnostics app.

## Output
- Composition directory: `composition/`
- Rendered video: `brag.mp4`
- Format: landscape — 1920x1080
- Duration: ~20 seconds (15–25s range)

## Source Material
- Project root: `/home/dev/android-network-tools`
- Primary files read: `README.md`, `app/src/main/kotlin/.../ui/theme/Color.kt`,
  `Theme.kt`, `app/src/main/res/values/strings.xml`, `HomeScreen.kt`
- Product name: Net Swiss Knife
- Tagline: "Your all-in-one Android networking toolkit"
- Strongest claim / verbatim copy (from the real onboarding screen,
  screenshotted live): "No accounts, no ads, and nothing leaves your phone
  unless a tool explicitly sends it somewhere, like a DNS or WHOIS query."
- Key UI moments to recreate/show: see Assets below — all real emulator
  screenshots, not recreated mockups.
- Copy that must appear verbatim:
  - "Net Swiss Knife"
  - "Your all-in-one Android networking toolkit"
  - "14 tools. One app." (derived claim — README lists exactly 14 tools:
    Ping, Traceroute, Port Scanner, LAN Scanner, DNS Lookup, Wi-Fi Scanner,
    TLS Inspector, Network Topology, WHOIS Lookup, HTTP Probe, mDNS Browser,
    Subnet Calculator, Speed Test, Wake-on-LAN)
  - "No accounts. No ads. Just tools." (outro line, condensed from the
    onboarding copy above)

## Creative Direction
- Tone preset: **app-store** (smooth, feature-card clean, corporate but not
  boring)
- Creative direction: "snap open a new tool every beat" — lean into the
  Swiss Army knife naming with a quick, confident, one-tool-per-beat rhythm.
  Earnest, not a joke — this is a real, deep utility app.
- Angle: a professional network engineer's whole toolbelt, unified into one
  clean app, demonstrated with real, live tool output (not mockups).
- Hook: app icon snaps in like a knife blade opening; "14 tools." / "One
  app." punches in fast.
- Outro / punchline: "Net Swiss Knife — No accounts. No ads. Just tools."
- Avoid:
  - Generic SaaS language ("streamline your workflow" etc.)
  - Abstract filler visuals — every scene must show real app content
  - Unrelated visual redesign — stay inside the app's own Material 3 look

## Visual Identity
- Background (dark): `#0F1512`
- Background (light, matches app's light scheme): `#F5FBF7`
- Primary accent: `#1B8A5E` (deep green)
- Primary container / light mint: `#B3F0D8`
- Secondary teal: `#4A6359`
- On-dark text: `#DDE4DF`
- On-light text: `#171D1A`
- Font: system default sans (the app uses Material 3 default typography, no
  custom font family declared) — use a clean modern sans in the composition
- Visual references from the project: Material 3 rounded cards, gradient
  hero headers (the app itself uses `Brush.linearGradient` on primary/
  tertiary containers for tool hero headers — visible directly in
  `wifi_spectrum.png` and `subnet_result.png`/`subnet_binary.png`)

## Storyboard
Use `brag-plan.md` in this directory as the full creative contract. Scene
summary:

1. **Hook** — 2.5s — dark bg, app icon (green/mint network-node glyph) snaps
   in, "14 tools." then "One app." punch in.
2. **Reveal** — 4s — `assets/screenshots/home_grid.png`, staggered card
   entrance (matches the real app's own stagger animation), label "Net Swiss
   Knife."
3. **Highlight 1 (Wi-Fi)** — 4s — `assets/screenshots/wifi_spectrum.png`,
   push-in on the spectrum chart, label "Live Wi-Fi spectrum analysis."
4. **Highlight 2 (Subnet)** — 4s — `assets/screenshots/subnet_binary.png`
   (hero shot), bit-grid ripple reveal, label "Binary breakdown, decoded
   instantly."
5. **Highlight 3 (Ping)** — 3.5s — `assets/screenshots/ping_live.png` →
   `assets/screenshots/ping_chart.png`, label "Real diagnostics. Real
   numbers."
6. **Punchline/outro** — 2.5s — dark bg, wordmark + "No accounts. No ads.
   Just tools."

## Audio
- Audio role: consistent light layer, app-store energy — one clean music bed
  plus a small number of well-timed SFX per feature-card-style reveal.
- Audio arc: bed enters under the hook, stays steady through the highlights,
  slight emphasis/hit on the outro logo landing, quick fade after.
- Music: `assets/music/happy-beats-business-moves-vol-1-by-ende-dot-app.mp3`
  (full upbeat track — table in `audio.md` recommends this for `app-store`)
- Music treatment: bed at 0.3–0.35 volume throughout, no ducking needed (no
  voiceover), gentle fade-out in the last ~0.5s after the final logo hit.
- Music cue guidance: bundled preset at
  `assets/music/cues/happy-beats-business-moves-vol-1-by-ende-dot-app.music-cues.json`
  (also `.md` summary alongside it). Regular beat grid starts at 3.02s (≈120
  BPM, ~0.5s spacing). Strong cues cluster later in the window: 16.02s,
  17.02s, 17.52s, 18.02s, 18.52s, 20.02s — these land naturally near
  Highlight 3 and the outro, so prefer locking the Highlight-3→outro
  transition and the final logo hit to two of these strong cues (within
  ±0.15s) rather than forcing early scenes onto the sparse pre-3s intro.
- Audio-reactive treatment: subtle — let the hero glow / card presence on
  the highlight screenshots breathe slightly with RMS. No waveform/equalizer
  visuals.
- Audio-coupled moments:
  - Hook icon-snap — impact/interface hit at the icon's first visible frame
  - Card stagger (Reveal scene) — 3–4 short card/drop sounds, one per
    staggered card, matching the app's real stagger rhythm
  - Bit-grid ripple (Highlight 2) — light tick/arpeggio-style accent as
    cells light up left to right
  - RTT chart draw-on (Highlight 3) — whip/wipe transition sound
  - Final logo hit (Outro) — one clean bell/impact hit, let it ring slightly
    over the music fade
- SFX selection guidance: per the app-store row in `audio.md` — `interface/
  drop_*` or `interface/click_*` per feature-card reveal, `impact/
  impactBell_heavy_000` on the outro. Keep all SFX at 0.65–0.75 volume.
- SFX analysis guidance: consult `~/.claude/skills/brag/assets/sfx/
  sfx-analysis.md` if present; prefer low/medium HF-risk files since this is
  a polished, repeated-card-reveal edit.
- Exact SFX choice: Hyperframes should choose exact filenames/timestamps
  once the animation timing is implemented.
- Audio files are already copied into `composition/assets/music/` (and its
  `cues/` subfolder). Hyperframes should copy any SFX it selects from the
  skill's `assets/sfx/` library into `composition/assets/sfx/`.

## Screenshot Assets (all real, captured live from the `dev36` emulator)
Located in `composition/assets/screenshots/`:

| File | Use |
|---|---|
| `home_grid.png` | Scene 2 — Reveal |
| `wifi_spectrum.png` | Scene 3 — Highlight 1 |
| `subnet_binary.png` | Scene 4 — Highlight 2 (hero shot) |
| `subnet_result.png` | optional supporting shot for Scene 4 |
| `ping_live.png` | Scene 5 — Highlight 3 (part 1) |
| `ping_chart.png` | Scene 5 — Highlight 3 (part 2, hero shot) |
| `more_sheet.png` | optional supporting shot — categorized tool list |
| `subnet_empty.png`, `ping_empty.png`, `topology_config.png` | not required; kept for reference only |

These are full-device screenshots (1080x2400, status bar + bottom nav
included). Hyperframes should crop/frame them (e.g. a phone-frame mockup or
a cropped content window) as fits the composition rather than showing raw
unstyled screenshots edge-to-edge.

## Hyperframes Instructions
Load the composition-building Hyperframes domain skills — `hyperframes-core`
(composition contract + `data-*` timing), `hyperframes-animation` (motion),
`hyperframes-creative` (design spec, beats, audio-reactive),
`hyperframes-keyframes` (seek-safe keyframes), and `hyperframes-cli` (lint/
check/render). `/brag` is its own workflow: do not enter the `hyperframes`
entry-point intent interview and do not route into its generic promo/
launch-video workflow. Prefer native Hyperframes conventions over anything
hardcoded in `/brag`.

Requirements:
- Show at least one real UI, copy, or visual element from the source
  project (this brief provides several — use at least the two hero shots:
  `subnet_binary.png` and `ping_chart.png`).
- Keep all text readable in the final render.
- Keep the video within 15–25 seconds.
- Include the planned music/SFX layer (not disabled, not documented as
  intentional silence).
- Treat the audio notes above as guidance, not a fixed cue sheet.
- Run `hyperframes check` before render — it is brag's single gate.
