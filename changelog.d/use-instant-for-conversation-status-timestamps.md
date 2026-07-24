### Changed
- Conversation archive and mute use cases now accept `Instant` timestamps instead of epoch-millisecond `Long` values.

### Migration
Pass an `Instant` directly when supplying a custom status timestamp.

### Compatibility
ABI: breaking.
Source: breaking for consumers that pass a custom `Long` timestamp.
Behavior: no behavior change.
