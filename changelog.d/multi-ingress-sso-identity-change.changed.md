Account updates no longer discard a previously stored SSO identity provider ID when the incoming session does not provide one.

  - ABI: breaking for previously compiled consumers constructing or copying `StoreSessionParam`; otherwise additive.
  - Source: additive, except for exhaustive handling of `AddAuthenticatedUserUseCase.Result.Failure`.
  - Behavior: retained SSO accounts can now report an identity provider change instead of reusing local data.
