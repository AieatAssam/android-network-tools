# CLAUDE.md – Net Swiss Knife

This file is the primary reference for Claude Code when working on this repository.
Read it in full before making any changes.

---

## Project Overview

**Net Swiss Knife** is a production-quality Android networking utilities app.
It provides a collection of networking diagnostic tools (Ping, DNS, Port Scanner, etc.)
in a clean, modern Jetpack Compose + Material 3 UI.

---

## Module Layout

```
android-network-tools/
├── app/                  # Android UI layer (Compose, ViewModels, Navigation, Hilt)
├── core-domain/          # Pure Kotlin – use cases, business logic
├── core-network/         # Pure Kotlin – network primitives, protocols, models
├── .github/
│   └── workflows/
│       ├── ci.yml               # Build & test on every push/PR to main
│       ├── release.yml          # Package & publish signed release APK/AAB
│       └── claude_add_tool.yml  # Claude-driven tool addition via workflow_dispatch
├── claude/
│   └── tool_instructions.md    # Step-by-step guide for adding new tools
├── CLAUDE.md                   # ← this file
└── README.md
```

### Layer Responsibilities

| Module | Language | Purpose |
|--------|----------|---------|
| `:core-network` | Pure Kotlin | Network interfaces, result wrappers, protocol implementations |
| `:core-domain` | Pure Kotlin | Use cases that orchestrate `:core-network` |
| `:app` | Kotlin + Android SDK | UI screens, ViewModels, navigation, DI wiring |

---

## Tech Stack

| Concern | Technology | Notes |
|---------|-----------|-------|
| Language | Kotlin 1.9.x | JDK 21, Kotlin DSL everywhere |
| UI | Jetpack Compose + Material 3 | **High-fidelity, animated UI required** |
| Navigation | Navigation Compose 2.7.x | Bottom nav + animated transitions |
| DI | Hilt 2.51.x | `@HiltViewModel`, `@AndroidEntryPoint` |
| Async | Coroutines + Flow | `viewModelScope`, `StateFlow` |
| Testing | JUnit 5 + MockK | TDD (Red → Green → Refactor) |
| Build | Gradle 8.x Kotlin DSL + Version Catalog | `gradle/libs.versions.toml` |
| Min SDK | 26 (Android 8.0) | Target SDK 34 |

---

## UI Requirements – CRITICAL

**Every screen and every tool MUST use modern, high-fidelity UI.**
Placeholder "Coming soon" screens are acceptable during development but must still
follow the patterns below. Do not ship bare `Box { Text("Coming soon") }`.

### Required Patterns

1. **Animated screen entry** – All screens must use `LaunchedEffect` + `animateFloatAsState`
   (or `AnimatedVisibility`) to animate content in on first composition.
   ```kotlin
   var visible by remember { mutableStateOf(false) }
   LaunchedEffect(Unit) { visible = true }
   AnimatedVisibility(visible, enter = fadeIn() + slideInVertically()) { ... }
   ```

2. **Navigation transitions** – `AppNavHost` must define `enterTransition`, `exitTransition`,
   `popEnterTransition`, `popExitTransition` on every `composable { }` entry.
   Use `fadeIn + slideInVertically` / `fadeOut + slideOutVertically` as defaults.

3. **Card-based layouts** – Use `ElevatedCard` or `OutlinedCard` from Material 3 for
   result panels, info sections, and tool tiles. Never use raw `Column` as a top-level container.

4. **Loading states** – Show an animated `CircularProgressIndicator` (or custom pulsing
   animation) while network operations are in progress.

5. **Shimmer / skeleton placeholders** – For items that load asynchronously, show a
   shimmer animation while loading rather than empty space.

6. **Smooth transitions** – Use `Crossfade` or `AnimatedContent` when switching between
   UI states (loading → result → error).

7. **Ripple and haptic feedback** – All interactive elements must have visible ripple
   feedback. Use `Indication` defaults from Material 3.

8. **Gradient accents** – Use `Brush.linearGradient` / `Brush.radialGradient` for hero
   areas, icons, and decorative backgrounds.

9. **Typography scale** – Use the full Material 3 typography scale:
   `displaySmall` for screen titles, `titleMedium` for section headers,
   `bodyMedium` for content, `labelSmall` for metadata.

10. **Dark-mode-safe** – All colors must reference `MaterialTheme.colorScheme.*` tokens,
    never hardcoded hex values in composables.

### Minimum UI Checklist per Tool Screen

- [ ] Animated entrance (fade + slide)
- [ ] Input field with `OutlinedTextField`, icon, and clear button
- [ ] Animated loading indicator while the use case executes
- [ ] `ElevatedCard` for displaying results
- [ ] `AnimatedContent` or `Crossfade` between states (idle / loading / success / error)
- [ ] Error state with `MaterialTheme.colorScheme.error` styling and retry action
- [ ] All strings in `strings.xml` (no hardcoded strings in composables)

---

## Development Workflow

### Adding a New Tool

Follow `claude/tool_instructions.md` exactly. Summary:

1. Write failing tests (`./gradlew :core-network:test` → RED)
2. Implement `:core-network` repository + model
3. Add `:core-domain` use case + tests
4. Create ViewModel in `:app`
5. Build the Compose screen (**follow UI requirements above**)
6. Wire into `NavRoutes` + `AppNavHost` with animated transitions
7. Add Hilt module if needed
8. Refactor while keeping tests green
9. `./gradlew test && ./gradlew :app:assembleDebug` must both pass
10. Update README and `claude/tool_instructions.md`

### TDD Cycle

```
RED:     Write failing test → ./gradlew :core-network:test (fails)
GREEN:   Write minimal impl → ./gradlew :core-network:test (passes)
REFACTOR: Clean up → ./gradlew test (all pass)
```

---

## Build Commands

```bash
# Run all unit tests
./gradlew test

# Run tests for a single module
./gradlew :core-network:test
./gradlew :core-domain:test

# Build debug APK
./gradlew :app:assembleDebug

# Build release APK (requires signing config)
./gradlew :app:assembleRelease

# Build release AAB (for Play Store)
./gradlew :app:bundleRelease

# Clean
./gradlew clean
```

---

## CI / CD Pipelines

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `ci.yml` | Push / PR to `main` | Run tests + build debug APK |
| `release.yml` | Push tag `v*.*.*` or manual dispatch | Sign + publish release APK/AAB to GitHub Releases |
| `claude_add_tool.yml` | Manual (`workflow_dispatch`) | Claude-driven tool addition with TDD verification |

### Publishing (release.yml)

The release workflow uses GitHub Actions secrets for signing:

| Secret | Description |
|--------|-------------|
| `RELEASE_KEYSTORE_BASE64` | Base64-encoded `.jks` / `.keystore` file |
| `RELEASE_KEYSTORE_PASSWORD` | Keystore password |
| `RELEASE_KEY_ALIAS` | Key alias |
| `RELEASE_KEY_PASSWORD` | Key password |

Set these in **GitHub → Settings → Secrets and variables → Actions** before running the workflow.

> **Important – GitHub Actions limitation:** The `secrets` context is **not** available
> inside `if:` expressions. The workflow works around this by running a detection step
> (`signing`) that reads the secret into an env var and emits a `has_keystore` output;
> all conditional steps then use `steps.signing.outputs.has_keystore == 'true'`.
> Do **not** write `if: secrets.XYZ != ''` in this workflow — it will cause a parse
> error and the entire workflow will fail to load.

---

## File Naming Conventions

| Entity | Convention | Example |
|--------|-----------|---------|
| Tool name | PascalCase | `WhoisLookup` |
| Route string | lowercase, no spaces | `whois` |
| Package suffix | lowercase, no underscores | `whois` |
| Compose screen file | `<ToolName>Screen.kt` | `WhoisScreen.kt` |
| ViewModel | `<ToolName>ViewModel.kt` | `WhoisViewModel.kt` |
| Repository interface | `<ToolName>Repository.kt` | `WhoisRepository.kt` |
| Use case | `<ToolName>UseCase.kt` | `WhoisUseCase.kt` |
| Hilt module | `<ToolName>Module.kt` | `WhoisModule.kt` |

---

## Architecture Invariants

- `:core-network` must **never** import Android SDK classes.
- `:core-domain` must **never** import Android SDK classes.
- `:app` may import from both `:core-network` and `:core-domain`.
- ViewModels must be annotated with `@HiltViewModel` and inject via `@Inject constructor`.
- All async operations must run in `viewModelScope` using coroutines.
- UI state must be exposed as `StateFlow<UiState>` from the ViewModel.
- Navigation routes must be declared in `NavRoutes.kt` only – never as raw strings.
