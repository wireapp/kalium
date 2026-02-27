---
name: mockative-to-mokkery-migration
description: Use when migrating Kotlin tests and interfaces from Mockative to Mokkery, including mixed-framework migration and @Mockable transition impact.
---

# Mockative to Mokkery Migration

Use this skill when converting existing Kotlin tests from Mockative to Mokkery.

## When to use

- The user asks to migrate tests from Mockative to Mokkery.
- A recent interface migration causes `NoSuchMockException` in Mockative tests.
- You need consistent conversion of stubbing and verification APIs.

## Workflow

1. Read `references/migration.md`.
2. Resolve target scope before editing:
   - If the user gives shorthand like `logic/cache`, resolve to actual test package paths under `logic/src/*Test/kotlin/com/wire/kalium/logic/...`.
   - Confirm whether each target folder still contains Mockative usage before changing files.
3. Identify interface-level migration impact first:
   - keep `@Mockable` if Mockative tests still exist
   - remove `@Mockable` only when dependent tests are migrated
4. Convert test files:
   - imports
   - mock creation
   - stubbing (`coEvery` -> `everySuspend`)
   - verification (`coVerify` -> `verifySuspend`)
5. Account for Mokkery strictness:
   - for collaborators with unstubbed `Unit` methods (common in DAO mocks), use `mock<...>(mode = MockMode.autoUnit)` or explicit `Unit` stubs.
6. Handle mixed-framework files incrementally when full migration is too broad.
7. Run focused tests first:
   - in this repo, common test classes are typically validated with `:logic:jvmTest`.
   - if task names are unclear, check `./gradlew :logic:tasks --all` first.
8. If requested, split delivery by folder:
   - one folder per branch/commit/PR to `develop`.
   - if a folder has no Mockative usage, report no-op explicitly (and create an explicit no-op branch/commit only when requested).

## Required output

- Keep behavior unchanged.
- Keep migration scope explicit.
- Call out any downstream tests that must migrate due to interface annotation changes.
- Explicitly report folders with no Mockative usage.
