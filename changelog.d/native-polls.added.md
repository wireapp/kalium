Added native end-to-end encrypted polls: `MessageContent.Poll`, `MessageContent.PollVote`,
`MessagePreviewContent.WithUser.Poll`, `MessageListContent.Poll`, `MessageScope.sendPollMessage`, and `MessageScope.sendPollVote`.

  - ABI: additive
  - Source: additive
  - Behavior: polls are stored as `POLL` messages and count as the last message of a conversation. Votes update the poll results.
  - Migration: consumers that exhaustively match `MessageContent`, `MessagePreviewContent.WithUser`, `MessageListContent`, or message content types need a poll branch.
