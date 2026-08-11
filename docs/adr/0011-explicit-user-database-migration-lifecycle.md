# 11. Explicit lifecycle for long-running user database migrations

Date: 2026-07-29

## Status

Proposed

## Context

Kalium owns the SQLDelight schema and migrations for `UserDatabase`. Each signed-in account has its
own user database.

Today, the first call to `CoreLogic.getSessionScope(userId)` creates a `UserSessionScope` and opens
the database. Opening the database can run pending SQLDelight migrations. The API is synchronous,
so a slow migration can block the thread that asks for the session. This could be the Android or
iOS main thread.

Most migrations are quick. Some changes, such as adding an index to a large table or rebuilding a
table, can take much longer. The app must not use the user session until the database is ready.

Kalium can own database preparation, but it cannot own app UI or platform background rules. Android
and iOS must decide how to keep the user informed and how to handle the app moving to the
background.

This ADR covers `UserDatabase`. It does not change `GlobalDatabase` or the backup import flow.

## Decision

### Migration types

Kalium will use two kinds of database work.

#### Required schema migration

A migration is required when the new Kalium code needs the new schema before it can run.

Examples include:

- creating an index used by a current query;
- rebuilding a table;
- adding a required constraint;
- adding a table or column used by current DAOs.

A required migration:

- stays in the SQLDelight `.sqm` files;
- is also reflected in the `.sq` schema used for a new database;
- finishes before Kalium exposes the user session;
- runs on a Kalium I/O dispatcher;
- commits its SQL changes and schema version together;
- rolls back when it fails;
- can be tried again after an interruption.

Data changes that the new schema needs are also required migrations. They must not be moved to a
background task only to provide percentage progress.

#### Deferred data migration

A migration is deferred only when old and new code can safely work while the data is updated.

A deferred migration:

- handles a limited number of rows per run;
- saves its checkpoint with the data change;
- can continue after the process stops;
- keeps a query fallback until it finishes.

The first implementation does not add a deferred-migration framework. We will add one when there is
a real use case.

### Public API

`CoreLogic` will expose one operation that prepares the user session and one optional state stream:

```kotlin
public suspend fun prepareUserSession(
    userId: UserId
): PrepareUserSessionResult

public fun observeUserSessionPreparation(
    userId: UserId
): Flow<UserSessionPreparationState>
```

The result contains either the ready scope or a typed failure:

```kotlin
public sealed class PrepareUserSessionResult {
    public class Success internal constructor(
        public val sessionScope: UserSessionScope
    ) : PrepareUserSessionResult()

    public class Failure internal constructor(
        public val failure: UserSessionPreparationFailure
    ) : PrepareUserSessionResult()
}
```

The app can observe these states:

```kotlin
public sealed class UserSessionPreparationState {
    public data object NotStarted : UserSessionPreparationState()
    public data object OpeningDatabase : UserSessionPreparationState()
    public data object MigratingDatabase : UserSessionPreparationState()
    public data object Ready : UserSessionPreparationState()

    public class Failed internal constructor(
        public val failure: UserSessionPreparationFailure
    ) : UserSessionPreparationState()
}
```

Every typed failure retains the exact exception raised while opening or migrating the database:

```kotlin
public sealed class UserSessionPreparationFailure {
    public abstract val exception: Throwable

    public class InsufficientStorage internal constructor(
        public override val exception: Throwable
    ) : UserSessionPreparationFailure()

    public class TemporarilyUnavailable internal constructor(
        public override val exception: Throwable
    ) : UserSessionPreparationFailure()

    public class ApplicationUpdateRequired internal constructor(
        public override val exception: Throwable
    ) : UserSessionPreparationFailure()

    public class SupportRequired internal constructor(
        public override val exception: Throwable
    ) : UserSessionPreparationFailure()
}
```

Apps may handle the typed failure or rethrow `failure.exception` without losing its type, message,
cause chain, or stack trace. They remain responsible for deciding how much diagnostic detail is safe
to show to users.

Observing the state does not start preparation. The state is only for UI. The suspending
`prepareUserSession` operation is the gate that every database user must call.

`UserStorageProvider` owns one in-process entry per user database. The entry contains the cached
`UserStorage`, the shared preparation attempt, and the storage lifecycle state flow. The flow is
passive: collecting it only observes the entry and never opens the database. `:logic` maps that
storage state to the public session state and creates the `UserSessionScope` after storage is ready.

### How preparation works

Preparation follows this flow:

1. `UserStorageProvider` changes the state to `OpeningDatabase` and moves the work to its I/O
   dispatcher.
2. Kalium creates the normal platform SQLDelight driver but does not publish it yet.
3. Kalium forces the driver to open.
4. If an upgrade is needed, the driver changes the state to `MigratingDatabase` and runs the
   generated `UserDatabase.Schema.migrate` path.
5. When the driver opens successfully, `UserStorageProvider` caches `UserStorage` and changes its
   state to `Ready`.
6. `:logic` creates and caches `UserSessionScope` using that same storage and returns `Success` with
   the ready session scope.

Kalium does not run a general verification pass after the migration. A successful SQLDelight driver
open is proof that the required schema migration completed.

If one migration needs an extra data check, that check must run inside the migration transaction,
before the driver commits the new schema version. It is part of that migration, not a separate
preparation phase.

### SQLDelight owns schema migration

`UserDatabase.Schema` remains the runtime source of truth.

Kalium must:

- run SQLDelight's generated migration path;
- keep required changes in `.sqm` files;
- use the existing platform database driver, key handling, and storage protection;
- let the driver own the migration transaction and schema-version update;
- test that these rules hold for the Android and Apple drivers;
- use the same open driver when publishing the normal user database.

Kalium must not copy migration SQL, split it into a second migration engine, or create a plaintext
database copy.

The whole pending SQLDelight upgrade is one indeterminate `MigratingDatabase` state. Kalium will not
show percentage progress or treat each `.sqm` file as a durable checkpoint. The database schema
version committed by the driver is the durable state.

At the time of this decision, Kalium uses the Wire SQLDelight fork based on SQLDelight 2.3.2. Driver
behavior must be tested when that dependency changes.

Backup import, backup validation, and their existing migration helpers are outside the scope of this
ADR.

### Planned code changes

The change will follow the current module boundaries:

- `:data:persistence` will provide an internal way to force the normal user database driver to open
  and report when SQLDelight starts an upgrade.
- `:domain:userstorage` will own one entry and state flow per user database, coordinate its shared
  preparation attempt, and cache storage only after preparation succeeds.
- `:logic` will expose the public API, map storage failure types while retaining their original
  exceptions, and create `UserSessionScope` only after storage is ready.
- Android and iOS will call the new API from startup and every background entry point that needs the
  user database.

This does not add a dependency or change the dependency direction between modules.

### Concurrency, cancellation, and retry

There is one preparation operation for each physical user database in a process.

- Concurrent callers wait for the same operation.
- They receive the same cached `UserSessionScope` when it succeeds.
- A repeated call after `Ready` returns the cached scope immediately.
- Cancelling one caller only cancels that caller's wait. It does not cancel work used by another
  caller.
- A transient storage-full, busy, or locked failure allows a later call to start a new attempt.
- Other failures do not enter an automatic retry loop, but their original exceptions still reach
  every caller waiting for that preparation attempt.

If the process stops during migration, the next call opens the database again. SQLDelight and the
platform driver read the stored schema version and run any migration that was not committed. The app
scheduler is not proof that migration finished.

Each app must list every process and extension that can open the same database. If more than one
process can open it, preparation also needs an operating-system-backed per-database lock. No process
may wait for that lock on its main thread.

A failed migration must never cause Kalium to delete or recreate the user database automatically.

### Existing session API

The API will be introduced in steps:

1. Add `prepareUserSession` and the preparation state stream.
2. Update Android, iOS, workers, services, receivers, and extensions to call it before using a user
   session.
3. Deprecate `getSessionScope(userId)` and the `sessionScope` helper.
4. In a release that allows the behavior change, make the old getter cache-only. Calling it before
   successful preparation is a programming error and must fail fast instead of opening or migrating
   the database.

`prepareUserSession` returns the ready scope inside `Success`, so new callers do not need another
lookup API.

The additive API and later behavior change must follow the ABI and changelog rules in ADR 9.

### App responsibilities

Each app must prepare the active user before creating database-dependent UI, dependency-injection
graphs, workers, services, receivers, or extensions.

While a required migration runs, the app must:

- show a migration screen instead of holding the system splash screen;
- keep database-dependent features closed;
- show an action the user can take when preparation fails;
- never delete or recreate the database after a failure;
- defer incoming work or keep it in a database-independent durable queue.

Android normally starts preparation while the app is visible. A foreground service may be used when
product requirements say migration should continue in the background. WorkManager state is not
proof that migration completed.

iOS also starts preparation while the app is visible. It may request finite background time. If iOS
suspends or ends the process, the next launch tries again. `BGProcessingTask` is better suited to
deferred work than to required startup migration.

### Release requirements

Before shipping a required migration:

- SQLDelight migration verification must pass for every supported schema snapshot;
- tests must use the generated SQLDelight migration path;
- tests must confirm that schema changes and the schema version commit together;
- tests must cover interruption and retry with the real Android and Apple drivers;
- concurrency tests must cover two callers and cancellation of one caller;
- platform tests must show that migration does not run on the main thread;
- Android and iOS must audit all database entry points;
- low-storage and database-busy behavior must be tested;
- rollout must start with internal or beta users.

Metrics may contain only the migration ID, schema versions, duration, result, and approved coarse
database or device-size buckets.

An index migration must also include the query it improves, query-plan evidence, migration time,
peak disk use, database growth, read improvement, and write cost on representative database sizes.

## Consequences

**Easier:**

- Slow migration cannot start through the normal synchronous session getter.
- Android and iOS use the same preparation and failure contract.
- SQLDelight stays responsible for schema migration.
- Apps have a clear state for gating UI and background work.
- Process death is handled by opening the database and checking its committed schema version.

**More difficult:**

- Kalium needs a preparation boundary before it creates `UserSessionScope`.
- Every app database entry point must move to the new API.
- Real driver behavior needs platform tests.
- Multi-process access may need a platform file lock.
- The transition temporarily supports both explicit and implicit session opening.

## Out of scope

- `GlobalDatabase` preparation;
- backup import and backup migration helpers;
- a deferred-migration framework;
- Android foreground-service implementation;
- iOS background-task implementation;
- the minimum supported `UserDatabase` schema version.

## References

- [ADR 3: Database Migration Testing Framework](0003-database-migration-testing-framework.md)
- [ADR 7: Swift-Friendly Result Types](0007-swift-friendly-result-types.md)
- [ADR 9: Public API, ABI, and Changelog Governance](0009-public-api-abi-and-changelog-governance.md)
- [SQLDelight migrations](https://sqldelight.github.io/sqldelight/2.3.2/jvm_sqlite/migrations/)
- [Android long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Apple `BGProcessingTask`](https://developer.apple.com/documentation/backgroundtasks/bgprocessingtask)
