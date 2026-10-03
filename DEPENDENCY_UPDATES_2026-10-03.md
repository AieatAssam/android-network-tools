# Dependency update review — 2026-10-03

Reviewed `build/dependencyUpdates/report.txt` and verified the material candidates against upstream release notes. The report predates the applied Okio and Bouncy Castle updates below, so its version list is stale for those artifacts. Gradle 9.8.0 is a wrapper update, not a library update, and is not applied.

## Recommended direct updates

| Current → available | Declaration / scope | Assessment |
| --- | --- | --- |
| AndroidX Compose BOM `2026.08.00` → `2026.09.00` | `gradle/libs.versions.toml`; aligns the Compose artifacts currently at 1.12.0 | Update the BOM as one unit. AndroidX lists Compose UI/animation 1.12.1 as stable; the release includes Compose UI fixes, including TalkBack traversal ordering and nested-scroll detachment handling. [Compose UI release notes](https://developer.android.com/jetpack/androidx/releases/compose-ui#1.12.1) |
| Navigation Compose `2.10.0` → `2.10.2` | `gradle/libs.versions.toml` | Recommended. The 2.10.1 patch fixes unexpected clipping when `NavHost` skips animations, which is relevant to this app's animated navigation. 2.10.2 is the latest stable patch and updates Lifecycle dependencies. [Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation#2.10.2) |
| Kover Gradle plugin `0.9.9` → `0.9.11` | Root buildscript classpath | Consider as a build-tool update. Upstream 0.9.10 fixes reporter concurrency and other report-task issues; 0.9.11 reverts a test-task skipping change. Keep the explicit FreeMarker 2.3.35 override in place and verify its resolution when upgrading. [Kover 0.9.10](https://github.com/Kotlin/kotlinx-kover/releases/tag/v0.9.10), [Kover 0.9.11](https://github.com/Kotlin/kotlinx-kover/releases/tag/v0.9.11) |

## Applied after the report

- Okio `3.18.1` → `3.18.2` in the vendored OkHttp module. Upstream says 3.18.1 regressed Base64 padding and 3.18.2 restores the prior behavior. Vendored OkHttp uses Base64 for WebSocket keys, credentials, certificate pins, and cache entries. [Okio changelog](https://github.com/square/okio/blob/master/CHANGELOG.md#version-3182)
- Bouncy Castle `bcprov`/`bcpkix` pins moved from `1.85` to `1.86` in the root buildscript and app lint constraints. The OkHttp fork's JVM and Android `compileOnly` pins now use `bcprov`/`bcutil` `1.86` and `bctls` `1.86.1`; `bcutil` also resolves transitively from `bcpkix` on the build classpaths. These are build-time or optional-provider paths, not ordinary app runtime dependencies. The vendor describes 1.86 as a hardening release with multiple CVE fixes. [Bouncy Castle 1.86 announcement](https://www.bouncycastle.org/resources/new-release-bouncy-castle-java-1-86/)

## Optional or lower-priority direct/build pins

- JUnit `6.1.2` → `6.1.3` is a stable patch. Its listed fix restores GraalVM 25 reachability metadata; this build does not appear to exercise JUnit on GraalVM, so treat as routine maintenance rather than a blocker. [JUnit 6.1.3 release notes](https://docs.junit.org/6.1.3/release-notes.html)
- `androidx.core:core-ktx` `1.19.0` → `1.19.1` is available. Its release note updates Lifecycle for bundled lint checks with AGP 9.5 alpha; this project currently pins AGP 9.4.1, so no specific issue here requires it. [Core release notes](https://developer.android.com/jetpack/androidx/releases/core#1.19.1)
- `androidx.annotation` `1.10.0` → `1.11.0`, Animal Sniffer annotations `1.27` → `1.28`, jose4j `0.9.6` → `0.9.7`, Conscrypt `2.6.2` → `2.7.0`, and Graal SVM `25.0.4` → `25.0.4.1.1` appear in the report. They are AndroidX or build/optional integration dependencies; assess with their own compatibility tests before batching. jose4j 0.9.7 is a new upstream tag, but this review found no release note establishing a specific fix for this project's use. [jose4j tags](https://bitbucket.org/b_c/jose4j/downloads/?tab=tags), [Conscrypt setup/version](https://github.com/google/conscrypt)
- Commons Lang `3.18.0` → `3.21.0` is listed by Gradle, but the Apache download/release pages still label 3.21.0 as a snapshot / show a placeholder date. Do not treat that candidate as confirmed stable until Apache publishes consistent release metadata. [Apache Commons Lang release history](https://commons.apache.org/proper/commons-lang/changes.html), [download page](https://commons.apache.org/lang/download_lang.cgi)

## Report entries not to update directly

- `androidx.compose.compiler:compiler 1.3.2 → 1.5.15` is reported from the plugin's `kotlin-extension` configuration, not the project's declared Compose compiler plugin. Kotlin 2.4.20 supplies the Compose compiler plugin; do not add or change this independent compiler coordinate based on the report.
- `kotlin-build-tools-impl`, Compose compiler plugin artifacts, `kover-jvm-agent`, and Kotlin logging/SARIF entries are plugin-created or transitive configurations. Update their owning plugin/dependency and inspect the resulting graph instead of pinning these report rows independently.
- Detekt remains on the declared `2.0.0-alpha.6` line. The stable-only update policy did not identify a stable replacement; do not chase pre-release builds as a routine update.
- Gradle `9.7.1 → 9.8.0` requires a separate wrapper/build compatibility decision. It is not applied here.

The report marks the remaining direct application versions (including Kotlin, AGP, Coroutines, OkHttp, SNMP4J, dnsjava, Hilt, MockK, and DataStore) as latest release versions at scan time.

## Default-branch security alerts checked after push

GitHub reported seven open Dependabot alerts on the repository's default branch.
The alert API identifies older build dependency versions. A read-only Gradle
classpath resolution on this review branch confirmed the following selections:

| Dependency | Resolved version here | Alert fix threshold |
| --- | --- | --- |
| FreeMarker | 2.3.35 | 2.3.35 |
| Bouncy Castle provider | 1.86 | 1.84 or 1.85, depending on the alert |
| Bouncy Castle PKIX | 1.86 | 1.84 |
| Commons Lang | 3.18.0 | 3.18.0 |
| Apache HttpClient | 4.5.14 | 4.5.13 |

These resolved versions are outside all seven alerted vulnerable ranges. The
settings plugin classpath contained none of these artifacts. This confirms the
review branch's resolution; it does not close alerts on the default branch or
constitute an exhaustive vulnerability scan. [Repository security alerts](https://github.com/AieatAssam/android-network-tools/security/dependabot)
