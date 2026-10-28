Message reactions now keep the order each emoji first reached the message, as the desktop client shows them, instead of coming back grouped by emoji. `Message.Reactions.reactions` and the timeline's `MessageListItem.reactions` list emoji in that order. When a sender's reactions change, the emoji they keep stay where they were; an emoji whose last reaction is removed and later re-added moves to the end.

  - ABI: unchanged.
  - Source: unchanged.
  - Migration: user database migration 163 recreates `MessageDetailsView`. Hosts that sort reactions themselves keep their own order; drop the sort to show Kalium's.
