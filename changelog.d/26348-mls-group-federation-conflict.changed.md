Changed MLS group creation to treat federation conflicts as terminal failures and discard the local pending conversation when cleanup succeeds.

  - ABI: breaking for previously compiled JVM consumers constructing `ConversationCreationResult.BackendConflictFailure` or implementing `CreateRegularGroupUseCase`.
  - Source: potentially breaking for custom `CreateRegularGroupUseCase` implementations, which must implement `discardPendingMLSGroupCreation`, and exhaustive failure matching after removal of the obsolete `ConflictWithMissingUsers` response and `ConflictingBackendsWithMissingUsers` failure. `BackendConflictFailure` now exposes an optional `conversationId` when automatic cleanup fails.
  - Behavior: federation conflicts during MLS group establishment are no longer queued indefinitely for recovery; successful cleanup returns no conversation ID, while failed cleanup returns the local pending conversation ID for explicit discard.
  - Migration: recompile JVM consumers, implement `discardPendingMLSGroupCreation`, replace obsolete missing-user conflict handling with `ConflictingBackends` domain handling, and use the returned `conversationId` to retry cleanup when it is non-null.
