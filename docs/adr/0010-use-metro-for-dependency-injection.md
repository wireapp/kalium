# 10. Use Metro for Dependency Injection

Date: 2026-07-13

## Status

Accepted

## Context

Kalium currently performs dependency injection manually in `GlobalKaliumScope` and
`UserSessionScope`. Feature-facing classes such as `ConversationScope`, `MessageScope`, and
`UserScope` receive every dependency needed by every API they expose.

For example, creating `ConversationScope` evaluates approximately forty constructor arguments even
when a consumer requests a use case that only needs `ConversationRepository`. Several of those
arguments are accessors that create repositories, handlers, or other feature scopes. The same
pattern is repeated across the user session and makes object ownership, reuse, and initialization
cost difficult to reason about.

`UserSessionScope` also starts session workers from its initializer. These workers resolve much of
the dependency graph immediately. Dependency injection can make construction lazy, but it cannot
defer dependencies that are deliberately used by eager startup work. Dependency ownership and
session startup therefore need to be treated as separate concerns.

Kalium is a Kotlin Multiplatform SDK and publishes APIs to Android, JVM, and Apple consumers. Any DI
implementation must support all those targets, preserve the existing public API, avoid runtime
reflection, and maintain explicit user-session teardown.

## Decision

Adopt Metro as Kalium's compile-time dependency injection framework and migrate to it incrementally.

### Graph lifetimes

Kalium will have two primary graph lifetimes:

- A global graph for process-wide services.
- A user-session graph created once for each logged-in user and discarded on logout.

Feature API groupings such as `ConversationScope`, `MessageScope`, and `UserScope` are not separate
lifetimes. They remain public facades over entry-point interfaces exposed by the user-session graph.
They must not receive feature-wide dependency bags.

### Binding lifetimes

Bindings use the shortest lifetime that preserves their required identity and state. Usage
frequency alone does not determine a binding's lifetime; ownership, mutable state, active jobs,
caches, subscriptions, and resource cleanup are the deciding factors.

Kalium uses four lifetime categories:

| Binding kind | Lifetime |
| --- | --- |
| Process-wide immutable services | Global graph |
| Hot, shared, or stateful session services, such as conversation, calling, and sync state | User-session graph |
| Optional, expensive features, such as backup or Cells | Lazily created feature graph |
| Cheap stateless repositories and use cases | Unscoped or operation-local |

A binding annotated with `@SingleIn(UserSessionLifetime::class)` is shared by all entry points for
one user session. It is initialized lazily on first use and retained until the session graph is
released. Different users and a new login after logout receive different instances.

Optional resource-heavy features use child feature graphs rather than user-session-scoped bindings.
The feature facade or an internal feature runtime owns the child graph. Closing the feature must
cancel its work, dispose resource-owning bindings, and drop the graph reference. This releases the
whole feature subgraph together and avoids individually evicting repositories whose dependencies
may still retain them.

Use cases are unscoped by default because most are cheap wrappers and the existing API returns a new
instance from property accessors. A use case is scoped only when it owns state or requires stable
identity.

The existing `kalium.providerCacheScope` policy remains authoritative for provider-level caches.
Metro graph scoping does not replace the `LOCAL` or `GLOBAL` cache behavior of
`UserStorageProvider`, `UserAuthenticatedNetworkProvider`, or future provider caches.

### Eviction and disposal

Metro scopes define ownership; they are not usage-based eviction caches. `Lazy` and `Provider`
defer creation but do not clear a scoped instance after it has been created.

Kalium does not use weak references for scoped bindings. Garbage-collection-driven eviction can
discard mutable state unpredictably and cannot guarantee that jobs, subscriptions, sockets, or
native resources are closed. Automatic idle-time eviction is also not part of the initial design,
because an active `Flow` collector or background job may still be using a repository even when no
public method is currently executing.

Any scoped binding that owns work or closeable resources must have an explicit disposal contract.
Session logout and feature shutdown invoke disposal before dropping their graph references. Bindings
that do not own resources need no clearing API; releasing the owning graph makes them eligible for
garbage collection once consumers release any remaining references.

Usage-based eviction may be introduced only for a measured memory problem. It must track active
operations and `Flow` collectors, prevent eviction races, dispose deterministically, and be covered
by concurrency and lifecycle tests.

### Public API and module boundaries

Metro graphs, binding containers, scope keys, and entry-point interfaces are internal to `:logic`.
Existing public scope classes and their properties remain unchanged so Metro types do not become
part of Kalium's published ABI.

Metro is initially applied only to `:logic`, where the current manual graph is assembled. Other
modules continue to expose ordinary constructors and interfaces. Applying Metro to another module
requires a demonstrated need and must preserve the dependency direction defined by ADR 4.

Initial graphs use explicit binding containers and entry-point interfaces. Contribution-based
aggregation can be considered after the basic graph has proven stable across all supported KMP
targets.

### Function types

Metro's function-provider interpretation is disabled during the initial migration. Kalium uses
function types extensively as callbacks and operations, and those must not silently become DI
providers. Deferred dependencies use `Lazy<T>` or Metro's explicit `Provider<T>` until callback
bindings have named `fun interface` types.

### Session startup

Dependency graph creation must not implicitly start feature work. Existing session startup work will
be moved behind an explicit session runtime boundary. Mandatory session services can still start at
login, but optional feature workers should only be resolved and started when their owning feature is
enabled.

### Session teardown

Logout cancels and drains user-session work, disposes resource-owning session and feature bindings,
removes the user-session graph from its owner, and clears the existing user storage and authenticated
network provider entries. Dropping the graph reference clears Metro's scoped-instance ownership; no
Metro cache-clearing API is required.

Application code can retain an old scope, use case, or repository reference after logout. Kalium
cannot forcibly collect such references, so disposed resource-owning bindings must reject or safely
handle further use. Before a resource-owning repository is migrated into Metro, the session teardown
boundary must be able to dispose it explicitly.

### Migration and verification

Migration proceeds in independently reviewable vertical slices:

1. Add Metro to `:logic` and create the user-session graph foundation.
2. Migrate conversation entry points while preserving `ConversationScope`.
3. Migrate message and user entry points.
4. Introduce child feature graphs for optional resource-heavy features.
5. Extract explicit session startup and teardown from `UserSessionScope`.
6. Migrate the remaining user-session and global bindings.

Each slice must include tests proving graph lifetime and lazy allocation behavior. Public API dumps
must remain unchanged unless a separate API change is explicitly approved.

## Consequences

**Easier:**

- Requesting one use case creates only its transitive runtime dependencies.
- Shared repositories have an explicit user-session lifetime and are created once per user.
- Dependency cycles and missing bindings are detected at compile time.
- Feature facades no longer need long constructor parameter lists.
- Tests can replace bindings at the graph boundary without constructing unrelated features.
- Object ownership and logout teardown become visible in the graph structure.
- Optional feature graphs can release expensive subgraphs before logout.

**More difficult:**

- Metro adds a compiler plugin and runtime dependency to `:logic`.
- Kotlin compiler upgrades must stay within Metro's supported compatibility window.
- The initial migration temporarily contains both Metro and manual construction paths.
- Raw function-type callbacks require care while function-provider interpretation is disabled.
- Eager session workers still instantiate their dependencies until startup is separated.
- Resource-owning bindings require explicit, idempotent disposal contracts.
- Feature lifetimes require a clear owner that signals when the feature is finished.
- Generated graph wiring exists for all declared entry points even though scoped service instances
  remain lazy.

**References:**

- [Metro dependency graphs](https://zacsweers.github.io/metro/latest/dependency-graphs/)
- [Metro scopes](https://zacsweers.github.io/metro/latest/scopes/)
- [Metro adoption strategies](https://zacsweers.github.io/metro/latest/adoption/)
- [Metro Kotlin compiler compatibility](https://zacsweers.github.io/metro/latest/compatibility/)
