# 15. Keyset-Paginated Conversation List

Date: 2026-09-04

## Status

Accepted

## Context

The existing conversation-list flow is a public compatibility surface built on a wide joined view, a count query, and offset pagination. Deep loads still scan or sort preceding rows, unread aggregation is not restricted to the requested page, and the returned conversation details contain substantially more data than the list renders.

## Decision

Add a separate common Kalium conversation-list flow and leave the legacy flow, models, configuration, and behavior unchanged. The new public API will return a dedicated sealed `ConversationListItem` hierarchy for the four currently paginated sources: all conversations, groups, channels, and one-to-one conversations; archive mode and contains-search remain supported, while favorites and custom folders remain on their existing flows.

The [conversation list guide](../guides/conversation-list.md) describes the public contract.

Order eligible conversations only by stored `Conversation.last_modified_date` descending and qualified conversation ID ascending. Do not order by name, unread state, pending-connection update time, or joinable calls, and do not change the existing writers of `last_modified_date`. A public `ConversationListPosition` will contain the old activity time and conversation-ID tie-breaker so the app can restore the same approximate ordering neighborhood without following a conversation that later moved.

Use a cursor-keyed Paging source with no count, placeholders, or arbitrary jumps. Normal loading appends older items; prepend and centered refresh are also supported so an invalidated or restored deep viewport can reach both newer and older items. Android will continue to own pixel-offset and query-identity persistence.

For each load direction, use an `AS MATERIALIZED` CTE to select the eligible page keys and apply the keyset boundary, ordering, and limit there. Hydrate only those keys with the narrow row projection, saved-draft or last-message preview, and five page-local unread counters. This requires SQLite 3.35 or newer; bundling a modern SQLite binary for the unencrypted Android path and supporting older SQLite runtimes are outside this decision.

Reuse the existing conversation-list membership, order, last-message, and unread invalidation keys, together with the `MessageDraft` table notification key. Draft writes invalidate loaded pages so previews update without rebuilding the pager on return. Folder changes and call changes do not invalidate the new Paging source. Joinable-call state remains an Android-observed overlay rather than part of the Kalium page or its ordering.

Add a global composite recency index and benchmark a second type-prefixed recency index. Ship the type-prefixed index only if it materially improves sparse filtered sources without increasing end-to-end message-write median or p95 latency by more than 10 percent.

## Considered Options

- Reusing the legacy view with a cursor was rejected because it would retain most of the projection, join, grouping, and first-page costs.
- A separate visible-row unread stream was deferred because indexed page-local counters retain one self-contained list flow; it can be reconsidered if invalidation churn is still material.
- A timestamp-only restoration position was rejected because conversations can share a millisecond timestamp; the conversation ID is retained only as the old position's deterministic tie-breaker.
- A new activity-time invalidation key was rejected because the existing order key is correct and substantially simpler, despite occasional unnecessary invalidation from pending-connection timestamp changes.
- Full-text search was deferred; contains-search keeps its current semantics and is benchmarked separately because keyset pagination cannot make a leading-wildcard search bounded.

## Consequences

- Pending connections no longer receive special recency from `Connection.last_update_date`; they use the stored conversation activity time like every other list item.
- Process-death restoration requires the app to save the list position and pixel offset. The internal page cursor remains composite and deterministic.
- The new API and models are additive public ABI. Legacy consumers continue to use the unchanged offset flow.
- Non-search loads must avoid count queries, global unread scans, and temporary ordering B-trees. Correctness tests must cover equal timestamps, membership parity, append, prepend, centered refresh, invalidation, and missing or duplicated rows.
- The accepted performance gates compare the new and legacy flows on representative Android SQLCipher data: first-page median at most 50 percent of legacy, middle/deep median at most 1.25 times the new first-page median, and deep-page p95 at most 1.5 times the new first-page p95.
