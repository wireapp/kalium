# Link preview fetching

Isolated fetching of untrusted preview URLs on Android, JVM, and Apple platforms. The module exposes two internal-use suspend functions: `fetchLinkPreviewHtmlHead()` and `fetchLinkPreviewImage()`. They return bounded HTML head text or image bytes and a MIME type. HTTP clients, engines, DNS resolvers, and configuration remain internal.

The fetcher permits HTTPS GET requests to public destinations, validates the addresses used by the actual connection, and checks every redirect. It accepts no authentication, cookies, or proxy configuration. Fetches have a ten-second overall deadline, at most five redirects, a 64 KiB metadata limit, and a 5 MiB image limit. Images must have an image MIME type and cannot be SVG.

Android and JVM use a dedicated OkHttp transport. Apple uses Network.framework to connect to a validated numeric address while verifying TLS against the original hostname; Ktor's HTTP parsing utilities handle response framing. Browser JavaScript is unsupported because it cannot enforce the same connection-address policy.

Each function creates a fresh isolated client for that fetch. Its `finally` block awaits outstanding request cleanup and closes the client and engine under `NonCancellable`, including on failure, timeout, or caller cancellation. No client is retained by the repository or account session. Preview generation runs as an account-scope child, so logout also cancels fetches and asset work.

OpenGraph interpretation, image metadata inspection, asset files, and domain failure mapping belong to `logic`. This module has no dependency on authenticated Wire API clients, persistence, or `logic`. Policy and fetch tests live here alongside platform transport tests; repository tests in `logic` use stateless fake fetch functions.
