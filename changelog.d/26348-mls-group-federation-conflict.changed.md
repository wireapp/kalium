Changed MLS group creation to report federation conflicts and discard the local pending conversation when cleanup succeeds.

  - ABI: breaking for previously compiled JVM consumers constructing `ConversationCreationResult.BackendConflictFailure` or implementing `CreateRegularGroupUseCase`.
  - Source: custom `CreateRegularGroupUseCase` implementations must implement `discardPendingMLSGroupCreation`; `BackendConflictFailure` now exposes an optional `conversationId` when automatic cleanup fails. Obsolete `ConflictWithMissingUsers` federation error types are removed.
  - Behavior: conflicting backend domains survive Core Crypto transport; successful local cleanup returns no conversation ID, while failed cleanup returns the local conversation ID for explicit discard. This 4.22 backport does not add pending MLS creation recovery.
  - Migration: recompile JVM consumers, implement `discardPendingMLSGroupCreation`, and use a non-null returned `conversationId` to retry local cleanup before closing the creation flow.
