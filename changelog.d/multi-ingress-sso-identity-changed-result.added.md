Added `SsoIdentityChanged` to detect retained accounts authenticated through a different identity provider.

  - ABI: additive.
  - Source: additive, except for exhaustive handling of `AddAuthenticatedUserUseCase.Result.Failure`.
  - Behavior: retained SSO accounts can now report an identity provider change instead of reusing local data.
  - Migration: consumers that exhaustively handle `AddAuthenticatedUserUseCase.Result.Failure` must handle `SsoIdentityChanged`.
