Removed call-state fields from public conversation details models: `ConversationDetails.Group.hasOngoingCall` and `ConversationDetailsWithEvents.hasOngoingCall`.

  - ABI: breaking
  - Source: breaking for consumers that referenced `ConversationDetails.Group.hasOngoingCall` or `ConversationDetailsWithEvents.hasOngoingCall`. `ObserveJoinableCallsUseCase` now emits joinable calls keyed by conversation ID.
  - Behavior: conversation list ordering can still use in-memory joinable call IDs, but call join markers are no longer exposed on Kalium conversation data class.
