# Explicit API Mode Migration for Logic Module

Date: 2025-12-23

## Status

Accepted

## Context

The Kalium logic module serves as the main SDK entry point that client applications interact with. Without explicit visibility modifiers, it was difficult to distinguish between public API surface (intended for consumers) and internal implementation details. This led to several issues:

1. **Accidental API Exposure**: Internal implementation classes and functions were inadvertently exposed to consumers, creating unintended API contracts
2. **API Maintenance Burden**: Changes to internal implementations could break consumers who were using APIs that were never intended to be public
3. **Unclear API Boundaries**: Developers couldn't easily distinguish between stable public APIs and internal utilities
4. **cryptoTransactionProvider Leakage**: Critical internal components like `CryptoTransactionProvider` were being directly accessed by sample applications, bypassing proper API abstractions

Kotlin's `explicitApi()` mode enforces that all public declarations have explicit visibility modifiers and return types, forcing intentional decisions about API surface.

## Decision

We enabled `explicitApi()` mode for the `:logic` module and adopted an **internal-first migration strategy**:

1. **Mark Everything as Internal**: Used automated scripts to add `internal` visibility modifiers to all declarations lacking explicit visibility (~3,449 declarations)
2. **Fix Interface Members**: Removed incorrectly added `internal` modifiers from interface members (1,278 instances), as they inherit visibility from the interface
3. **Selective Public Exposure**: Only made APIs `public` when consumer modules failed to compile, ensuring minimal API surface
4. **Create Public Wrappers**: For internal components that needed controlled access (like `cryptoTransactionProvider`), created public wrapper methods in appropriate scopes (e.g., `DebugScope.refillKeyPackages()`, `DebugScope.generateEvents()`)

### Implementation Details

- Created Python scripts for automated migration to handle:
  - Adding `internal` modifiers while skipping `override` functions
  - Removing invalid `internal` from interface members
  - Preserving proper indentation and code structure

- Used `@InternalKaliumApi` opt-in annotation for:
  - Test code accessing internal APIs within the same module
  - Debug utilities that need controlled internal access
  - Explicitly marking intentionally exposed internal APIs

## Consequences

- **Minimal Public API**: Only APIs that are actually used by consumers are public
- **Clear API Boundaries**: Developers can immediately distinguish public SDK APIs from internal implementation details
- **Prevented Leakage**: Critical internal components like `CryptoTransactionProvider` are now properly encapsulated behind public wrappers
- **Better Encapsulation**: Internal implementations can be refactored without breaking consumers
- **Improved Documentation**: The public API surface is now clearly defined and easier to document
- **Compiler-Enforced Contracts**: Future additions must explicitly declare visibility, preventing accidental API exposure

Costs:
- **Verbose Code**: All declarations now require explicit visibility modifiers

### Migration Notes

For consumers experiencing compilation errors:
1. Check if the API should be public (common use case) - submit an issue/PR to expose it
2. For sample/test code, use `@OptIn(InternalKaliumApi::class)` if the internal API is intentionally marked with `@InternalKaliumApi`
