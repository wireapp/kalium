# Keep foundational core modules independent of infrastructure

`:core:common` previously pulled network, persistence, and crypto implementations into builds of
shared helpers, while `:core:data` depended on network DTOs. Move the infrastructure failure
hierarchy, exception adapters, logging initialization, and DTO-bearing wrappers to the existing
`:data:data-mappers` module. Keep `Either`, logger access, structured logging, and domain models in
core so compiling these foundations does not require the infrastructure projects.

The complete sealed failure hierarchy moves together because it exposes concrete network and
crypto exceptions. A forwarding compatibility dependency from core would preserve the build
coupling this change removes. We therefore accept a breaking change to low-level module ownership,
preserve Kotlin package names and runtime behavior, and expose the moved contracts through the SDK
facade and domain APIs that return them. No new module is required; adapter consumers still compile
the infrastructure they actually use. Direct consumers must update dependencies and rebuild as
described in the [migration guide](../guides/core-infrastructure-migration.md).
