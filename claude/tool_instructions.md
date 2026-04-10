# Instructions for Adding a New Tool

This document describes exactly how to add a new networking tool to the **Net Swiss Knife** Android app. Follow these steps every time you receive a `tool_prompt` describing a new tool.

---

## Repository Overview

**Net Swiss Knife** is an Android networking utilities app. Each "tool" (e.g. Ping, DNS Lookup, Port Scanner) follows a consistent layered architecture:

```
:core-network  ← pure Kotlin, no Android. Network primitives, interfaces, models.
:core-domain   ← pure Kotlin. Use cases that wrap :core-network logic.
:app           ← Android. ViewModels, Compose screens, Navigation, Hilt DI.
```

### Tech Stack

| Concern | Technology |
|---------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| DI | Hilt (`@HiltViewModel`, `@AndroidEntryPoint`) |
| Async | Kotlin Coroutines / Flow |
| Testing | JUnit 5 + MockK (TDD) |

---

## Naming Conventions

Given a `tool_prompt` such as "WHOIS lookup tool":
- **Tool name**: `Whois` (PascalCase)
- **Route**: `whois` (lowercase)
- **Package suffix**: `whois`
- **Files**: `WhoisRepository`, `WhoisUseCase`, `WhoisViewModel`, `WhoisScreen`

---

## Step-by-Step Implementation

### 1. Identify the tool

From `tool_prompt`, determine:
- Tool name (PascalCase)
- Route string (lowercase, used in `NavRoutes`)
- What network operation it performs

### 2. Add failing unit tests (TDD – RED phase)

**Before writing any implementation**, add failing tests.

#### In `:core-network/src/test/.../`

Create `<ToolName>Test.kt`:

```kotlin
package net.aieat.netswissknife.core.network

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WhoisRepositoryTest {

    // RED: this test will fail because WhoisRepository doesn't exist yet
    @Test
    fun `lookupDomain returns result for valid domain`() {
        // arrange
        val repo = FakeWhoisRepository()  // or use mockk
        // act + assert (write the assertion you want to be true)
        // ...
    }
}
```

Run `./gradlew :core-network:test` – tests should **fail** at this point. That's expected (red phase).

### 3. Implement `:core-network` (TDD – GREEN phase)

Create the minimum code to make the tests pass.

**File: `core-network/src/main/kotlin/com/example/netswissknife/core/network/<toolname>/<ToolName>Repository.kt`**

```kotlin
package net.aieat.netswissknife.core.network.<toolname>

import net.aieat.netswissknife.core.network.NetworkResult

interface <ToolName>Repository {
    suspend fun lookup(input: String): NetworkResult<<ToolName>Result>
}

data class <ToolName>Result(
    // fields specific to this tool
)
```

Provide a real or fake implementation just sufficient to make tests pass. Run `./gradlew :core-network:test` – tests should now **pass** (green phase).

### 4. Add `:core-domain` use case

**File: `core-domain/src/main/kotlin/com/example/netswissknife/core/domain/<ToolName>UseCase.kt`**

```kotlin
package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.<toolname>.<ToolName>Repository
import net.aieat.netswissknife.core.network.<toolname>.<ToolName>Result
import net.aieat.netswissknife.core.network.NetworkResult
import javax.inject.Inject

class <ToolName>UseCase @Inject constructor(
    private val repository: <ToolName>Repository
) : UseCase<String, NetworkResult<<ToolName>Result>> {
    override suspend fun invoke(params: String): NetworkResult<<ToolName>Result> {
        return repository.lookup(params)
    }
}
```

Add corresponding unit tests in `:core-domain/src/test/.../<ToolName>UseCaseTest.kt` using MockK.

### 5. Add `:app` ViewModel

**File: `app/src/main/kotlin/com/example/netswissknife/app/ui/screens/<toolname>/<ToolName>ViewModel.kt`**

```kotlin
package net.aieat.netswissknife.app.ui.screens.<toolname>

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.aieat.netswissknife.core.domain.<ToolName>UseCase
import net.aieat.netswissknife.core.network.<toolname>.<ToolName>Result
import net.aieat.netswissknife.core.network.NetworkResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class <ToolName>UiState(
    val isLoading: Boolean = false,
    val result: <ToolName>Result? = null,
    val error: String? = null
)

@HiltViewModel
class <ToolName>ViewModel @Inject constructor(
    private val useCase: <ToolName>UseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(<ToolName>UiState())
    val uiState: StateFlow<<ToolName>UiState> = _uiState

    fun execute(input: String) {
        viewModelScope.launch {
            _uiState.value = <ToolName>UiState(isLoading = true)
            _uiState.value = when (val result = useCase(input)) {
                is NetworkResult.Success -> <ToolName>UiState(result = result.data)
                is NetworkResult.Error   -> <ToolName>UiState(error = result.message)
            }
        }
    }
}
```

### 6. Add `:app` Compose screen

**File: `app/src/main/kotlin/com/example/netswissknife/app/ui/screens/<ToolName>Screen.kt`**

> **CRITICAL – High-Fidelity UI Required**
> Every tool screen MUST implement the full UI specification below.
> Bare placeholder text (`"Coming soon"`) is **not acceptable** in delivered code.

#### Mandatory UI patterns

| Pattern | How to implement |
|---------|-----------------|
| **Animated entry** | `LaunchedEffect(Unit) { visible = true }` + `AnimatedVisibility(fadeIn + slideInVertically)` |
| **State transitions** | `AnimatedContent` or `Crossfade` between idle / loading / success / error |
| **Loading indicator** | `CircularProgressIndicator` inside the loading state |
| **Card layout** | `ElevatedCard` with `RoundedCornerShape(16.dp)` for result panels |
| **Input field** | `OutlinedTextField` with leading icon and trailing clear button |
| **Error state** | Red-tinted card with `MaterialTheme.colorScheme.error` + retry button |
| **Gradient accents** | `Brush.verticalGradient` / `Brush.radialGradient` for hero areas |
| **Typography** | `displaySmall` → title, `titleMedium` → sections, `bodyMedium` → content |
| **Dark mode safe** | All colors via `MaterialTheme.colorScheme.*`, never hardcoded hex |

#### Template

```kotlin
package net.aieat.netswissknife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import net.aieat.netswissknife.app.ui.screens.<toolname>.<ToolName>ViewModel
import net.aieat.netswissknife.app.ui.screens.<toolname>.<ToolName>UiState

@Composable
fun <ToolName>Screen(viewModel: <ToolName>ViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter   = fadeIn(tween(400)) + slideInVertically(tween(400)) { 40 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Input ──────────────────────────────────────────────────
            var input by remember { mutableStateOf("") }
            OutlinedTextField(
                value         = input,
                onValueChange = { input = it },
                label         = { Text("Host / IP") },
                leadingIcon   = { Icon(/* tool icon */, null) },
                trailingIcon  = {
                    if (input.isNotEmpty()) {
                        IconButton(onClick = { input = "" }) {
                            Icon(Icons.Default.Clear, "Clear")
                        }
                    }
                },
                modifier      = Modifier.fillMaxWidth(),
                singleLine    = true
            )

            Button(
                onClick  = { viewModel.execute(input) },
                modifier = Modifier.fillMaxWidth(),
                enabled  = input.isNotBlank() && !uiState.isLoading
            ) {
                Text("Run")
            }

            // ── Result / loading / error ──────────────────────────────
            AnimatedContent(
                targetState = uiState,
                label       = "<toolname>-state"
            ) { state ->
                when {
                    state.isLoading -> {
                        Box(Modifier.fillMaxWidth(), Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.error != null -> {
                        ElevatedCard(
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text     = state.error,
                                color    = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                    state.result != null -> {
                        ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                            // Render result fields here
                            Column(Modifier.padding(16.dp)) {
                                Text(state.result.toString())
                            }
                        }
                    }
                }
            }
        }
    }
}
```

### 7. Wire into Navigation and bottom bar

#### `NavRoutes.kt`

Add a new object inside the sealed class:
```kotlin
object <ToolName> : NavRoutes("<toolname>", "<Tool Label>", Icons.Default.<SomeIcon>)
```

Add it to `bottomNavItems`:
```kotlin
val bottomNavItems = listOf(Home, Ping, Traceroute, Ports, Lan, Dns, <ToolName>)
```

#### `AppNavigation.kt`

Add a new `composable` entry:
```kotlin
composable(NavRoutes.<ToolName>.route) { <ToolName>Screen() }
```

### 8. Add Hilt binding (if needed)

If your repository has a concrete implementation separate from the interface, add a Hilt module:

**File: `app/src/main/kotlin/com/example/netswissknife/app/di/<ToolName>Module.kt`**

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class <ToolName>Module {
    @Binds
    @Singleton
    abstract fun bind<ToolName>Repository(
        impl: <ToolName>RepositoryImpl
    ): <ToolName>Repository
}
```

### 9. Refactor (TDD – REFACTOR phase)

- Remove duplication.
- Improve naming.
- Ensure all tests still pass: `./gradlew test`.

### 10. Verify the full build

```bash
./gradlew test
./gradlew :app:assembleDebug
```

Both must succeed before committing.

### 11. Update documentation

- **`README.md`**: Add a row to the "Available Tools" table with the new tool name, route, and a one-line description.
- **`claude/tool_instructions.md`**: Add a brief entry at the bottom under "## Tools Added So Far" (create the section if it doesn't exist).

---

## TDD Summary

| Phase | Action | Expected result |
|-------|--------|----------------|
| **Red** | Write failing tests | `./gradlew :core-network:test` fails |
| **Green** | Write minimal implementation | `./gradlew :core-network:test` passes |
| **Refactor** | Clean up code | All tests still pass |

---

## File Checklist for a New Tool

- [ ] `core-network/src/main/.../network/<toolname>/<ToolName>Repository.kt`
- [ ] `core-network/src/main/.../network/<toolname>/<ToolName>Result.kt` (if separate)
- [ ] `core-network/src/test/.../network/<ToolName>Test.kt`
- [ ] `core-domain/src/main/.../domain/<ToolName>UseCase.kt`
- [ ] `core-domain/src/test/.../domain/<ToolName>UseCaseTest.kt`
- [ ] `app/src/main/.../app/ui/screens/<toolname>/<ToolName>ViewModel.kt`
- [ ] `app/src/main/.../app/ui/screens/<ToolName>Screen.kt`
- [ ] `app/src/main/.../app/di/<ToolName>Module.kt` (if needed)
- [ ] `NavRoutes.kt` – add route and icon
- [ ] `AppNavigation.kt` – add composable
- [ ] `README.md` – update tools table
- [ ] `claude/tool_instructions.md` – update "Tools Added So Far"

---

## Tools Added So Far

*(This section is updated automatically each time a tool is added.)*

| Tool | Route | Description |
|------|-------|-------------|
| Home | `home` | Welcome screen / overview |
| Ping | `ping` | Placeholder for ICMP ping |
| Traceroute | `traceroute` | Placeholder for traceroute |
| Port Scanner | `ports` | Placeholder for TCP port scanning |
| LAN Scanner | `lan` | Placeholder for LAN device discovery |
| DNS Lookup | `dns` | Placeholder for DNS resolution |
| HTTP Probe | `httprobe` | HTTP/HTTPS request tester with security header analysis |
| Subnet Calculator | `subnet` | IPv4 subnet calculator with binary breakdown and notation conversion |
