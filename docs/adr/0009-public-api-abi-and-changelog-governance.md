# 9. Public API, ABI, and Changelog Governance

Date: 2026-06-15

## Status

Proposed

## Context

Kalium is consumed as an SDK by external Android, JVM, iOS, and multiplatform clients. Those
consumers need two guarantees when upgrading:

1. Existing compiled applications should keep running when a release claims binary compatibility.
2. Source-level API changes should be documented clearly enough that consumers know what changed,
   what they need to migrate, and whether the change is safe to adopt immediately.

ADR 6 already introduced `explicitApi()` for `:logic` so public declarations are intentional.
However, explicit API mode does not detect whether a public declaration changed in a binary
incompatible way after it was published. A change can be source-compatible but still break already
compiled Kotlin/JVM consumers, for example by changing a return type, changing constructor
parameters on a public data class, or adding default parameters to a public function.

Kalium also has a tag-triggered `generate-changelog.yml` workflow that generates release notes from
commits. That is useful for release automation, but it does not force pull requests that change the
consumer-facing API to include migration guidance. Release notes generated only from commit messages
are not enough for SDK consumers.

Kotlin Gradle Plugin 2.2.0 and newer includes built-in ABI validation through
`kotlin { abiValidation() }`, with `checkKotlinAbi` and `updateKotlinAbi` Gradle tasks. Kalium is on
Kotlin 2.3.0, so this can be adopted without adding the older
`org.jetbrains.kotlinx.binary-compatibility-validator` plugin.

## Decision

Adopt a public API and ABI governance workflow for published Kalium artifacts.

### Compatibility Policy

Kalium will distinguish between:

- **Binary compatibility**: already compiled consumers can replace the previous Kalium artifact with
  the new one without recompiling.
- **Source compatibility**: consumers can recompile their source code against the new Kalium version
  without changing their code.
- **Behavioral compatibility**: existing API keeps the same documented semantics, except for bug
  fixes or explicitly documented behavior changes.

Stable public APIs should preserve binary and source compatibility within the supported release
line. Breaking changes should go through a deprecation cycle where possible, and should be reserved
for major releases or release lines that explicitly allow breaking changes.

APIs annotated with Kalium opt-in markers such as `@InternalKaliumApi`, `@DebugKaliumApi`, and
`@DelicateKaliumApi` have the stability guarantees described by those annotations and their KDoc.
These annotations must be used deliberately, not as a way to avoid documenting consumer-impacting
changes.

### ABI Validation

Use Kotlin Gradle Plugin's built-in ABI validation instead of adding the standalone binary
compatibility validator plugin.

The initial rollout should cover the published artifact surface, starting with `:logic` because it
is the main SDK entry point and already uses `explicitApi()`. Additional published modules should be
added as their public surface is classified.

Expected Gradle shape:

```kotlin
kotlin {
    abiValidation {
        filters {
            excluded {
                annotatedWith.add("com.wire.kalium.util.InternalKaliumApi")
                annotatedWith.add("com.wire.kalium.util.DebugKaliumApi")
            }
        }
    }
}
```

If the Kotlin Gradle Plugin version still requires the experimental opt-in for this DSL, the module
build file should opt in to `org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation`.

CI must run:

```bash
./gradlew checkKotlinAbi
```

Developers must run this locally before finishing public API changes. If the ABI change is
intentional and accepted, update the checked-in ABI dumps with:

```bash
./gradlew updateKotlinAbi
```

ABI dump updates are reviewable API changes. Reviewers should treat them like source changes, not
generated noise.

### Changelog Enforcement

Any pull request that changes public API or ABI must include consumer-facing release notes.

CI should enforce this by requiring one of the following when public ABI dumps change, or when the
pull request is labelled as an API-impacting change:

- A changelog fragment was added or updated.
- The pull request has an explicit `no-changelog-needed` label with a reviewer-accepted reason.
- The pull request has an explicit `internal-only` label and the ABI/API gate confirms no published
  consumer surface changed.

Direct edits to a single `CHANGELOG.md` on every pull request should be avoided because they cause
frequent merge conflicts. Prefer a fragment-based workflow:

```text
changelog.d/
  1234-added-backup-status-api.md
  1235-deprecated-old-login-flow.md
```

Each fragment should be short and consumer-focused:

```markdown
### Added
- Added `BackupStatus.InProgress` for observing backup progress.

### Migration
No action required unless consumers exhaustively match `BackupStatus`.

### Compatibility
ABI: additive.
Source: additive.
Behavior: no behavior change.
```

The release workflow should collect accepted fragments into release notes or `CHANGELOG.md`, then
clear or archive consumed fragments. The existing tag-triggered generated changelog may still be
used, but consumer-facing API notes must not depend only on generated commit summaries.

### Pull Request Expectations

Pull requests that affect public APIs should include:

- The ABI dump diff, when applicable.
- A changelog fragment or accepted skip label.
- Migration guidance for deprecations, replacements, removals, and behavior changes.
- Tests for new or changed behavior.
- A note when an API is intentionally experimental, delicate, debug-only, or internal.

Reviewers should block public API changes that:

- Break ABI unintentionally.
- Add public APIs without explicit stability classification.
- Remove or rename stable public APIs without deprecation or release-line approval.
- Add consumer-impacting behavior changes without release notes.
- Update ABI dumps without explaining why the change is acceptable.

## Consequences

**Easier:**

- CI catches accidental public ABI changes before they reach a release.
- Reviewers can inspect API evolution through explicit ABI dump diffs.
- Consumers get release notes that explain how to migrate, not only what commits landed.
- The policy builds on existing Kalium practices: `explicitApi()`, opt-in API annotations, and
  generated release automation.
- No new Gradle dependency is needed because ABI validation is provided by Kotlin Gradle Plugin.

**More difficult:**

- Public API changes require one more artifact to review: the ABI dump.
- Intentional API changes require changelog fragments, even when the code change is small.
- The team must maintain a clear list of published modules covered by ABI validation.
- Some KMP/host-target combinations may need careful ABI validation configuration so unsupported
  local targets do not create false confidence or false failures.

**Future work:**

- Add the ABI validation Gradle configuration for `:logic`.
- Decide which additional published modules are part of the external API contract.
- Add a CI workflow that runs `checkKotlinAbi`.
- Add a PR changelog check that understands `changelog.d/`, `no-changelog-needed`, and
  `internal-only`.
- Update the release workflow to merge changelog fragments into the final GitHub release notes.

**References:**

- [Kotlin backward compatibility guidelines](https://kotlinlang.org/docs/api-guidelines-backward-compatibility.html)
- [Kotlin Gradle Plugin ABI validation](https://kotlinlang.org/docs/gradle-binary-compatibility-validation.html)
