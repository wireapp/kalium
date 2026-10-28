# Keep public failure contracts independent of infrastructure

`:core:common` previously pulled network, persistence, and crypto implementations into foundational
builds, while `:core:data` depended on network DTOs. Put the complete sealed failure hierarchy in a
small `:core:failures` module that depends on domain models and serialization only. Its public error
details use `ServerError`, `MlsErrorDetails`, and `ProteusFailure.Code`; concrete transport and crypto
types never appear in that contract. Optional `Throwable` causes remain available for diagnostics.

Keep exception conversion, MLS transport serialization, logging initialization, and DTO-bearing
wrappers in `:data:data-mappers`. SDK and domain modules export `:core:failures` with `api` and depend
on the adapters with `implementation`. Exporting the whole adapter module would expose unrelated
public mapper declarations to consumers and weaken compile avoidance. Shared functional helpers and
logger access remain in `:core:common`.

This intentionally breaks low-level constructors, error-detail properties, and module ownership.
Business decisions use normalized details instead of downcasting diagnostic causes. Preserve status
codes, labels, retry delays, qualified IDs, crypto classifications, and the MLS transport JSON shape
at the adapter boundary. Direct consumers must update dependencies and rebuild; see the
[migration guide](../guides/core-infrastructure-migration.md). Full SDK builds still compile the
infrastructure; a smaller core graph alone does not establish an end-to-end speedup.
