The position returned for a message in a conversation (`GetSearchedConversationMessagePositionUseCase`) now counts newer self-deleting messages. The paged message list shows them, so a position that skipped them pointed at a newer message than the one asked for.

  - ABI: no change
  - Source: no change
  - Migration: none
