# Net Swiss Knife – Android Networking Utilities

**Net Swiss Knife** is an Android "Swiss army knife" app for network diagnostics and utilities. It provides a collection of networking tools in a clean Jetpack Compose + Material 3 UI.

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
│       └── claude_add_tool.yml     # Claude-driven "add tool" workflow
├── claude/
│   └── tool_instructions.md        # Instructions for Claude when adding new tools
└── README.md
```

### `:core-network`
Pure Kotlin module (no Android SDK dependency). Contains:
- Network result wrappers (`NetworkResult`)
- Host/IP validation utilities (`HostValidator`)
- Interfaces and models for each networking tool (e.g. ping, DNS, port scanner)
- This is where all TDD unit tests live.

### `:core-domain`
Pure Kotlin module that depends on `:core-network`. Contains:
- Use cases (`UseCase<Params, Result>`) that orchestrate `:core-network` logic
- `ValidateHostUseCase` and similar helpers
- Unit-tested independently.

### `:app`
Android module (Jetpack Compose, Material 3, Hilt). Contains:
- Single-Activity architecture (`MainActivity`)
- Navigation Compose with a bottom navigation bar
- Screens for each tool (Home, Ping, Traceroute, Ports, LAN, DNS)
- ViewModels injected via Hilt

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt |
| Build | Gradle Kotlin DSL + Version Catalog |
| Testing | JUnit 5 + MockK |
| Min SDK | 26 (Android 8.0) |

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

---

## Available Tools

| Route | Tool | Status |
|-------|------|--------|
| `home` | Home / Overview | Placeholder |
| `ping` | Ping | Placeholder |
| `traceroute` | Traceroute | Placeholder |
| `ports` | Port Scanner | Placeholder |
| `lan` | LAN Scanner | Placeholder |
| `dns` | DNS Lookup | Placeholder |

---

## CI & Automation

### Standard CI (`ci.yml`)
Runs on every push to `main` and every PR targeting `main`:
1. Sets up JDK 21 (Temurin)
2. Caches Gradle
3. Runs `./gradlew test`
4. Runs `./gradlew :app:assembleDebug`

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
