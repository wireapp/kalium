# 10. Generate the Release SBOM from Resolved Dependency Graphs

Date: 2026-07-11

## Status

Accepted

## Context

Kalium's customer SBOM pipeline currently copies third-party artifacts once for every consuming
module and target, deduplicates those copies, recursively extracts JAR, AAR, KLIB, and npm trees,
and performs a file-level ScanCode scan. Even after pruning compiled and media files, a release run
takes approximately 20 minutes and maintains a large shell and Python toolchain.

The customer notice and license-policy gate do not depend on ScanCode's complete file inventory.
They use resolved Maven POM metadata, npm package metadata, bundled LICENSE/NOTICE/COPYING files,
and Kalium's reviewed license overrides. The full file scan therefore performs substantially more
work than the normal release deliverable requires.

Kalium is a multiplatform SDK. Its inventory must include the production configurations for JVM,
Android, Apple, and JavaScript targets while excluding samples, test fixtures, and build tools.
Generic source-tree discovery is not authoritative enough for this because Gradle variant
selection determines the artifacts actually shipped to each target.

## Decision

Use Gradle's resolved production dependency graphs as the authoritative source for the normal
release SBOM.

- Generate CycloneDX 1.6 JSON with the official CycloneDX Java model and serializer from a
  Kalium-owned Gradle task. The official Gradle plugin cannot currently be used because its task
  registration conflicts with Maven Publish on Kalium's Gradle version.
- Explicitly include Kalium's JVM, Android, Apple, and JavaScript runtime configurations.
- Exclude sample, test, and tool projects from the aggregate.
- Collect each resolved external artifact once for license evidence.
- Read only package metadata and LICENSE, LICENCE, NOTICE, COPYING, and UNLICENSE files from
  dependency archives. Do not recursively materialise compiled archive contents.
- Preserve the reviewed license override file and the existing accepted-license compliance gate.
- Validate the CycloneDX output and compare its component inventory with the legacy output during
  migration.
- Retain the existing ScanCode pipeline as an explicit deep-audit command for forensic file-level
  evidence. It is no longer the default release path after equivalence is established.

CycloneDX is the canonical machine-readable format. SPDX output, when required by a customer, is
derived from the validated CycloneDX document with a pinned converter instead of rescanning files.

## Consequences

**Easier:**

- Normal SBOM generation scales with the resolved dependency graph instead of the number of files
  inside every dependency.
- Gradle can cache and incrementally execute the SBOM tasks.
- Component inclusion follows the same variant-aware resolution used by Kalium builds.
- License policy and manual review data remain unchanged.
- The fast and deep-audit paths have distinct, documented purposes.

**More difficult:**

- File-level copyright, email, URL, and arbitrary source-header discoveries are available only from
  the deep-audit path.
- npm packages and opaque prebuilt native libraries require explicit collection alongside Gradle's
  Maven component graph.
- The migration must compare component and license coverage before the legacy path can be removed
  from release automation.
