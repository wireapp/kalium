# Data Layer

Data access and transformation layer handling persistence, networking, and mapping.

**Modules:**
- `network` - Network implementation (Ktor client, API calls, WebSocket)
- `network-model` - DTOs, API request/response models
- `network-util` - Network state observation and utilities
- `persistence` - Database operations (SQLDelight)
- `persistence-test` - Test utilities for persistence layer
- `protobuf` - Protocol buffer message definitions
- `data-mappers` - Mapping between network models, persistence models, and domain models

**Dependencies:** Core layer only
**Used by:** Domain layer, Logic layer
