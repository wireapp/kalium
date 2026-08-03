Consumers that exhaustively handle `AddAuthenticatedUserUseCase.Result.Failure` must handle `SsoIdentityChanged`.

Previously compiled JVM consumers must be recompiled because `StoreSessionParam` gained a constructor parameter.
