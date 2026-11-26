Message timelines no longer invalidate when messages, reactions, receipts, or users change in other conversations, so sync writes elsewhere can't keep cancelling a conversation's first page.

  - ABI: unchanged
  - Source: compatible
  - Behavior: user changes other than name, picture, accent, deletion, or defederation reach an open timeline on its next refresh
