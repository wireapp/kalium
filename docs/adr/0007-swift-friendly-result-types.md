# 7. Swift-Friendly Result Types for Public APIs

Date: 2026-01-16

## Status

Accepted

## Context

Kalium's public APIs extensively used `Either<CoreFailure, Unit>` for error handling. This created
poor Swift interop:

- Generic `Either` types translate poorly to Swift/OjbC, resulting in complex, unidiomatic APIs
- Swift developers couldn't easily pattern match or understand error cases

## Decision

Replace `Either<CoreFailure, Unit>` with **non-generic, action-specific sealed classes** for public
APIs:

```kotlin
public sealed class MessageOperationResult {
    public data object Success : MessageOperationResult()
    public data class Failure(val error: CoreFailure) : MessageOperationResult()

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public fun toEither(): Either<CoreFailure, Unit> = ...
}
```

**Key principles:**

- No generics (concrete types per domain)
- Action-specific naming (`MessageOperationResult`, `FetchConversationResult`)
- Hidden `toEither()` bridge function for internal Kotlin code, for backward compatibility with
  Android and internals. Specifically for MessageOperationResult, covering most of our Message
  sending APIs.
- Use `@HiddenFromObjC` to avoid exposing the bridge function to Objective-C/Swift consumers.

## Consequences

**Easier:**

- Idiomatic Swift API with clean enum pattern matching
- Better type safety and discoverability for Swift consumers
- Gradual migration without breaking internal code

**More difficult:**

- More boilerplate (one result type per domain vs single generic)
- Need `.toEither()` bridge calls in internal code, use as needed.
- More types to maintain

**Future pattern:**

- Create specific result types (avoid generics)
- Use action-specific names
- Case by case, include hidden `toEither()` for backward compatibility
- Annotate with `@HiddenFromObjC` to avoid exposing to Swift/ObjC
