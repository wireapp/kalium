# 2. Module Boundary Restructuring

Date: 2025-11-27

## Status

Accepted

## Context

The Kalium codebase had grown organically with modules at the root level (e.g., `:common`, `:data`, `:persistence`, `:network`, `:backup`, `:calling`, `:cells`). This flat structure made it difficult to understand module relationships, enforce architectural boundaries, and maintain clear separation of concerns. As the codebase scaled, we needed a more organized module hierarchy that would:

- Clearly define architectural layers and their responsibilities
- Make module dependencies more explicit and easier to reason about
- Improve discoverability by grouping related modules together
- Enforce better separation between core infrastructure, data access, domain logic, and testing utilities

## Decision

We reorganized modules into a hierarchical structure with clear architectural boundaries:

**Core Layer** (`:core:*`) - Foundation modules:
- `:core:common` (was `:common`)
- `:core:data` (was `:data`)
- `:core:cryptography` (was `:cryptography`)
- `:core:logger` (was `:logger`)
- `:core:util` (was `:util`)

**Data Layer** (`:data:*`) - Data access and infrastructure:
- `:data:network` (was `:network`)
- `:data:network-model` (was `:network-model`)
- `:data:network-util` (was `:network-util`)
- `:data:persistence` (was `:persistence`)
- `:data:persistence-test` (was `:persistence-test`)
- `:data:protobuf` (was `:protobuf`)
- `:data:data-mappers` (new module for transformations)

**Domain Layer** (`:domain:*`) - Business logic boundaries:
- `:domain:backup` (was `:backup`)
- `:domain:calling` (was `:calling`)
- `:domain:cells` (was `:cells`)
- `:domain:conversation-history` (new)
- `:domain:messaging:sending` (new, extracted from logic)
- `:domain:messaging:receiving` (new, extracted from logic)

**Test Layer** (`:test:*`) - Testing utilities:
- `:test:mocks` (was `:mocks`)
- `:test:data-mocks` (new)
- `:test:benchmarks` (was `:benchmarks`)
- `:test:tango-tests` (was `:tango-tests`)

**Sample/Tools Layer** (`:sample:*`, `:tools:*`):
- `:sample:cli` (was `:cli`)
- `:sample:samples` (was `:samples`)
- `:tools:testservice` (was `:testservice`)
- `:tools:monkeys` (was `:monkeys`)
- `:tools:backup-verification` (new)
- `:tools:protobuf-codegen` (was `:protobuf-codegen`)

All module references were updated throughout the codebase including build files, CI workflows, documentation, and the dependency graph visualization.

## Consequences

**Benefits:**
- **Clearer Architecture**: The layer-based structure makes the architecture immediately visible from the project structure
- **Better Discoverability**: Developers can quickly locate modules by their architectural purpose
- **Enforced Boundaries**: The naming scheme makes it obvious when a module is reaching across layers inappropriately
- **Improved Documentation**: Module paths now self-document their architectural role (e.g., `:data:network` vs just `:network`)
- **Scalability**: New modules can be added to appropriate layers without cluttering the root
- **Easier Onboarding**: New team members can understand the system organization more quickly

**Trade-offs:**
- **Migration Effort**: All module references needed updating across build files, CI workflows, and documentation
- **Longer Module Paths**: Module references are now more verbose (e.g., `projects.core.common` instead of `projects.common`)
- **Breaking Change**: External consumers referencing modules directly will need to update their references

**Technical Changes:**
- Updated all `implementation(projects.*)` references in build files
- Updated CI workflow gradle task paths (e.g., `:cli:assemble` → `:sample:cli:assemble`)
- Updated module graph configuration to show full paths and nest by module type
- Updated detekt baseline and project structure documentation
- Added README files to layer directories explaining their purpose and guidelines
