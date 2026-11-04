# Code and test review

Review base: `main`. Starting branch commit: `ec0da17`.

The primary agent reviewed the code and tests. GPT-6 Luna agents with xhigh reasoning implemented the findings. This pass builds on the previous branch review and concentrates on whether tests exercise observable behavior with independent expectations.

## Findings

1. **Traceroute reports a responding router as the destination.** `TracerouteResult.reachedDestination` used the highest hop's response status, while the ViewModel populated `resolvedIp` from the last responding router. The test fixture derived its destination from that same router, so tests affirmed the defect. Carry the resolver's destination separately and retain the native engine's destination-response evidence. Cover incomplete routes, out-of-order hops, multiple responders, and metadata preservation through enrichment.
2. **HEAD curl exports use the wrong curl mode.** `-X HEAD` changes the method but leaves curl expecting a response body. A local server returning `Content-Length: 100` without a body reproduced exit code 18; `--head` completed successfully. Export HEAD using curl's header-only mode and cover both redirect and custom-header policies.
3. **UDP can lose its selected route between classification and binding.** TCP already rechecks the destination against one network observation. UDP and Wake-on-LAN used separate checks and a bind that could silently do nothing after network loss. Add equivalent atomic datagram binding, propagate binding failure, and cover closure and prevention of sends when the route disappears or changes.

## Tests removed

| File | Cases | Why removed |
| --- | ---: | --- |
| `NetworkResultTest.kt` | 3 | Constructs a Kotlin data class and reads the supplied fields; does not exercise either error factory. |
| `PingPacketResultTest.kt` | 6 | Constructor passthrough and compiler-generated equality only. |
| `PingResultTest.kt` | 4 | Reads the host, packets, statistics, and output supplied by the test itself. |
| `PingStatsTest.kt` | 7 | Supplies loss and RTT statistics explicitly and then asserts those same values; never computes statistics. |

The typed-error factory tests and `PingStatsComputeTest` remain. Tests of parameter forwarding, calculations, cancellation, cleanup, protocol parsing, and policy boundaries remain even when small. Source guards and dependency behavior tests are not treated as substitutes for runtime coverage, but were not removed merely because of their form.

## Missing coverage added

- Traceroute destination evidence and resolver metadata across repository and ViewModel boundaries, replacing expectations that encoded the incorrect behavior.
- HEAD export mode with redirect following and sensitive-header suppression.
- UDP binding races, required binding failure, socket ownership, and the no-send behavior on failure.
- JSON nested and empty containers, string escapes, number lexeme and duplicate-key preservation, root primitives, malformed token boundaries, exact nesting limits, and output growth limits independent of input size.

Existing traceroute cancellation/deadline expectations now include independently specified resolver metadata. Repository integration tests use real coroutine timers so virtual enrichment deadlines cannot race real operation cleanup. The SNMP saturation test also waits, within a deadline, for executor workers to become idle after coroutine completion; completion can resume the caller before the worker exits its runnable. Its saturation, no-allocation, and exact cleanup assertions remain intact.

## Validation

- Regression proof: temporarily restoring the old HEAD exporter and old traceroute reach predicate produced seven assertion failures (all three HEAD cases and four traceroute cases). Both fixed source files were restored byte-for-byte afterward.
- Python quality, release, and OUI suites: 31 tests passed.
- Resource ownership inventory: passed; all 17 raw resource creation/open sites have reviewed scope registration.
- Final combined command passed: `./gradlew test ktlintCheck detekt :app:koverVerify :core-domain:koverVerify :core-network:koverVerify :app:lintDebug :app:assembleRelease --continue --no-daemon --no-parallel`.
- JVM results: 2,195 tests, zero failures, one skipped optional public SNMP demo test (app: 617; core-domain: 221; core-network: 1,357). Thus 2,194 tests passed.
- Release APK checks passed for the required SNMP security classes, ARM64 ICMP native library, OUI data, and PSL index. GPT-6 Sol performed the final validation.

Android device and emulator behavior was not validated in this pass.
