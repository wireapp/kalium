# Native End-to-End Encrypted Polls

## Status

Proposed

## Context

Polls are currently possible only through services or bots that send composite
messages with buttons. That makes creating a poll cumbersome, and it moves the
poll workflow out of the normal end-to-end encrypted client messaging model.

Composite button messages already exist in Kalium, but their current semantics
are closer to bot callbacks than native polls:

- `Composite` messages store a text body and a list of buttons.
- `ButtonAction` is a signaling message and is not persisted as conversation
  content.
- `ButtonActionHandler` ignores actions from other users and only marks a local
  button selected when the action was sent by the self user.
- `ButtonActionConfirmation` is expected to come from the original composite
  message author and updates only the selected button state.

This means clients do not derive a shared poll result from encrypted messages in
the conversation. They also do not retain the per-voter state required to show
counts, percentages, and voter lists.

## Decision

Introduce native poll protocol content in Kalium instead of modeling polls as
bot-owned composite buttons.

Add two encrypted client message contents:

- `Poll`, a regular timeline message containing:
  - stable poll id, using the message id as the canonical poll id
  - question text
  - ordered options, each with a stable option id and label
  - settings, initially `allowMultipleAnswers` and `hideVoterNames`
- `PollVote`, a signaling message containing:
  - referenced poll message id
  - selected option ids

`Poll` and `PollVote` should be encoded in `messages.proto` as new
`GenericMessage` oneof entries. They are sent through the same encrypted message
pipeline as text, reactions, and other client messages. No server-side bot or
third-party service participates in vote creation or tallying.

Persist poll votes as derived local state keyed by conversation id, poll message
id, and voter user id. A user's latest valid vote replaces their previous vote
for that poll. For single-answer polls, a valid vote contains at most one option
id. For multiple-answer polls, a valid vote may contain multiple distinct option
ids. Invalid option ids are rejected.

When a client receives a `PollVote` for a poll message it does not have, it must
ignore the vote and not buffer it for later replay. This keeps slow sync and
partial history behavior deterministic and follows the current requirement that
votes for unknown polls are discarded.

Clients keep enough local state to answer both result questions:

- aggregate counts per option
- the current selected option ids per voter

`hideVoterNames` is a UI/display setting, not a cryptographic anonymity
guarantee. Clients still receive the sender identity of every encrypted vote so
they can enforce one current vote per user and derive consistent counts. A truly
anonymous poll would require a separate cryptographic tallying design and is out
of scope for this decision.

Android should expose poll creation from the message composer, opening a create
poll screen or sheet with:

- question input
- at least two options
- dynamic option add/remove
- `allowMultipleAnswers`
- `hideVoterNames`

Android should render poll timeline items with:

- question
- option rows
- current user's selected state
- counts and percentages
- a voter details entry point when voter names are not hidden

## Consequences

Poll creation and voting remain end-to-end encrypted and work without bots.

Every client can derive the same poll result from conversation messages, subject
to the same local history it has received. A client that missed the original
poll will ignore later votes for that poll, as required.

Kalium needs changes across protocol mapping, domain models, persistence,
message receiving handlers, send use cases, backup/restore if applicable, and
tests. Android needs composer entry points, poll creation state, timeline
rendering, vote sending, and vote detail UI.

Existing composite button behavior can remain for bot/service integrations. It
should not be extended to represent native polls because its confirmation model
is author-centric and does not store per-voter state.

Older clients that do not understand the new poll message type will not render
native polls. The protobuf unknown-message strategy should be chosen so older
clients warn or safely discard rather than showing misleading composite-button
UI.
