# Core Layer

Foundation layer providing low-level infrastructure and utilities.

**Modules:**
- `common` - Common error handling, functional programming utilities, and core initialization
- `cryptography` - Cryptographic operations (MLS, Proteus, E2EI)
- `data` - Core data models and domain entities
- `logger` - Logging infrastructure and configuration
- `util` - Utility functions, platform abstractions, and serialization

**Dependencies:** Core modules should only depend on other core modules and external libraries

**Used by:** Data layer, Domain layer, Logic layer
