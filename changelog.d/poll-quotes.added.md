Added replies to native polls: `MessageContent.QuotedMessageDetails.Poll` carries the quoted poll's question, and the quote hash of a poll is the text hash of its question.

  - ABI: additive
  - Source: additive
  - Behavior: user database migration 159 recreates `MessageDetailsView`, which now returns a quoted poll's question as the quoted text. Replies to polls are sent with a quote hash and verified on receipt.
  - Migration: consumers that exhaustively match `MessageContent.QuotedMessageDetails.Content` need a poll branch.
