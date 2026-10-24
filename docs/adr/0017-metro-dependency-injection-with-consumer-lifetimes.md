# 17. Migrate Kalium to Metro with explicit dependency lifetimes

Date: 2026-10-20

## Status

Proposed

## Context

Kalium wires repositories, use cases and background work through handwritten
classes such as `GlobalKaliumScope`, `UserSessionScope`, `ConversationScope` and
`MessageScope`. A ViewModel that needs one conversation use case and one message
use case reaches the constructor dependencies of both feature containers.
`UserSessionScope.conversations` also resolves several members of `messages`.
Simple read operations therefore pull in unrelated feature dependencies.

The conversation and message containers are cached with `by lazy`; each
ViewModel does not recreate them. The cost comes from broad first-access
construction and session startup. `UserSessionScope.init` starts workers that
access the same feature containers before a ViewModel requests them.

The existing ownership rules are mixed. Some objects are retained by the global
owner, user session or feature container. Other getters create fresh wrappers.
A fresh wrapper can also be captured once by a cached feature constructor and
shared by all use cases from that container. Storage and network providers have
an explicit `kalium.providerCacheScope` policy for process-global or local
caches. Replacing all of these with one scope would change their behavior.

Android already uses Metro, but its providers still delegate to Kalium's manual
feature containers. The migration must happen inside Kalium. It must also allow
an ephemeral repository to be shared by several use cases belonging to one
ViewModel, without retaining that repository for the whole user session.

## Decision

Use Metro to resolve individual bindings in Kalium. Keep constructor injection
explicit, place binding declarations near their implementations, and use
providers for platform factories, external instances and lifetime-specific
construction. Feature packages organize bindings; they do not imply separate
runtime graphs. Do not move the large feature constructors into Metro providers.

### Global, user and consumer ownership

Use graph extensions for nested lifetimes and retain graphs through explicit
owners. The scope names below describe the proposed roles; they are internal
implementation details.

| Lifetime | Owner and sharing rule |
| --- | --- |
| `KaliumLifetime` | One graph per existing `CoreLogic` owner. Global bindings are shared with its user graphs. Existing process-global providers keep their separate cache policy. |
| `UserLifetime` | One active graph per qualified user identity and session generation, retained by Kalium. Existing user-shared state and resources remain shared across consumers. |
| `ConsumerLifetime` | One graph per creator instance, such as a ViewModel or an explicitly owned operation. Selected ephemeral bindings are shared within that consumer and differ between consumers. |
| Unscoped | Resolve a fresh value where the current contract or use case requires it. Consumer sharing is an explicit binding decision. |

Keep authentication as a separate branch keyed by the existing server
configuration and proxy credentials. Login work must not require an
authenticated user graph. Preserve other existing ownership distinctions,
including feature containers currently returned fresh by getters.

`@SingleIn` caches one binding per matching graph instance. It does not create a
process-wide singleton, infer the creator's lifecycle, or automatically deduplicate
unscoped dependencies during construction. Apply it to a provider when sharing
depends on the consumer rather than making the repository class universally
scoped. Contributing a binding to a scope does not itself cache its instances.

### Sharing ephemeral repositories within a consumer

Kalium exposes a framework-neutral consumer factory and a narrow API of
individual use cases. A consumer graph extends the user's graph and obtains
existing shared resources from that parent. Its repository providers can use
`@SingleIn(ConsumerLifetime::class)` while the use cases themselves remain
unscoped.

For example, two use cases constructed for ViewModel A can receive the same
consumer-scoped `MessageRepository`. ViewModel B gets another consumer graph and
repository. Both repositories use the same user-owned database and authenticated
network resources. A repository that is already shared by the user session stays
owned by the user graph instead.

Construct use cases that need consumer-owned dependencies within the consumer
graph. Importing already-created use cases from a user API cannot change the
repositories those use cases hold. A parent-owned binding must not depend on
consumer-owned state. Where APIs are composed with Metro's factory `@Includes`
parameter, expose the shared dependencies or factories needed by the child and
resolve its individual use cases there. Keep one binding path per type and
qualifier.

Android creates the consumer inside the actual ViewModel creation path and ties
it to that ViewModel instance. Recomposition and configuration changes must not
create a new consumer for a retained ViewModel. An Activity-retained UI session
graph may expose factories, but must not cache consumer-owned use cases or
resources. ViewModels remain owned by their existing `ViewModelStoreOwner`.
Other platforms and background operations supply equivalent explicit owners.

Consumer sharing deliberately permits selected formerly transient repository
wrappers to be reused within one creator. Apply it after checking their mutable
state and whether references escape that owner. It must not narrow the lifetime
of existing user-shared state or promote every transient object to a singleton.

### Preserve existing identity and platform behavior

Inventory declarations and capture sites before migrating a binding. Preserve
existing global and user caches, cached use cases, mutable flows, mutexes,
schedulers, typing state, delivery state and self-deletion handlers. A getter's
factory semantics alone do not describe the lifetime of an instance captured by
another object. Use distinct qualified bindings where necessary to preserve
those captures and aliases across features.

Continue obtaining storage and authenticated network resources through the
existing providers, including their removal paths and `GLOBAL` versus `LOCAL`
policy. A per-graph cache cannot replace a process-global provider cache shared
across `CoreLogic` or provider instances.

Keep public SDK types and accessor signatures compatible during migration.
Existing facades delegate to migrated bindings while retaining their existing
ownership and getter behavior. New consumers opt into the consumer API; a
legacy accessor must not silently open an unrelated consumer on each call.

Keep common contracts, scope markers, bindings and consumer extensions in shared
Kotlin code where possible. Place final annotated root graphs in platform source
sets when they must aggregate platform contributions. Validate the Kotlin and
Metro compiler versions together in Kalium's standalone build and Android's
consumer build. Metro `1.4.5` and Kotlin `2.3.20`, already selected by Android,
are the initial integration baseline, subject to the required platform checks.

### Startup, invalidation and cleanup

Extract the current session initialization into explicitly started user runtime
work. Preserve its required timing and platform conditions. Workers depend on
individual bindings rather than feature containers. Required startup work can
still construct dependencies; lazy DI does not eliminate that requirement.

Kalium continues to own concurrent user graph creation, account switching,
invalidation, logout and session replacement. Repeated requests for an active
user return the same graph. Logout prevents new consumers from using the outgoing
generation and quiesces existing consumers before the established platform
shutdown. A later login for the same user receives a fresh generation.

Consumer disposal releases only consumer-owned work and resources. User and
global owners retain responsibility for their resources. Scope annotations do
not cancel coroutines or close resources; cleanup remains explicit and must not
initialize an unused lazy resource merely to close it. Preserve existing drain,
native shutdown, cleanup failure and account ownership rules.

On Apple, keep asynchronous account preparation and lease acquisition before
exposing synchronous cached access. Notification and share extensions retain
their restricted runtimes and do not resolve the main application's full user
graph. See [NSE account runtime ownership](0011-nse-account-runtime-ownership.md)
and [sharing while the app owns the account](0013-sharing-while-the-app-owns-the-account.md).

### Incremental migration

1. Record current identity and capture relationships and measure allocations and
   construction time for cold session startup, first consumer creation and warm
   recreation. Separate mandatory background work from requested dependencies.
2. Add Metro to Kalium, introduce internal graph contracts and platform roots,
   and retain the existing resource providers, registries and lifecycle owners.
3. Migrate `ObserveConversationDetailsUseCase` and `ObserveMessageByIdUseCase`
   through one consumer graph and the real ViewModel factory path. Their new
   bindings must not resolve the old conversation or message containers. Route
   compatibility accessors through bindings that preserve their prior behavior.
4. Migrate stateful bindings and startup workers in feature slices. Remove old
   bridge providers for each migrated binding and remove broad feature
   constructors once no consumer depends on them.

Before completing the migration, verify reference identity within and across
consumers, users and graph generations; factory behavior; concurrent first
resolution; invalidation during creation; and both provider cache policies.
Verify that unused feature handlers are not constructed unless required startup
work requests them. Run Android, JVM and Apple builds, public API/ABI checks,
and existing account lifecycle and extension checks. Measure the resulting
allocation and startup changes rather than assuming a performance improvement.

### Alternatives considered

- Making manual feature dependencies lazy reduces some eager work but retains
  handwritten wiring and requires each container to implement sharing correctly.
- Changing only Android's Metro providers leaves Kalium's broad construction
  paths in place and does not help other SDK consumers.
- Making every repository a user singleton changes identity and retention for
  ephemeral wrappers. Explicit consumer ownership provides narrower sharing.
- Keeping every ephemeral binding unscoped preserves fresh construction but
  cannot guarantee one shared instance across a creator's use cases.

## Consequences

Use cases can span feature packages without constructing both feature
containers. Existing shared state keeps its owner, and selected ephemeral
repositories can be reused within one consumer without being retained for the
whole session. Metro validates the declared dependency graph at compile time.

The migration adds consumer factories and explicit lifetime adapters. It
temporarily retains compatibility wiring and requires Kotlin compiler plugin
updates to be validated across supported platforms. Consumer sharing is a
deliberate identity change for selected transient bindings and needs verification.
Construction savings depend on the dependency paths required by actual startup
and consumer work.

This ADR proposes the migration. It does not change runtime behavior, public API
or ABI by itself.

## References

- [Current Kalium architecture](../ARCHITECTURE.md)
- [Current user session construction](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/UserSessionScope.kt)
- [Current user session registry](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/UserSessionScopeProvider.kt)
- [Provider cache policy](../CONTRIBUTING.md#kaliumprovidercachescope)
- [Metro 1.4.5 graph extensions and factory inputs](https://github.com/ZacSweers/metro/blob/1.4.5/docs/dependency-graphs.md)
- [Metro 1.4.5 binding scopes](https://github.com/ZacSweers/metro/blob/1.4.5/docs/scopes.md)
- [Metro 1.4.5 multiplatform graph placement](https://github.com/ZacSweers/metro/blob/1.4.5/docs/multiplatform.md)
