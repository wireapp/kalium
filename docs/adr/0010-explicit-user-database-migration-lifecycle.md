# 10. Explicit lifecycle for long-running user database migrations

Date: 2026-07-29

## Status

Proposed

## Context

Kalium owns the SQLDelight schema and migrations for `UserDatabase`. Each signed-in account has its
own user database.

Today, the first call to `CoreLogic.getSessionScope(userId)` creates a `UserSessionScope`, which
creates `UserStorage` and opens the database. Opening the database can run pending SQLDelight
migrations. This happens through a synchronous API, so a slow migration can block the thread that
first asks for the session.

Most migrations are quick. Some schema changes, such as building an index on a large table or
rebuilding a table, can take much longer. The app must not use a database that is only partly
prepared, and the migration must not block the Android or iOS main thread.

Kalium can own the shared migration behavior, but it cannot own app UI or platform lifecycle rules.
Android and iOS have different ways to keep work running when the app moves to the background, and
neither platform guarantees that a process will stay alive.

This ADR covers `UserDatabase` first. `GlobalDatabase` is small, so it will keep its current opening
flow unless measurements show that it needs the same lifecycle.

## Decision

### Migration types

Kalium will distinguish between two types of database work.

#### Required schema migration

Use a required migration when the new Kalium code needs the new schema before it can run.

Examples include:

- creating an index;
- rebuilding a table;
- adding a required constraint;
- adding schema that DAOs use immediately.

A required migration:

- stays in the shared SQLDelight `.sqm` files;
- is also reflected in the `.sq` schema used for a new database;
- completes before Kalium exposes the user session and its DAOs;
- runs on a Kalium I/O dispatcher, never on the app main thread;
- commits its SQL changes and schema-version update in the same transaction;
- rolls back both the SQL changes and schema-version update when it fails;
- uses the database schema version, plus any required checks, as proof of completion;
- is safe to retry after interruption.

Existing `.sqm` migration history will not be moved to a new framework. New schema migrations will
continue to use SQLDelight's generated migration path.

Data transformation that the new schema or DAOs require belongs to this required migration path and
follows the same atomicity, rollback, and retry rules. It must not be moved to a chunked post-schema
workflow merely to expose percentage progress. If old and new code cannot safely coexist, the work
is not a deferred migration.

The app may show indeterminate progress. Kalium will not invent percentage progress when SQLite does
not provide reliable progress.

#### Deferred data migration

Use a deferred migration only when the old and new code can safely work while data is updated.
This also covers work that fetches data from the server only to prepare a new feature. That work
should not require a blocking schema migration or a full slow sync.

A deferred migration:

- processes a limited number of rows per run;
- saves its checkpoint in the same transaction as the data changes;
- can resume after the process stops;
- keeps a query fallback until the work is complete;
- is implemented once in shared Kalium code.

Android and iOS may schedule deferred work differently. A reusable deferred-migration framework will
be added when there is a concrete use case; it is not required for the first schema migration.

### Kalium responsibilities

The work will follow Kalium's existing module boundaries:

- `:data:persistence` owns schema inspection, SQLDelight migration execution, and verification.
- `:domain:userstorage` coordinates preparation for one user database at a time.
- `:logic` exposes a small, app-facing preparation API and a Swift-friendly result/state, following
  ADR 7.

The public API will be introduced in two steps:

1. Add a suspending preparation operation for a user session. It prepares the user database and
   caches the ready session.
2. After Android and iOS use that operation at startup, make `getSessionScope(userId)` return only a
   prepared session instead of doing a potentially slow first open.

The first step is additive. The second step is a behavior change and must follow the public API,
ABI, and changelog rules in ADR 9.

The preparation contract must provide enough state for an app to show:

- no preparation started;
- database opening or verification in progress;
- required schema migration in progress;
- ready;
- a failure that can be retried, such as low storage or temporary database contention;
- a failure that needs a fixed app version or support.

Exact API names and exported types belong in the implementation review. The ADR does not require the
larger `plan`/`execute`/`open` API proposed in the original design.

Kalium must also:

- allow only one preparation operation per user database in a process;
- allow concurrent callers to await that same operation without duplicating work;
- prevent cancellation of one caller from cancelling preparation still needed by other callers;
- re-read the schema version before running a migration;
- run the generated SQLDelight migration instead of copied or manually split migration SQL;
- ensure the migration and schema-version update commit together;
- open and publish the normal database only after migration and verification succeed;
- use the platform's existing database driver, key handling, and storage protection;
- never create a plaintext database copy as part of migration;
- avoid logging message content, encryption material, account identifiers, or database paths.

The session preparation path must not use the current backup-import `SqlDriver.migrate` helper as it
is today. That helper calls the generated migration directly, but it does not own the database
opening transaction or schema-version update. It may be reused only after it meets the same atomic
migration contract.

### App responsibilities

Each app must prepare a user session before creating database-dependent UI, dependency-injection
graphs, workers, services, receivers, or extensions for that user.

While a required migration runs, the app must:

- show a real migration screen instead of keeping the system splash screen visible;
- keep database-dependent features closed;
- show an actionable error for recoverable failures;
- avoid automatically deleting or recreating the database after a failure;
- defer incoming work or place it in a database-independent durable queue.

On Android, the app normally starts required preparation while it is visible. A database-independent
background entrypoint, such as durable notification-fetch work, may also prepare a session on demand
when platform execution and user-visible feedback requirements are satisfied. The app may use a
foreground service when product requirements say preparation should continue after the app is
backgrounded. WorkManager may record that app work is pending, but it is not part of the shared
correctness model and its state is not proof that preparation completed. It remains an option for
deferred data migrations.

On iOS, the app also starts required preparation while it is visible. It may request finite
background time when the app moves to the background. If iOS suspends or terminates the process, the
next launch inspects the database and retries. `BGProcessingTask` is suitable for deferred work, not
for a migration that must finish before the app can be used.

Platform scheduling state is never proof that a migration completed. The database schema version is
the source of truth.

### Concurrency and recovery

The in-process guard is required for every platform.

Before release, each app must list every process or extension that can open the same database. If
more than one process can open it, the implementation must add an operating-system-backed
per-database lock. SQLite locking protects the file, but another process must not wait for a long
migration on its main thread.

An interrupted migration is not marked as complete. The next attempt inspects the database and
starts from the last schema version committed by the driver. The current driver may treat the full
pending upgrade range as one transaction, so retrying may rerun that full range. This ADR does not
assume that every `.sqm` file creates a separate checkpoint.

Temporary failures may use bounded retry. Invalid SQL, a wrong encryption key, corruption, or a
failed post-migration check must stop automatic retries and return a controlled error. Migration
failure must never silently delete user data.

### Release requirements

Every required migration must meet these conditions before release:

- SQLDelight migration verification passes from every supported schema snapshot.
- The minimum supported schema version is documented and covered by a schema snapshot.
- Tests execute the generated SQLDelight migration path, not a copied SQL script.
- Tests verify that the schema version changes only when the migration commits.
- Tests follow the remaining migration-testing rules from ADR 3.
- Concurrency tests verify single-flight preparation and cancellation of one waiter.
- The real Android and Apple drivers are tested for upgrade, interruption, and retry behavior.
- Android and iOS tests show that migration work does not run on the main thread.
- Each app has an audited list of database consumers and none can bypass preparation.
- Low-storage and database-busy behavior is tested.
- Metrics contain only migration ID, schema versions, duration, result, and approved coarse device
  or database-size buckets.
- Rollout starts with internal/beta users before a staged production release.

For an index migration, the change must also include:

- the exact query it improves;
- query-plan evidence that the intended index is used;
- migration time and peak disk-use measurements on representative database sizes;
- read improvement, database growth, and write-cost measurements.

The implementation must not rely on unlimited Android or iOS background time.

## Consequences

**Easier:**

- Android and iOS use the same migration and recovery rules.
- A slow migration cannot accidentally run during synchronous session creation on the main thread.
- Apps have a clear state to gate UI and background work.
- Process death is handled by inspecting and retrying, not by trusting scheduler state.
- Existing SQLDelight `.sqm` files remain the source of schema changes.
- Existing migration history does not need to be rewritten.

**More difficult:**

- Kalium needs a new preparation boundary before `UserSessionScope` is exposed.
- Android and iOS must update startup and every non-UI database entry point.
- Multi-process or app-extension access may require a platform file lock.
- Users with large databases may wait on a migration screen.
- Migration tests must cover the generated path and real platform drivers.
- The staged API rollout temporarily supports both the old implicit open and the new explicit
  preparation path.

**Not decided here:**

- the Android foreground-service implementation;
- the full deferred-migration API and checkpoint schema;
- the exact minimum supported `UserDatabase` schema version.

**References:**

- Wire Android ADR 14: Android integration for explicit Kalium user database preparation
- [ADR 3: Database Migration Testing Framework](0003-database-migration-testing-framework.md)
- [ADR 7: Swift-Friendly Result Types](0007-swift-friendly-result-types.md)
- [ADR 9: Public API, ABI, and Changelog Governance](0009-public-api-abi-and-changelog-governance.md)
- [SQLDelight migrations](https://sqldelight.github.io/sqldelight/2.0.2/native_sqlite/migrations/)
- [Android long-running workers](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Apple `BGProcessingTask`](https://developer.apple.com/documentation/backgroundtasks/bgprocessingtask)
