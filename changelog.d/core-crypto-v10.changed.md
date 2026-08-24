Migrated Kalium to the official Core Crypto 10.4 Kotlin Multiplatform API and its managed X.509 acquisition flow.

  - ABI: breaking for `E2EIEnrollmentResult.Initialized`; legacy ACME nonce, order, and authorization fields were removed.
  - Source: consumers constructing or inspecting `E2EIEnrollmentResult.Initialized` must use `target`, `oAuthClaims`, and `isNewClientRegistration` only.
  - Behavior: Core Crypto now owns ACME/DPoP/OIDC state, credential references, revocation checks, and buffered MLS-message replay.
  - JavaScript: `core:cryptography` uses the official Core Crypto 10.4 browser npm/Wasm package behind the shared Kotlin API, and the module targets, browser tests, SQLDelight Wasm support, and backup npm publication that existed before the migration are restored.
  - Migration: pass the ID token returned by the identity-provider flow to `finalizeEnrollment`; the `oAuthState` parameter remains accepted for compatibility but is no longer consumed by Core Crypto 10.
