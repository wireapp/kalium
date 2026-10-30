# Link preview fetching

Isolated fetching of untrusted preview URLs on Android, JVM, and Apple platforms. The module exposes an internal-use `LinkPreviewFetcher` through `createLinkPreviewFetcher()`. Its only fetch operations return bounded HTML head text or image bytes and a MIME type. HTTP clients, engines, DNS resolvers, and configuration remain internal.

The fetcher permits HTTPS GET requests to public destinations, validates the addresses used by the actual connection, and checks every redirect. It accepts no authentication, cookies, or proxy configuration. Fetches have a ten-second overall deadline, at most five redirects, a 64 KiB metadata limit, and a 5 MiB image limit. Images must have an image MIME type and cannot be SVG.

Android and JVM use a dedicated OkHttp transport. Apple uses Network.framework to connect to a validated numeric address while verifying TLS against the original hostname; Ktor's HTTP parsing utilities handle response framing. Browser JavaScript is unsupported because it cannot enforce the same connection-address policy.

The owning session creates the HTTP client lazily. Suspending `close()` is idempotent, cancels active fetches, and awaits their request cleanup. Initialization and closure share a lifecycle lock; requests after closure return `null` and cannot recreate the client.

OpenGraph interpretation, image metadata inspection, asset files, and domain failure mapping belong to `logic`. This module has no dependency on authenticated Wire API clients, persistence, or `logic`. Policy and fetch tests live here alongside platform transport tests; repository tests in `logic` use a fake fetcher.
