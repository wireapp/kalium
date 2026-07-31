### Added
- Added the SSO identity provider ID to authenticated session data.
- Added `SsoIdentityChanged` to detect retained accounts authenticated through a different identity provider.

### Changed
- Account updates no longer discard a previously stored SSO identity provider ID when the incoming session does not provide one.

### Migration
Consumers that exhaustively handle `AddAuthenticatedUserUseCase.Result.Failure` must handle `SsoIdentityChanged`.
Previously compiled JVM consumers must be recompiled because `StoreSessionParam` gained a constructor parameter.

### Compatibility
ABI: breaking for previously compiled consumers constructing or copying `StoreSessionParam`; otherwise additive.
Source: additive, except for exhaustive handling of `AddAuthenticatedUserUseCase.Result.Failure`.
Behavior: retained SSO accounts can now report an identity provider change instead of reusing local data.
