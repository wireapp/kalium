# Architecture

Kalium is a Kotlin Multiplatform SDK organized by layer. Dependency direction is intentionally one-way:

```text
core -> data -> domain -> logic
```

Feature code should stay within the narrowest layer that can own it. The `:logic` module is the public SDK entry point and coordinates the lower layers.

## Layers

### `core:*`

Foundation modules shared by the rest of the SDK:

- `:core:common`: error handling, `Either`, shared utilities
- `:core:data`: public and internal data models
- `:core:cryptography`: CoreCrypto and libsodium integration
- `:core:libsodium`: libsodium bindings
- `:core:logger`: logging infrastructure
- `:core:util`: platform utilities and serialization helpers

Core modules may depend on other core modules and external libraries.

### `data:*`

Persistence, networking, and model transformation:

- `:data:network`: Ktor HTTP client, retry logic, authentication, WebSocket APIs
- `:data:network-model`: request and response DTOs
- `:data:network-util`: network state and shared network helpers
- `:data:persistence`: SQLDelight databases and DAOs
- `:data:persistence-test`: database test fixtures
- `:data:data-mappers`: network/persistence/domain mappers
- `:data:protobuf`: protobuf definitions and generated access

Data modules depend on core modules.

### `domain:*`

Feature domains and business workflows:

- `:domain:backup`
- `:domain:calling`
- `:domain:cells`
- `:domain:conversation-history`
- `:domain:messaging:sending`
- `:domain:messaging:receiving`
- `:domain:nomaddevice`
- `:domain:usernetwork`
- `:domain:userstorage`
- `:domain:work`

Domain modules depend on core and data modules.

### `:logic`

The SDK facade consumed by clients. It wires repositories, use cases, scopes, network access, persistence, and feature domains into public APIs such as `CoreLogic`, `GlobalKaliumScope`, and `UserSessionScope`.

Rules for `:logic`:

- Expose concrete result types, not `Either`.
- Keep constructor injection explicit.
- Define use cases as interfaces or functional interfaces in the relevant package.
- Keep implementation classes close to their public contract unless an existing package pattern says otherwise.

### Support Modules

- `:sample:cli`: command-line app for manual testing and debugging
- `:sample:samples`: small API usage examples
- `:tools:*`: developer tools
- `:test:*`: mocks, fixtures, benchmarks, and integration-test support

## Source Sets

Most modules use Kotlin Multiplatform source sets:

- `commonMain` and `commonTest` for shared code
- `jvmMain` and `jvmTest` for JVM-specific code
- `androidMain`, `androidHostTest`, and `androidDeviceTest` for Android
- `appleMain`, `iosMain`, and Apple target source sets for iOS/macOS
- `jsMain` and `jsTest` where JavaScript support is enabled

Prefer `commonMain` and `commonTest` when the behavior is genuinely platform-neutral.

## Persistence

Kalium uses SQLDelight with two databases:

- `UserDatabase` under `db_user/` for user-specific data such as messages and conversations
- `GlobalDatabase` under `db_global/` for shared data such as accounts and server configuration

Schema files live under:

```text
data/persistence/src/commonMain/db_*/com/wire/kalium/persistence/*.sq
```

Migrations live under:

```text
data/persistence/src/commonMain/db_*/migrations/
```

Use `:data:persistence-test` fixtures for database tests.

## Architectural Decisions

Architectural decisions are recorded in [ADR files](adr/). Add a new ADR before introducing a new dependency, new module relationship, or new architectural pattern.

## Dependency Graph

The module graph Gradle task writes the generated dependency graph here when the project needs a full module-level view.
