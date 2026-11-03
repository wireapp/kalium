Added `ConversationScope.observeNotesConversation` (`ObserveNotesConversationUseCase`), which emits the MLS self conversation as the "Notes to self" conversation while `KaliumConfigs.notesToSelfEnabled`, the synced "show notes" setting and MLS are enabled and its MLS group is established.

  - ABI: additive
  - Source: additive
  - Behavior: `ConversationDetails.Self.interactionAvailability()` now returns `ENABLED` for the MLS self conversation (the Proteus self conversation stays `DISABLED`), so consumers can send messages there.
  - Migration: none.
