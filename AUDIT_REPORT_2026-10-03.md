# Code review and fixes — 2026-10-03

## Scope

GPT-6-luna agents at high effort reviewed the app, core-domain, core-network,
build configuration, CI, and repository tooling in separate assignments.
Additional agents independently reviewed the changes. The vendored OkHttp
review focused on the repository's local response-budget modifications.
This review does not establish that the entire codebase is free of bugs.

## Changes

- Port scan: create the operation session when a cold Flow is collected, so
  repeated collections use separate sessions and the deadline starts at collection.
- Host validation: enforce the 253-character ASCII DNS name limit, reject
  malformed IPv6 zone suffixes, and reject IPv6 literals with a trailing dot
  instead of treating it as a DNS root marker.
- cURL export: permit an initial HTTP URL instead of rejecting it with an
  HTTPS-only protocol allowlist. Follow only HTTPS redirects to preserve
  downgrade protection. This deliberately also blocks HTTP-to-HTTP redirects
  in exported commands; curl cannot express a conditional per-hop downgrade rule.
- UDP socket binding: classify multicast only for validated IP literals,
  avoiding an incidental hostname lookup during classification.
- TLS inspection: normalize absolute DNS names before configuring SNI, so a
  trailing DNS root dot does not silently suppress SNI.
- LLDP: reject malformed management-address OID arcs instead of dropping them
  and shifting the remaining bytes into a different address.
- Release workflow: require all signing secrets, including when none are supplied.
- Topology test: enable discovered-neighbor queries in the scenario that expects
  a neighbor query to hit its deadline.
- Port scan deadline test: keep virtual time paused until the external resolver
  worker has started, eliminating a race between worker scheduling and the deadline.
- Static analysis: explicitly allow idiomatic Compose function names in three
  helpers, and format changed files without regenerating baselines.
- README: align HTTP Probe availability and exported redirect behavior with code.
- Ping sharing and Ping/Wi-Fi accessibility descriptions: use count labels
  that remain grammatical when there is exactly one packet, sample, or access point.
- Dependencies: update Okio to 3.18.2 for its Base64 compatibility fix and
  Bouncy Castle providers to 1.86 (TLS to 1.86.1) for published security fixes.
  See [dependency review](DEPENDENCY_UPDATES_2026-10-03.md) for other available updates.

## Regression coverage

Added tests for repeated port scan Flow collection, long DNS names, malformed
IPv6 zones, HTTP cURL protocol selection, literal multicast classification,
SNI with an absolute DNS name, and malformed LLDP management addresses including IPv6 decoding.

## Validation

The final combined Gradle verification succeeded:

| Check | Result |
| --- | --- |
| `:core-network:test` | 1,370 passed, 1 skipped, 0 failures |
| `:core-domain:test` | 222 passed, 0 failures |
| `:app:testDebugUnitTest` | 623 passed, 0 failures |
| `ktlintCheck` and `detekt` | Passed across all configured modules |
| All three `koverVerify` tasks | Passed without lowering coverage gates |
| `:app:assembleDebug` | Passed |
| `:app:lintDebug` final resource/package check | Passed; 0 errors, 41 warnings, 9 hints |
| Python quality/release/OUI tests | 31 passed; resource ownership check passed |
| `dependencyUpdates` | Completed; candidates reviewed in the dependency report |
| `git diff --check` | Passed |

The initial lint run was canceled after a long-running source walk; a fresh
isolated retry succeeded. Remaining lint warnings include dependency update
advisories, constructor defaults used by tests, and intentional trust-manager
behavior in diagnostic probes. The three confirmed count-copy issues were fixed.

No Android device was connected for instrumented tests. Signed release
publication and external network interoperability were not exercised.
The pre-existing untracked `.commandcode/` directory was left untouched.
