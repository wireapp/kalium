Added `ConversationCreationResult.PendingMLSGroupCreation` and `CreateRegularGroupUseCase.retryPendingMLSGroupCreation` so clients can recover a failed MLS group establishment using the existing conversation.

  - ABI: additive
  - Source: potentially breaking for consumers that exhaustively match `ConversationCreationResult`.
  - Behavior: an MLS establishment failure after backend conversation creation now returns the pending conversation and schedules recovery instead of returning a generic creation failure.
  - Migration: handle `PendingMLSGroupCreation` and optionally call `retryPendingMLSGroupCreation` to retry immediately.
