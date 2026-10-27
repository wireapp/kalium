# Data Layer

Data access and transformation layer handling persistence, networking, and mapping.

**Modules:**
- `network` - Network implementation (Ktor client, API calls, WebSocket)
- `network-model` - DTOs, API request/response models
- `network-util` - Network state observation and utilities
- `persistence` - Database operations (SQLDelight)
- `persistence-test` - Test utilities for persistence layer
- `protobuf` - Protocol buffer message definitions
- `data-mappers` - Model and exception mapping, infrastructure failure contracts, transport wrappers, and logging initialization

**Dependencies:** Core modules and other data modules

`data-mappers` owns the `com.wire.kalium.common.error` contracts because they expose network and
crypto exceptions. It also owns `CoreLogger`, which initializes logging across the infrastructure
modules. Shared logger access and `Either` remain in `:core:common`.

See the [migration guide](../docs/guides/core-infrastructure-migration.md) for the moved low-level APIs.

**Used by:** Domain layer, Logic layer
