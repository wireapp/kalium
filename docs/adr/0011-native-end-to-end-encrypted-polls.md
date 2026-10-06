# Native End-to-End Encrypted Polls

## Status

Accepted

## Context

Polls are currently possible only through services or bots that send composite
messages with buttons. Composite messages store a text body and a list of
buttons. `ButtonAction` tells the composite author which button a user picked,
and `ButtonActionConfirmation` from the author marks the self user's choice as
selected. The bot owns the result. Clients store only their own selection, so
they cannot show counts, percentages, or who voted for what.

Bot polls must keep working for existing service integrations.

## Decision

Add native poll content to Kalium as a separate message type. Do not extend
composite messages. Bot polls keep `COMPOSITE`, `ButtonContent`, `ButtonAction`,
and `ButtonActionConfirmation` unchanged.

### Protocol

Add two `GenericMessage` oneof entries:

- `Poll poll = 31`, a regular timeline message with the question, ordered
  options (stable id and text), `allow_multiple_answers`, and the standard
  `expects_read_confirmation` and `legal_hold_status` flags. The message id is
  the poll id.
- `PollVote pollVote = 32`, a signaling message with the poll message id and
  the sender's complete current selection. An empty selection retracts the vote.

Both are sent through the normal end-to-end encrypted pipeline. No bot or
server takes part in voting or tallying.

Votes are never anonymous. Every client receives the sender of each encrypted
vote and needs it to keep one current vote per user. A setting that only hides
names in the UI would suggest an anonymity guarantee that the protocol does not
give, so there is no such setting.

A `Poll` is sent with `unknownStrategy = DISCARD_AND_WARN`. Clients without poll
support show an unsupported-message notice instead of silently dropping it.
`PollVote` keeps the default `IGNORE`. The ephemeral message format cannot carry
polls, so polls are never self-deleting. Clients should not offer poll creation
while self-deleting messages are active.

### Storage and rules

- `PollContent` stores the question, `allow_multiple_answers`, and the options
  as JSON encoded in Kotlin. Options never change after creation. Kotlin
  encoding means option text is never escaped by SQL string concatenation.
- `PollVoteContent` stores the latest vote of each user, keyed by conversation,
  poll, and voter. A vote replaces the stored vote only if it is not older, so
  out-of-order delivery cannot bring back an old selection. A retraction is
  stored as an empty selection for the same reason.
- A vote is valid when the poll exists locally, every option id belongs to the
  poll, and a single-answer poll has at most one option. Repeated ids are
  removed. `PollMessageDAO.recordVote` checks this in the same transaction that
  stores the vote. Invalid votes and votes for unknown polls are dropped and never
  buffered, so a client's result only depends on the history it has.
- Received polls drop options with a repeated id.
- Deleting a poll for everyone removes its poll content and votes.
- `POLL` counts as a last-message type. Conversation lists, previews, and
  notifications show the poll question.
- The legacy database backup exports and imports both poll tables.
- The message timeline loads poll content, votes, and voters with each page as
  `MessageListContent.Poll`, and a new vote invalidates the open timeline.
- The notification service extension stores received polls and votes with the
  same validation as the app, so polls and votes that arrive in the background
  are not lost.

### Sending

`SendPollMessageUseCase` validates 2 to 10 distinct, non-blank options and a
non-blank question. It persists the poll and sends it like a location message,
including the pending-message retry.

`SendPollVoteUseCase` stores the self user's vote before sending it, so results
update at once. If sending fails, it restores the previous vote, unless a newer
vote replaced it in the meantime, and returns a failure so the UI can tell the
user.

## Consequences

Poll creation and voting stay end-to-end encrypted and work without bots. Every
client derives the same result from the votes it has received. A client that
missed the original poll ignores later votes for it.

Older clients show an unsupported-message notice for native polls. Bot polls
behave as before.

The UI decides how to present the results. It must not suggest that votes are
anonymous.
