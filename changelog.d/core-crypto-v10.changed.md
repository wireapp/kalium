Migrated Kalium to the official Core Crypto 10.4 Kotlin Multiplatform API and its managed X.509 acquisition flow.

  - ABI: breaking for `EnrollE2EIUseCase`; the two-step `initialEnrollment`/`finalizeEnrollment` API and its intermediate result types were replaced by one `invoke` operation with an authentication callback.
  - ABI: `CryptoCredentialRef` now exposes its credential type so same-key Basic and X.509 credentials remain distinguishable.
  - Source: call `enrollE2EI(isNewClientRegistration) { request -> ... }`, authenticate against `request.target` with the `keyAuth` and `acmeAudience` claims, and return the ID token.
  - Behavior: Core Crypto now owns ACME/DPoP/OIDC state and stays alive in memory while authentication runs. Credential acquisition, installation, key rotation, and conversation migration complete in the same invocation; only post-acquisition rotation recovery is persisted.
  - JavaScript: `core:cryptography` uses the official Core Crypto 10.4 browser npm/Wasm package behind the shared Kotlin API, and the module targets, browser tests, SQLDelight Wasm support, and backup npm publication that existed before the migration are restored.
  - Migration: remove persisted enrollment-initialization state and the separate finalization call. Keep the suspending Kalium invocation active while the app presents OAuth, then return the resulting ID token from the callback.
