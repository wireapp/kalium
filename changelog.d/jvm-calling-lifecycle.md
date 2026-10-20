### Added
- Add opt-in JVM calling and media providers with acknowledged handle readiness, shutdown, per-call media admission, and camera transitions.
- Retain native callback ownership until shutdown completes and allow failed release to be retried.

### Compatibility
Android keeps its existing AVS binding. JVM applications must register their process-owned `LifecycleCalling` and `JvmCallMedia` implementations before enabling calling. Provider registration does not initialize AVS or acquire devices. Speaker state is emitted only for an observed hardware route.
