# Public failure contracts

This module owns failure types exposed by Kalium APIs. It depends on domain models and serialization;
it must not depend on network, persistence, protobuf, crypto implementations, or `:data:data-mappers`.

`ServerError`, `MlsErrorDetails`, and `ProteusFailure.Code` describe errors without transport types.
Optional `Throwable` causes are diagnostic information. Control flow must use the normalized details
and work when there is no cause.

Concrete exceptions are converted in `:data:data-mappers`. SDK and domain modules export this module
with `api` and use the adapters through `implementation`. See the
[migration guide](../../docs/guides/core-infrastructure-migration.md).
