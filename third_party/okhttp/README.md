# Local OkHttp source fork

This module vendors OkHttp `parent-5.5.0` at upstream commit
`a94bdf152084d11acecd44dcd09ffef203f4f0aa` (Apache-2.0). Source files retain
their upstream notices; see [LICENSE.txt](LICENSE.txt). The JVM and Android
source sets and required generated IDNA/public-suffix resources are included so
the Android app and JVM tests use the same upstream implementation.

This fork enforces parser-time response metadata budgets in
OkHttp's HTTP/1 and HTTP/2 decoders. Keep its public API compatible with the
pinned Maven artifacts used by `okhttp-tls` and `mockwebserver3`. Do not replace
parser enforcement with a response interceptor or adapter-only limit.
