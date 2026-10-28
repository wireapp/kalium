# Data Layer

Data access and transformation layer handling persistence, networking, and mapping.

**Modules:**
- `network` - Network implementation (Ktor client, API calls, WebSocket)
- `network-model` - DTOs, API request/response models
- `network-util` - Network state observation and utilities
- `persistence` - Database operations (SQLDelight)
- `persistence-test` - Test utilities for persistence layer
- `protobuf` - Protocol buffer message definitions
- `data-mappers` - Model and exception mapping, transport wrappers, and logging initialization

**Dependencies:** Core modules and other data modules

`data-mappers` converts concrete network/crypto exceptions into `:core:failures` contracts.
It also owns `CoreLogger`, which initializes logging across infrastructure modules. SDK consumers
receive the failure contract through `api`; adapter dependencies stay `implementation`. Shared
logger access and `Either` remain in `:core:common`.

See the [migration guide](../docs/guides/core-infrastructure-migration.md) for the moved low-level APIs.

**Used by:** Domain layer, Logic layer
