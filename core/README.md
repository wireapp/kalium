# Core Layer

Foundation layer providing low-level infrastructure and utilities.

**Modules:**
- `common` - Functional programming utilities, shared logger access, and structured logging
- `cryptography` - Cryptographic operations (MLS, Proteus, E2EI)
- `data` - Domain models without network DTO dependencies
- `logger` - Logging infrastructure and configuration
- `util` - Utility functions, platform abstractions, and serialization

**Dependencies:** Core modules should only depend on other core modules and external libraries

`common` and `data` must also stay independent of crypto implementations. Infrastructure failures,
exception conversion, DTO wrappers, and logging initialization live in `:data:data-mappers`.
See the [migration guide](../docs/guides/core-infrastructure-migration.md) for direct consumers of the moved APIs.

**Used by:** Data layer, Domain layer, Logic layer
