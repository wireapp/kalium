Removed the `@DebugKaliumApi` `ConversationDAO.getAllConversationDetailsWithEvents` from `:data:persistence`. Nothing in Kalium called it; the conversation list reads the same view through `ConversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch`.

  - ABI and source: breaking for direct `ConversationDAO` callers and implementations of the debug method.
  - Behavior: unchanged.
  - Migration: page the list with `getPagerForConversationDetailsWithEventsSearch`.
