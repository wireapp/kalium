# MLS Use Cases and Handlers

This file documents the main MLS-related Android flows and their error behavior.

## Overview Table

| Flow | Trigger | Success path | Main error behavior |
|---|---|---|---|
| `MLSWelcomeEventHandler` | `Conversation.MLSWelcome` event | process welcome, mark conversation established, resolve 1:1, refill key packages | recover from `ConversationAlreadyExists`; on `OrphanWelcome` either skip rejoin or rejoin depending on local state |
| `JoinExistingMLSConversationUseCase` | explicit rejoin / stale recovery / orphan welcome recovery | join by external commit or establish locally | stale join retries after refetch, reset on selected failures |
| `JoinExistingMLSConversationsUseCase` | bulk recovery for `PENDING_JOIN` conversations | iterate all pending MLS conversations and join/establish them | network/keypackage failures are mostly skipped, not fatal |
| `RecoverMLSConversationsUseCase` | post-sync MLS recovery for established groups | compare local MLS epoch with stored remote epoch and rejoin if stale | `ConversationNotFound` marks group as `PENDING_AFTER_RESET` |
| `StaleEpochVerifier` | MLS message decryption reports out-of-sync | compare local core-crypto epoch with fresh backend epoch, then rejoin if stale | does not rejoin if issue was only unprocessed local events |
| `ResetMLSConversationUseCase` | explicit recovery when conversation cannot be recovered otherwise | reset backend conversation, leave local group, refetch metadata, establish again | if local MLS group is missing, fallback uses DB epoch |
| `MLSResetConversationEventHandler` | `Conversation.MLSReset` event | leave old group, update new group id/state/epoch | if new group already exists locally, marks established; otherwise pending-after-reset |
| `NewMessageEventHandler` | incoming Proteus/MLS message event | decrypt, persist application message, run side effects | MLS errors are mapped to ignore / inform-user / out-of-sync / reset |

---

## `MLSWelcomeEventHandler`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSWelcomeEventHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSWelcomeEventHandler.kt)

Detailed duplicate/replay semantics for `MLSWelcome`, `NewMLSMessage`, and `MLSReset` are documented separately in:
- [Duplicate Handling](./duplicate-handling.md)

### Normal flow

1. Ensure MLS is enabled in the transaction context.
2. Call `fetchConversationIfUnknown(...)` so local metadata exists.
3. Call `processWelcomeMessage(...)` in core-crypto.
4. If welcome carries CRL distribution points, validate them.
5. Mark conversation as `ESTABLISHED` in local persistence.
6. If this is a 1:1, resolve the one-on-one protocol state.
7. Attempt to refill key packages.
8. Log structured success with timing data.

### Error handling

#### `MLSFailure.ConversationAlreadyExists`

Behavior:
- wipe local conversation in core-crypto,
- retry processing the same welcome once.

Why:
- this handles the case where local crypto state still contains a previous group for the same conversation.

#### `MLSFailure.OrphanWelcome`

Behavior is split into two sub-cases:

1. If local DB says the group is `ESTABLISHED` **and** core-crypto confirms the group exists:
   - skip rejoin,
   - do **not** rejoin by external commit.

2. Otherwise:
   - discard welcome,
   - try `JoinExistingMLSConversationUseCase`.

Why:
- the same core-crypto error can mean either:
  - the welcome was already processed before, or
  - the referenced key package was genuinely no longer available locally.

### Important limitation

After successful welcome processing Android currently marks the conversation as `ESTABLISHED`, but welcome flow itself does not directly write the local core-crypto epoch back into DB. Later recovery flows may therefore still need to resynchronize state.

---

## `JoinExistingMLSConversationUseCase`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/conversation/JoinExistingMLSConversationUseCase.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/data/conversation/JoinExistingMLSConversationUseCase.kt)

### Purpose

Join or establish MLS for a conversation where the user is already a member but local MLS state may be missing or stale.

### Decision tree

1. If protocol is not MLS-capable: no-op.
2. If core-crypto already has the local MLS group:
   - skip join/establish,
   - synchronize DB metadata with local MLS state (`groupState=ESTABLISHED`, `epoch=local core-crypto epoch`).
3. Else if DB `epoch != 0`:
   - fetch `groupInfo` from backend,
   - join by external commit.
4. Else:
   - establish a new local group using members/public keys.

### Why step 2 matters

This is one of the main Android protections against **state drift**:
- DB may say `epoch=0` or `PENDING_JOIN`,
- while core-crypto already has an established group.

The current implementation explicitly trusts core-crypto more than DB in this case.

### Error handling for external commit join

`MLSMessageFailureHandler` maps failures to:

- `Ignore`
  - log and stop.
- `ResetConversation`
  - call `ResetMLSConversationUseCase`.
- anything else
  - propagate failure.

### Retry behavior

If join/establish fails with `StaleMessage`:
- refetch conversation state,
- read updated conversation from repository,
- retry join/establish once with fresher metadata.

### Still using DB state

When local MLS group does **not** exist, the use case still uses DB `epoch != 0` to choose between `join` and `establish`.

This is an intentional compromise:
- if core-crypto has no local group, it cannot provide the missing epoch,
- replacing this branch safely would require a backend-first strategy instead of local DB fallback.

---

## `JoinExistingMLSConversationsUseCase`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/conversation/JoinExistingMLSConversationsUseCase.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/data/conversation/JoinExistingMLSConversationsUseCase.kt)

### Purpose

Bulk wrapper over `JoinExistingMLSConversationUseCase` for all conversations currently in `PENDING_JOIN`.

### Flow

1. Open one crypto transaction.
2. Load conversations with `groupState = PENDING_JOIN`.
3. Launch async join tasks per conversation.
4. Await all tasks; fail if one async result returns a hard failure.

### Error behavior

This bulk use case is intentionally tolerant:

- `NetworkFailure.ServerMiscommunication` with invalid request:
  - skipped.
- `CoreFailure.MissingKeyPackages`:
  - skipped.
- other non-network failures:
  - skipped.
- generic network failure:
  - propagated.

This means bulk join is not a strict “all or nothing” operation from a product point of view.

---

## `RecoverMLSConversationsUseCase`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/conversation/RecoverMLSConversationsUseCase.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/conversation/RecoverMLSConversationsUseCase.kt)

### Purpose

Post-sync recovery for conversations already marked `ESTABLISHED`.

### Flow

1. Load all conversations with group state `ESTABLISHED`.
2. For each MLS conversation:
   - compare local core-crypto epoch against stored conversation epoch using `isLocalGroupEpochStale(...)`.
3. If stale:
   - call `JoinExistingMLSConversationUseCase`.
4. If local crypto group is missing:
   - mark conversation `PENDING_AFTER_RESET`.

### Important caveat

This use case still uses the conversation epoch from **local persistence** as the “remote/current” epoch reference.

That means it is still sensitive to DB drift.

This was left unchanged on purpose for now because replacing it with a backend fetch per conversation changes recovery cost and behavior significantly.

---

## `MLSConversationsRecoveryManager`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/conversation/MLSConversationsRecoveryManager.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/conversation/MLSConversationsRecoveryManager.kt)

### Purpose

Coordinator that waits until incremental sync is `Live` and only then triggers `RecoverMLSConversationsUseCase` if the persistent flag `needsToRecoverMLSGroups` is set.

This avoids running MLS recovery too early while sync is still catching up.

---

## `StaleEpochVerifier`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/message/StaleEpochVerifier.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/feature/message/StaleEpochVerifier.kt)

### Trigger

Called from `NewMessageEventHandler` when MLS message decryption resolves to `OutOfSync`.

### Current behavior

For parent conversations:
1. Fetch fresh conversation metadata from backend.
2. Extract remote `groupId` + `epoch` from `ConversationResponse`.
3. Ask core-crypto whether local epoch is stale relative to that fresh backend epoch.
4. If stale:
   - rejoin existing MLS conversation,
   - insert “lost commit” system message.
5. If not stale:
   - assume local issue was likely unprocessed events rather than missing commits,
   - do nothing else.

For subconversations:
- fetch fresh subconversation details directly from backend,
- do the same stale comparison,
- if stale, join by external commit using backend group info.

### Why this matters

Earlier Android logic read epoch back from local DB after fetch. Current code instead uses the **fresh backend response directly**, which reduces the chance that stale persistence causes a wrong recovery decision.

---

## `ResetMLSConversationUseCase`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/data/conversation/ResetMLSConversationUseCase.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/data/conversation/ResetMLSConversationUseCase.kt)

### Purpose

Last-resort repair when Android decides the conversation must be reset.

### Flow

1. Check feature flags / user config / federation constraints.
2. In a crypto transaction:
   - get MLS-capable protocol info from conversation repository,
   - try to get current local epoch from core-crypto,
   - if local group is missing, fallback to DB epoch,
   - call backend reset endpoint,
   - leave local group,
   - refetch conversation,
   - re-establish MLS group with updated group id and current members.

### Why this is still risky

This is one of the last places where Android still falls back to DB epoch when core-crypto returns `ConversationNotFound`.

That fallback is still there because:
- by definition local crypto state is missing,
- so core-crypto cannot provide epoch,
- and this flow has not yet been rewritten to be fully backend-first.

For cross-platform discussions, this is one of the most important places to review.

---

## `MLSResetConversationEventHandler`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSResetConversationEventHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/MLSResetConversationEventHandler.kt)

### Purpose

React to an incoming `Conversation.MLSReset` event.

### Flow

1. Leave the old group locally.
2. Check whether the new group already exists in core-crypto.
3. If yes:
   - read epoch from core-crypto,
   - set new state to `ESTABLISHED`.
4. If no:
   - set epoch to `0`,
   - set new state to `PENDING_AFTER_RESET`.
5. Update DB with new group id, new epoch, and new state.

This is a good example of trusting core-crypto over DB where possible.

---

## `NewMessageEventHandler`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/NewMessageEventHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/NewMessageEventHandler.kt)

### MLS path

1. Unpack MLS message via `MLSMessageUnpacker`.
2. On success:
   - process each unpacked message in the batch,
   - persist application messages,
   - run legal hold handling / delivery confirmation / self deletion side effects.
3. On failure:
   - map error via `MLSMessageFailureHandler`.

### Failure outcomes

- `Ignore`
  - log only.
- `InformUser`
  - optionally insert failed decryption placeholder.
- `OutOfSync`
  - call `StaleEpochVerifier`.
- `ResetConversation`
  - call `ResetMLSConversationUseCase`.

---

## `MLSMessageFailureHandler`

Source:
- [`kalium/logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageFailureHandler.kt`](../../logic/src/commonMain/kotlin/com/wire/kalium/logic/sync/receiver/conversation/message/MLSMessageFailureHandler.kt)

### Resolution matrix summary

| Failure family | Resolution |
|---|---|
| `WrongEpoch`, `InvalidGroupId` | `OutOfSync` |
| rejected failures indicating broken group state (`GroupOutOfSync`, invalid leaf info) | `ResetConversation` |
| stale/missing reference/client mismatch style rejected failures | `InformUser` |
| duplicates / buffered futures / self commit / unmerged / stale proposal / old epoch / orphan welcome / conversation missing | `Ignore` |
| generic MLS / network / storage / unknown failures | `InformUser` |

This is the central mapping that decides whether Android tries to recover, resets, or only surfaces a failure placeholder.
