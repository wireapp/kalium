### Added

- Added experimental capability artifacts and a JVM headless calling-service composition with
  explicit session, crypto, event-delivery, conversation, calling, and lifecycle dependencies.
- Added `:logic:client` as an explicit full-client composition entry point while preserving the
  existing `:logic` API.

### Migration

Existing `:logic` consumers do not need to change. Headless service consumers must supply durable
identity-scoped session, crypto, event checkpoint/idempotency, and conversation protocol state and
must opt in to the experimental service API.

### Compatibility

ABI: additive; new JVM/Apple artifact surfaces have independent baselines, the Android client
entry point has a class-signature baseline, and the existing `:logic` baseline is unchanged.
Source: additive.
Behavior: no intended change for existing client consumers.
