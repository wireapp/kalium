### Removed
- Removed call-state fields from public conversation details models: `ConversationDetails.Group.hasOngoingCall` and `ConversationDetailsWithEvents.hasOngoingCall`.

### Migration
Consumers that show join-call UI should derive that state from call APIs, such as `ObserveJoinableCallsUseCase`, and combine the resulting conversation IDs with list items at the UI/app layer.

### Compatibility
ABI: breaking.
Source: breaking for consumers that referenced `ConversationDetails.Group.hasOngoingCall` or `ConversationDetailsWithEvents.hasOngoingCall`.
Behavior: conversation list ordering can still use in-memory joinable call IDs, but call join markers are no longer exposed on Kalium conversation data class.
