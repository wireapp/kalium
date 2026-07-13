# iOS NSE Lightweight Kalium — Milestone 10 Rollout and Observability

Date: 2026-07-13

## Scope and decision

Milestone 10 closes only rollout and diagnostics gaps that Kalium can define without inventing the
native application's feature system, a telemetry SDK, product copy, signed entitlements, backend
inputs, or physical-device evidence. Production construction remains unavailable behind every
existing gate plus new native ownership gates.

The repository now owns:

- a pure version-1 rollout decision supplied by the native host;
- fail-closed short-circuiting before the notification runtime can touch the account lock, storage,
  authentication, network, CoreCrypto, or AVS;
- one bounded, fixed-shape, payload-excluding completion observation; and
- explicit masks for integration work that the native application and product owners must supply.

It does not fetch or cache a feature flag, choose a rollout percentage, derive a cohort, upload
diagnostics, choose notification text, create platform replacement identifiers, or coordinate an
app release.

## Rollout control v1

`NotificationExtensionRolloutControl` carries only an evaluated host snapshot:

- contract version and monotonically host-owned revision;
- issue and expiry time;
- feature state: enabled, disabled, or unavailable;
- kill-switch state: allow, stop, or unavailable;
- cohort result: included, excluded, or unavailable; and
- a coarse stop reason that is valid only when the kill switch is engaged.

Kalium validates that the revision is positive, but intentionally stores no rollout policy state
and cannot compare revisions across invocations. Fetch authentication, rollback protection, and
monotonic revision enforcement remain responsibilities of the native feature/kill-switch owner.

The maximum accepted snapshot lifetime is 24 hours. This is a conservative spike ceiling, not a
product-approved refresh cadence. The native owner can issue shorter snapshots. Missing,
unreadable, malformed, future-issued, expired, overlong, or unsupported snapshots fail closed.

The decision order is:

```text
evaluate locally readable kill switch
    STOP -> ROLLOUT_DISABLED / ROLLOUT_KILL_SWITCH
    otherwise validate contract version
    -> require kill-switch availability
    -> validate revision, issue/expiry ordering, maximum lifetime, and current time
    -> require feature state
    -> require included cohort
    -> ELIGIBLE
```

A stop decision is allowed to win over an unsupported remainder because it only reduces behavior.
No incompatible snapshot can enable the runtime. `ELIGIBLE` is permission to continue evaluating
the existing assembly and lifecycle gates; it is not evidence that production construction is
ready.

The unavailable companion value is the default request value. Old or incomplete hosts therefore
fail closed instead of silently enabling NSE synchronization.

## Short-circuit and staged-data safety

The public façade evaluates rollout before launching code that can call
`NotificationExtensionRuntime.execute`. That runtime is the sole entry to the bounded engine and
therefore the sole route to the process lock, lazy handoff open, transport/authentication,
CoreCrypto, and the synchronous AVS bridge.

Every denied result:

- returns the privacy-preserving fallback directive;
- reports an empty local sync summary;
- never calls the runtime;
- does not acquire the account process lock;
- does not open, migrate, clean, delete, or recreate the handoff database;
- does not read or change the shared cursor; and
- does not initialize authentication, WebSocket, CoreCrypto, or AVS resources.

Disabling or rolling back NSE sync therefore preserves already staged parents, children, cursor
provenance, recovery records, import states, and tombstones. The foreground importer may continue
to drain data according to the Milestone 8/9 native release protocol. A kill switch is not a
cleanup, cursor rewind, downgrade, or account-removal command.

Rollout eligibility cannot override the Milestone 9 account lifecycle. The native app must durably
stop legacy synchronization and activate the shared cursor under the account lock before it may
issue an eligible decision. Global recovery, tombstone, incompatible schema, and unresolved
production gates continue to fail closed independently.

## Privacy-constrained observation v1

The framework can emit at most one observation for one completion. The observation is built from
the already selected final result and contains only:

- schema version;
- stable status and reason enums;
- one monotonic elapsed-time bucket;
- one remaining-deadline bucket; and
- buckets for frame, staged, duplicate, local-writer ACK, materialized, and drain-batch counts.

It deliberately excludes:

- raw or decrypted event data and protobufs;
- message text, call payload, notification content, or exception messages;
- transport/authentication tokens and delivery tags;
- exact byte counts or per-stage timing;
- account, client, user, sender, conversation, marker, message, or notification identifiers;
- App Group, database, CoreCrypto, or lock paths;
- the stable account/client lock digest; and
- AVS callback values or native error text.

Exact byte and count values remain in the local `NotificationExtensionResult` for bounded host
control, but diagnostics copy only count buckets and no byte fields. This prevents exact payload
sizes from becoming an unintended content side channel.

The optional correlation value is caller supplied and accepted only as a lowercase canonical
UUID-v4 string. This bounds its representation but does not make it intrinsically privacy-safe or
prevent a malicious or mistaken caller from encoding data into it. The native owner must generate
it randomly, treat non-PII provenance as an external production prerequisite, and never derive it
from any account, device, user, conversation, notification, or message identifier. The
observation's `toString` reports only whether correlation is present and never renders its value.

The actual iOS completion callback is invoked before any observation is constructed or the optional
observer is called. Observation construction and observer exceptions are swallowed, and at-most-once
completion remains protected by the same atomic completion gate used for cancellation and runtime
races. Observation shape and cardinality are bounded to one fixed record. The observer contract is
explicitly non-blocking; diagnostics retention and export occur outside the NSE invocation and must
not be implemented by doing synchronous network work in the callback.

The synchronous observer interface cannot enforce a host callback wall-clock limit. Production
integration therefore remains gated on a native diagnostics owner that keeps the callback local and
non-blocking. Count and timing buckets are still operational metadata and remain subject to that
owner's privacy classification, retention, consent/access, and export review.

No logging or telemetry dependency was added, and diagnostics are not persisted in the handoff
database.

## Native ownership contracts

Production integration must explicitly assign and approve every
`NotificationExtensionHostResponsibility`:

| Responsibility | Native/product contract |
| --- | --- |
| Generic message fallback | Supply approved privacy-preserving text; never trust remote push content as message text. |
| Generic call fallback | Supply separately approved call fallback behavior when AVS/policy is unavailable. |
| Feature-flag source | Fetch, authenticate, cache, version, and expire the rollout snapshot outside Kalium. |
| Kill-switch source | Provide an independently readable stop path with an operational owner. |
| Cohort decision | Derive the cohort with an approved deterministic native rule; pass only the evaluated enum to Kalium. |
| Diagnostics retention | Define on-device lifetime, deletion, access, consent, and privacy classification. |
| Diagnostics export | Define upload batching, network policy, authentication, sampling, and sink behavior outside the NSE critical path. |
| Replacement identifier | Derive a stable opaque platform notification replacement ID from the approved native contract; never expose it as diagnostics correlation. |
| Cursor cutover | Record the native legacy-stop receipt and activate shared authority through the Milestone 9 protocol. |
| Downgrade/rollback | Coordinate app/NSE versions; kill new NSE sync first, preserve staged data, and never restore a second cursor authority implicitly. |
| Rollout stop conditions | Set thresholds, review owners, escalation, and re-enable criteria for the coarse stop classes. |

Declaring responsibilities does not make production available. It only makes missing ownership
queryable. `NotificationExtensionFactory` still returns no instance and reports all prior external
gates plus:

- native rollout-control ownership;
- approved generic fallback and replacement behavior;
- privacy-approved diagnostics retention/export;
- cursor cutover and downgrade release ownership; and
- rollout stop-condition approval.

## Rollout stop conditions

The public stop taxonomy is intentionally coarse:

- privacy or security incident;
- crash or memory-pressure regression;
- deadline-margin regression;
- storage integrity;
- cursor or foreground-import integrity;
- CoreCrypto state integrity;
- backend delivery/ACK/gap behavior;
- notification duplication or disclosure; and
- another explicitly approved stop.

Thresholds are not encoded in Kalium because simulator timings and synthetic counts are not
production SLOs. The native/product owner engages the kill switch after evaluating those thresholds.
Re-enable requires a fresh valid snapshot; old eligible state must not be reused after expiry.

## Disposable verification design

The Apple-only synthetic rollout probe uses a side-effect-counting runtime and a throwing observer.
It verifies that missing, unsupported, stale, stopped, disabled, and excluded controls all complete
with generic fallback and zero runtime calls. An eligible synthetic control enters the runtime once.
The probe cancels each completed handle repeatedly and verifies one completion and one observation.

The existing M7 framework probe first creates a real synthetic staged child under the Milestone 5
lock and Milestone 6 store. It then runs every denied rollout path and reopens the handoff store to
prove the exact staged-child evidence is unchanged. This is host/simulator evidence only; the
plaintext database is never a production constructor.

The probe also supplies every native responsibility bit and verifies that production remains
unavailable because repository-external gates are still blocked. No real feature service,
diagnostics upload, notification presentation, authentication, backend, crypto receive, or AVS
payload is claimed.

## Verification evidence

- Common metadata plus iOS Simulator ARM64 and macOS ARM64 source compilation passed for the core
  framework. The core and unchanged split AVS iOS Simulator frameworks linked. The existing AVS
  archive emitted its known non-fatal debug-map warnings.
- Swift type-checked in application-extension mode with warnings treated as errors against the
  regenerated framework headers.
- The disposable Swift host ran on the booted iPhone 16 Pro / iOS 18.4 simulator and reported:

```text
rolloutFailClosed=true
rolloutDeniedRuntimeCalls=0
rolloutEligibleRuntimeCalls=1
rolloutCompletionAtMostOnce=true
stagedDataPreservedOnDisable=true
observationPayloadExcluded=true
observerFailureNonFatal=true
productionAvailable=false
```

- Existing stage-before-ACK, account-lock coverage, store-close-before-unlock, exact-proto,
  privacy-preserving fallback, split-AVS bridge, and completion evidence remained green. The AVS
  payload stays intentionally malformed and synthetic; its native failure is not real call evidence.
- Dependency resolution is unchanged. The public header exposes only the intended rollout,
  readiness, observation bucket, observer, and UUID-correlation types. Internal rollout evaluation,
  UUID validation, and bucketing helpers are not exported. No exact byte count, payload/protobuf,
  token, account/user/conversation/message identifier, path, exception/native text, SQLDelight type,
  CoreCrypto context, bounded-engine port, or AVS domain type appears in the observation surface.
- Core/AVS symbol separation remained intact. Repository detekt, diff/whitespace,
  no-sensitive-field/source-logging, dependency/build-file, and no-test-file audits passed.
- Independent privacy/security/rollout review fixed completion-before-diagnostics ordering and
  narrowed correlation to canonical UUID-v4, then found no remaining scoped blocker.
- No automated tests were added, modified, generated, or run.

## Production gates still open

- Native feature/kill-switch fetch and cache, signed or otherwise authenticated policy provenance,
  cohort derivation, operational owner, refresh cadence, rollout percentages, and stop thresholds.
- Approved generic message/call fallback copy, platform notification replacement identifiers,
  diagnostics privacy review, retention, consent/access, and export implementation.
- Real native-app import mapping, legacy-stop receipt, cursor cutover, contract-1-to-2 migration,
  downgrade/recovery release coordination, and account-removal orchestration.
- Encrypted App Group handoff storage, independent shared Keychain keys, file protection,
  descriptor-relative construction/measurement, and secure erasure or key retirement.
- Signed app/NSE entitlements, locked/first-unlock behavior, physical-device `.appex`, APNs/backend
  account, consumable WebSocket, exact raw capture, and local-writer ACK evidence.
- Real Proteus/MLS/Welcome/buffered-output receiving, CoreCrypto-to-handoff crash ordering,
  notification-policy snapshot, and real notification-only AVS payloads.
- Physical-device cold start, RSS/jetsam, storage pressure, backlog, deadline margin, and binary
  measurements with iOS/product-approved budgets.

No repository result in this milestone is a production rollout approval or SLO.
