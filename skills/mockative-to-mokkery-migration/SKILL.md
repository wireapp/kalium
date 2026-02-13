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
2. Identify interface-level migration impact first:
   - keep `@Mockable` if Mockative tests still exist
   - remove `@Mockable` only when dependent tests are migrated
3. Convert test files:
   - imports
   - mock creation
   - stubbing (`coEvery` -> `everySuspend`)
   - verification (`coVerify` -> `verifySuspend`)
4. Handle mixed-framework files incrementally when full migration is too broad.
5. Run focused tests first, then run detekt.

## Required output

- Keep behavior unchanged.
- Keep migration scope explicit.
- Call out any downstream tests that must migrate due to interface annotation changes.
