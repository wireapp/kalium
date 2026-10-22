Added replies to native polls: `MessageContent.QuotedMessageDetails.Poll` carries the quoted poll's question, and the quote hash of a poll covers its question, options and answer mode (ADR 0016).

  - ABI: additive
  - Source: additive
  - Behavior: user database migration 159 recreates `MessageDetailsView`, which now returns a quoted poll's question as the quoted text and reads quoted content only from the quoted message's conversation. Replies to polls are sent with a quote hash and verified on receipt.
  - Migration: consumers that exhaustively match `MessageContent.QuotedMessageDetails.Content` need a poll branch.
