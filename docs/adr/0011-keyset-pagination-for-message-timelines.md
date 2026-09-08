---
status: accepted
---

# 11. Keyset pagination and a smaller message list model

Date: 2026-09-05

The existing message timeline uses numeric offsets over `MessageDetailsView` and returns full
`Message.Standalone` objects. A separate API uses keyset pagination over message keys and a
dedicated `MessageListItem` model containing the data needed to render the timeline. The new path
avoids deep-offset traversal and unused message details. Keep both APIs supported for a few months
after the new API ships, then deprecate the offset API.

The shared loader, concrete page API, and Paging adapter are implemented in this repository.
See the [API guide](../guides/message-timeline.md) for session lifetime, anchors, and adoption.
Client migration and device measurements remain rollout gates. The implementation does not claim
a measured speedup or start the offset API's deprecation period.

## Evidence

In the inspected Super Kalium checkout, `MessageExtensionsImpl.getPagingSource` uses
`AsyncQueryPagingSource`. Each load counts messages and selects from `MessageDetailsView` with
`ORDER BY date DESC LIMIT ... OFFSET ...`. The view includes full sender details, asset credentials,
unknown and failed-decryption payloads, quotes, and collection aggregates. Multipart attachments
then require one additional query per multipart message.

The Android app requests 20 messages per page, 60 initially, and prefetches at a distance of 30.
Its mappers use only part of each full message, but also need enough information for every supported
bubble, system event, status, quote, reaction, and attachment. Some system events and delivery
failures cause additional user queries during per-message mapping. Android also uses placeholders
and numeric positions for unread and searched-message navigation.

These are code findings, not measured performance results. See the
[implementation plan](../plans/message-timeline-pagination.md) for source references, field mapping,
and the benchmark work required before rollout.

## Decision

- Add a separately named keyset entry point. Preserve the existing API's signatures, full-message
  return types, offset semantics, and behavior. Consumers opt into the new API explicitly.
- Order each conversation by `creation_date DESC, id DESC`. Use the exact stored timestamp and
  SQLite's ID ordering in the cursor. The ID resolves timestamp ties deterministically.
- Select a bounded page of keys from `Message` using an indexed cursor predicate, then load list
  data only for those keys. Return at most the requested number of items, with one extra key used
  to detect another page. Batch attachments and related users for the selected page.
- Introduce a typed `MessageListItem` contract. Keep rendering data and references for actions.
  Resolve full details through existing message and asset APIs when an action needs them.
  Do not construct full messages and discard fields afterward.
- Provide latest, older, newer, and around-message loading. Consumers adopting the new API replace
  timeline positions and placeholders with message anchors. Keep unread counts for badges,
  outside the new paging path.
- Keep the query and list model in shared code. Expose concrete page and result types usable by
  Swift, with an Android Paging adapter for the current app. Compose and SwiftUI retain their
  native UI behavior. This work does not introduce a module or a general pagination framework.
- Treat pages as part of an invalidatable database generation. Refresh around the visible message
  when relevant data changes, including changes to the stored timestamp. A cursor does not freeze
  a message's position across writes.

The active path is chronological. `PendingMessagesPagingSource` and pending-specific SQL exist,
but `getPagerForConversation` does not use them in either inspected checkout. This proposal
preserves the active ordering. A future pending-first mode needs a separate ordering contract.

## Tradeoffs

Adding only a cursor to `MessageDetailsView` would reduce offset work while retaining unnecessary
data and per-message attachment queries. A smaller projection with offsets would retain deep-page
traversal. The proposed change addresses both costs.

A permanently materialized timeline table would require extra writes and synchronization when
users, receipts, reactions, quotes, or attachments change. Start with indexed reads from existing
tables and page-scoped batch queries. Consider persisted summaries only if measurements show a
remaining problem that needs them.

Keyset pages do not provide arbitrary global row numbers. Android must migrate navigation,
scroll-to-latest, and refresh anchoring together with paging. Equal-timestamp messages gain a
defined order that the existing query does not specify. Relevant writes can still move messages;
generation invalidation and anchor restoration handle that case.

Support and test both pagination APIs during the initial adoption period. Target deprecation of the
offset timeline API a few months after the new API ships, with the exact release recorded in the
migration plan. Deprecation keeps the old API available and working; it does not remove it. Decide
removal separately after consumers migrate and the supported compatibility window closes, following
[ADR 9](0009-public-api-abi-and-changelog-governance.md). Full-message detail APIs remain supported.
Search-result lists, media galleries, exports, and remote history cursors remain separate work.

SQLite documents the deep-offset cost and indexed tuple alternative in its
[scrolling-window example](https://www.sqlite.org/rowvalue.html#scrolling_window_queries).
Android Paging supports custom keys and refresh anchoring through
[`PagingSource`](https://developer.android.com/reference/kotlin/androidx/paging/PagingSource).
