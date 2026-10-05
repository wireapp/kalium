# Message timeline pagination plan

Status: shared API implemented under [ADR 11](../adr/0011-keyset-pagination-for-message-timelines.md).
Client adoption and performance measurements are pending. No speedup has been measured.

## Implemented in Super Kalium

- `MessageScope.openMessageTimeline` opens an account/conversation-scoped generation with concrete
  requests, pages, and results. `MessageScope.getPaginatedFlowOfMessageListItems` provides the Paging adapter.
- `MessageTimeline.sq` selects eligible keys using the stored timestamp and SQLite's binary ID order.
  Migration 146 adds the key view and conversation/date/ID index. Existing indexes remain available.
- Page assembly loads scalar display rows, batches child collections and users, and hydrates no
  full messages or `UserDetails`. Related-user bindings are chunked at 500 IDs.
- A session registers dependencies before its first read, keeps them for every retained page,
  cancels invalidated loads, and releases listeners on close. The Paging flow closes its source
  when collection ends.
- Correctness tests cover fresh and upgraded databases, both pagination APIs, query plans, rendering
  fields, timestamp ties, scoped cursors, mutation refresh, and listener lifetime.

See the [API guide](../guides/message-timeline.md) for usage. Benchmark fixtures are delivered in a
separate stacked PR after the implementation PR. They must be run and assessed before client rollout.
The Android checkout referenced below has not been migrated by this shared-library change.

## Current behavior and scope

Inspected on 2026-09-05 at Super Kalium `6a671865f` and Wire Android `fb0f15d16`.
Android source was read from `/Users/mohamadjaara/Desktop/wire/main/wire-android`.
Android paths below are relative to that checkout; Kalium paths are relative to this repository.

| Source | Finding |
| --- | --- |
| `logic/.../feature/message/GetPaginatedFlowOfMessagesByConversationUseCase.kt` | Exposes `startingOffset` and `Flow<PagingData<Message.Standalone>>`; defaults to all visibility values. |
| `logic/.../data/message/MessageRepositoryExtensions.kt` | Maps persisted entities to full messages; remote Nomad history has separate pagination state. |
| `data/persistence/.../dao/message/MessageExtensions.kt` | Selects through the offset source; `withMultipartAttachmentsList` queries attachments once per multipart message. |
| `data/persistence/.../dao/message/AsyncQueryPagingSource.kt` | Runs a count for every load, returns positional counts, supports jumps, and derives refresh keys from numeric positions. |
| `data/persistence/src/commonMain/db_user/com/wire/kalium/persistence/Messages.sq` | Timeline query sorts by date only. Existing conversation/date indexes omit the message ID tie-breaker. Send acknowledgement can change creation dates and shift other pending messages. |
| `data/persistence/src/commonMain/db_user/com/wire/kalium/persistence/MessageDetailsView.sq` | Loads full sender data, asset credentials, raw payloads, quotes, read counts, and collection aggregates. |
| Android `app/src/main/kotlin/com/wire/android/ui/home/conversations/usecase/GetMessagesForConversationUseCase.kt` | Page size 20, initial size 60, prefetch distance 30, placeholders enabled, no explicit retained-page limit. Filters rows through `flatMap`. |
| Android `app/src/main/kotlin/com/wire/android/ui/home/conversations/messages/ConversationMessagesViewModel.kt` | Turns a searched message or unread count into an offset. Asset actions already fetch details by conversation and message ID. |
| Android `app/src/main/kotlin/com/wire/android/ui/home/conversations/usecase/GetUsersForMessageUseCase.kt` | Fetches additional users per message for member events and partial delivery. |
| Android `app/src/main/kotlin/com/wire/android/ui/home/conversations/messagelist/ConversationMessageList.kt` | Uses reverse layout, message keys, placeholder spacers, loaded indices, and item-count changes for scrolling behavior. |

The shorthand `logic/...` refers to `logic/src/commonMain/kotlin/com/wire/kalium/logic/`.
The shorthand `data/persistence/...` refers to
`data/persistence/src/commonMain/kotlin/com/wire/kalium/persistence/`.

Scope is the messages inside a conversation. Keep full-message detail APIs for sending, retry,
download, export, and inspection. Do not migrate conversation lists, search-result pagination,
media-gallery pagination, or the server's Nomad cursor in this change. Opening a search result in
the timeline does migrate to an ID anchor when that consumer adopts the new API.

Introduce a separately named keyset API beside the current offset API. Keep both supported for a
few months after the new API ships. Preserve existing signatures, full-message return types,
offset semantics, placeholders, and navigation behavior for callers of the old API. Changes below
to the list model and anchor-based navigation apply to consumers that explicitly adopt the new API.
Shared schema or query-helper changes must preserve the existing path.

## Data the list needs

Use a shared, typed `MessageListItem`, with regular, deleted, and system content variants. Keep
Android resources, formatted strings, Compose state, and SwiftUI state in their apps. Reuse small
existing value types when they fit; avoid reusing `Message`, `User`, or asset transport objects just
to satisfy existing mapper signatures.

The following is the initial contract, traced through Android's `MessageMapper.kt`,
`MessageContentMapper.kt`, `RegularMessageContentMapper.kt`, `SystemMessageContentMapper.kt`, and
`ui/home/conversations/model/UIMessage.kt`.

| Area | Include in the list contract | Load separately or omit from the list |
| --- | --- | --- |
| Identity and order | Conversation ID, message ID, exact creation timestamp, sender ID, self flag, sender client ID where used by actions | Global row number and total timeline count |
| Sender display | Name, accent, preview-avatar reference, availability, membership/user type, connection status, deleted/unavailable flags, legal-hold indicator, guest expiry, bot/app identity | Full avatar, email, phone, handle, protocol capabilities, unrelated conversation and profile fields. Preserve derived unavailable and membership semantics. |
| Status | Sending/sent/delivered/read/failure state, read count where rendered, edit timestamp, deleted state, expiry duration and self-deletion state, decryption error code and resolved flag | Raw failed-decryption bytes, unknown encoded payload, retry internals |
| Text | Full displayed text and mention ranges/IDs; unknown-content type name for its fallback label | No silent truncation of visible message text |
| Quote | Original ID, sender display, date/edit time, verification/deleted/unavailable state, text or asset/location/multipart preview data, self-quote flag | Original full message and attachment credentials. Existing quoted-multipart detail loading stays separate. |
| Asset | Asset reference, name, MIME type, size, dimensions, duration, and a derived completeness flag matching current image visibility rules | Encryption keys, hashes, tokens, encoded media, and transport details; resolve through asset loaders |
| Link preview | URL and range, permanent URL, title/summary, image dimensions and current local image reference | Image credentials; resolve them by message ID and preview offset if an image needs fetching |
| Reactions | Emoji, count, whether self reacted | Lists of reactors, which belong to reaction details |
| Partial delivery | Failure categories and recipient display names/IDs/domain grouping required by the current bubble | Full recipient users; keep current failure presentation during this migration |
| Multipart | Attachment identities and display metadata consumed by the attachment renderer, including availability/status and preview references | Full attachment operational data after attachment actions can resolve it by ID |
| Other content | Location coordinates/name/zoom; composite button IDs/text/selection; type-specific system-event values and member display names | Unused columns from unrelated content variants |

The list currently passes full link-preview and multipart objects through to downstream components.
Changing the top-level mapper alone cannot remove those fields safely. Audit `LinkPreviewCard`,
`MessageContentAndStatus`, `MultipartAttachmentsViewModel`, attachment UI mapping, and image/audio
loaders when replacing those types. Preserve current preview selection and URL-range behavior.
Resolve download credentials at the point of use, including automatic image loading.

Keep transfer progress and audio playback in their existing observation paths. Do not invalidate
and rebuild the entire list for every progress tick.

A row limit bounds message count, not bytes. Large text, many attachments, and large system-event
member lists can still produce large pages. Measure these explicitly. Do not silently cap content
that Android displays; a collapsed preview with on-demand expansion would be a separate UI decision.

## Query and cursor contract

Use `MessageTimelineCursor` containing the stored timestamp and message ID, plus conversation and
display-policy identity for validation. Scope the loader to the signed-in account and keep cursors
ephemeral to its paging session. Reject conversation or policy mismatches; discard cursors when the
account changes. Dates use the database precision without a formatting
or precision-losing round trip. Comparisons use the same binary ID collation as SQL.

Proposed requests are `Latest`, `Older(cursor)`, `Newer(cursor)`, `AroundMessage(messageId)`, and
`AtReadBoundary(lastReadDate)`. Each has a requested item limit. Start with 20 for ordinary loads,
60 for initial/around loads, and a maximum of 100; validate positive sizes and clamp oversized
requests. These are proposed defaults to verify with benchmarks, not new UI requirements.

For a fixed conversation, use this key-selection shape:

```sql
SELECT id, creation_date
FROM Message
WHERE conversation_id = :conversationId
  AND visibility IN :visibility
  AND (creation_date, id) < (:cursorDate, :cursorId)
ORDER BY creation_date DESC, id DESC
LIMIT :limitPlusOne;
```

This is the chronological predicate sketch. Add the agreed display eligibility predicates before
the limit. Latest omits the cursor condition. Newer uses `>` with ascending order to select the
nearest newer messages, then reverses the retained keys into descending presentation order.
Use separate statements for latest/older/newer so nullable-cursor conditions do not obscure index
selection. Never reverse after retaining the wrong end of the extra-key result.

Take at most the requested number of keys and use the extra key only to establish whether another
page exists in that direction. Load no content for the extra key. Build continuation cursors from
the first and last retained database keys. An empty result terminates that direction; a deleted
cursor row still works because the cursor retains its tuple values.

For around-message loading, resolve the anchor by both conversation and ID. Include an eligible
anchor once, split the remaining budget between nearest newer and older keys, and fill unused
budget from the other side when near an edge. Probe both sides for continuation. Return keys in
descending order and identify the anchor for the UI. If an existing anchor is no longer eligible,
use its timestamp as the neighborhood. If it is gone, refresh can use the last known tuple; a
fresh jump without a known tuple returns `AnchorNotFound` for the app to handle.

For the first-unread entry point, seek the earliest eligible message strictly newer than the
conversation's `last_read_date`, then load around it. Preserve the current read-boundary equality
rule. If there is no unread message, load latest. Unread badges remain independently observed;
their count no longer sets a database offset or a list index.

### Visibility and filtering

The current app suppresses hidden regular messages, hidden/deleted system events, incomplete
incoming images, and some system events such as another user's admin promotion. It renders a
deleted regular message as a marker. Translate these rules into explicit list eligibility, with
fixtures checked against the current app mapper. Selecting only `VISIBLE` would lose markers.

Put eligibility into key selection wherever the current facts are available in SQL. Project image
completeness as a boolean so the UI does not need credentials to decide whether to show the row.
If a presentation-only exclusion must remain after loading, continuation must advance from scanned
database keys, and an empty rendered batch must not signal exhaustion. Bound each scan batch and
test long runs of excluded messages. Prefer eliminating such exclusions from the shared list path
before rollout; sparse eligibility also belongs in the performance fixtures.

### Index and page assembly

Start by evaluating an index on `(conversation_id, creation_date DESC, id DESC)`. Compare a
visibility-specific variant on `(conversation_id, visibility, creation_date DESC, id DESC)` for
single-visibility requests. A multi-value visibility filter can require merging/sorting ranges;
do not assume that placing visibility first satisfies chronological ordering. Keep the smallest
index set supported by actual query plans and insert benchmarks.

Use `EXPLAIN QUERY PLAN` and measurements on the shipped SQLite/SQLCipher versions. Confirm a seek
near the cursor with no history-sized sort or work proportional to prior pages. Validate tuple
syntax through the repository's SQLDelight compiler as well as the runtime. If a scalar equivalent
is required, verify its plan independently. SQLite describes the relevant index rules in its
[query-planning documentation](https://www.sqlite.org/queryplanner.html).

Run key selection and page assembly in a short read transaction. Query scalar list fields only for
retained IDs, always scoped by conversation, then fetch related collections in page-sized batches.
Deduplicate sender and member IDs before fetching their display fields. Keep client-derived
legal-hold checks restricted to those users instead of pulling the full `UserDetails` view into
the timeline query. Batch attachments,
mentions, previews, buttons, reactions, receipts, and failure recipients where needed; skip empty
categories. Group by full message identity and restore the selected key order explicitly.

Keep this flow in the message DAO and list mapper. Avoid per-item queries, collection joins that
multiply rows before the limit, and whole-conversation aggregation before key selection. Use typed
rows instead of assembling arbitrary content as JSON strings in SQL. Chunk bindings within the
supported driver limit, especially for member IDs and attachments. A few page-scoped queries are
acceptable; query count should not grow once per message.

Add a numbered database migration and fresh-schema indexes together. Replace an existing prefix
index only after checking its other callers and plans. Measure index size and write overhead.

## Refresh and platform integration

Provide a concrete `MessageTimelinePage` with items and nullable older/newer cursors, plus an
action-specific result type consistent with [ADR 7](../adr/0007-swift-friendly-result-types.md).
Keep loading and invalidation contracts usable from Kotlin/Native without Android UI dependencies.
Do not require Swift to consume `PagingData` or create a new platform abstraction for unrelated APIs.

Android uses a dedicated `PagingSource<MessageTimelineCursor, MessageListItem>` over the shared
loader. It reports undefined positional counts, disables placeholders and positional jumping, and
refreshes around the item nearest the viewport anchor. Keep the current page/initial/prefetch
sizes at first. Trial a finite `maxSize` of 200 items and verify page dropping and reload behavior;
the final memory budget must also account for text and nested collections.

`KaliumPager` currently fixes its key type to `Int`. The new timeline can use its own Paging factory
directly. Avoid changing every offset-based caller merely to reuse that wrapper. Put the adapter
alongside the current paging code unless existing platform boundaries require otherwise.

Listen for all dependencies that can affect loaded rows, including message order/visibility,
content, reactions, receipts, sender display, member display, client-derived legal-hold state,
quoted originals, buttons, and attachments. Preserve the repository's `message_list_*` custom notification keys and
`DbInvalidationController` batching. A listener only on `Message` would miss child-table updates.
Keep dependencies for all retained pages, not only the most recently loaded query.

Register invalidation before reading. If the generation changes during key selection or assembly,
discard the result and refresh. Cancel obsolete loads and release listeners when a collector or
Paging source closes. Propagate coroutine cancellation. For Swift, expose the same invalidation
signal with an explicit subscription lifetime and discard pages from obsolete generations.

On edit, deletion, acknowledgement, visibility change, or imported history, invalidate and resolve
the viewport anchor's current key by ID. Restore the same message and pixel offset when it still
exists. Use the saved tuple to find a neighbor when it is gone. Deduplicate retained items by full
message identity. Keyset pagination alone cannot prevent skips or duplicates across mutable sort
keys; refreshing the affected generation is part of correctness.

Migrate Android's `GetMessagesForConversationUseCase`, `ConversationMessagesViewModel`,
`ConversationMessagesViewState`, `ConversationScreen`, and `ConversationMessageList` together:

- Replace `startingOffset`, searched-message positions, and `firstUnreadEventIndex` with message
  anchors and the read boundary. Resolve highlight/scroll positions within the loaded window.
- Change list mappers to consume `MessageListItem`; remove per-message user fetching from this path.
- Preserve stable message keys, date/author grouping, deleted markers, read receipts, and expiry.
- Scroll-to-latest starts a latest load if the current window is around history. Index zero in a
  historical window is not necessarily the newest message.
- Replace autoscroll based only on growing `itemCount` with a check for new latest messages and
  whether the viewport is near the latest edge. Page loading and dropping also change item count.
- Recheck reply/search jumps, audio-message navigation, keyboard focus, and restored scroll state
  after placeholders disappear. Preserve the offline indicator as a synthetic UI item.
- Local older-page exhaustion can still trigger Nomad history fetch. Keep local and remote
  exhaustion separate. Imported rows invalidate the local generation; preserve existing remote
  cursor state, retry behavior, and loading indicators.

## Delivery sequence

Each step should leave existing callers usable. The shared implementation covers the library work
in steps 2, 3, 4, and 6. Step 1's performance baseline, step 5's client migration, and release scheduling
remain open. The sequence below describes adoption through eventual removal.

1. Record baseline behavior and measurements. Add fixtures for all rendered/suppressed content
   variants and trace downstream preview/attachment consumers. Record plans and timings at the
   first page and depths of 1,000, 10,000, and 100,000 messages. Exit when the field contract and
   current ordering/visibility behavior have executable examples.
2. Add key queries, indexes, migration tests, and around/read-boundary lookup to persistence.
   Verify ordering and traversal independently of content mapping. Exit when both directions and
   equal-timestamp groups traverse exactly once and deep loads use an indexed seek.
3. Add the smaller persistence projection, page batch assembly, shared list model, and concrete
   page API in `:logic`. Compare output against the current app fixtures and measure allocations.
   Exit when rendering data is complete and the path avoids full `MessageDetailsView` hydration,
   full `User` objects, and per-message attachment/user queries.
4. Add dependency invalidation and the Android Paging adapter. Exercise a small Kotlin/Native
   consumer of loading, errors, refresh, and cancellation. Exit when stale in-flight loads cannot
   replace the current window and viewport refresh works through timestamp changes.
5. Migrate the Android checkout's timeline and downstream asset/preview consumers. Exit when
   navigation and rendering regressions pass with placeholders disabled and bounded retention.
   Measure through the final UI mapping path, including scrolling and live updates.
6. Publish the additive API with ABI updates and a changelog fragment under ADRs 9 and 10. Document
   the new anchor contract and identify remaining consumers. Support and test both APIs during the
   first few months of adoption; migrating Android alone does not end support for other callers.
7. Deprecate the offset timeline entry point a few months after the new API ships. Announce the
   deprecation release and provide migration guidance to the keyset API. Keep the deprecated API
   available and working throughout the supported compatibility window.
8. Decide removal in a later release after supported consumers migrate and the compatibility window
   closes. Only then remove the old timeline entry point and its exclusive helpers. Keep full-message
   detail APIs and shared offset helpers used by search and galleries.

The migration tracking issue must name the introduction release and a target deprecation release
a few months later before rollout. No exact date is chosen by this ADR. Track removal separately;
the deprecation release is not a removal deadline. Do not implement a fake offset-to-cursor adapter
that scans history or silently route existing callers through the new contract.

## Verification and rollout gates

Correctness coverage must include:

- Existing offset callers retaining their signatures, full-message models, positional navigation,
  and behavior while the new API is in use. Run both paths against fresh and upgraded databases,
  including shared index and helper changes, and retain this coverage through deprecation.
- Empty conversations, short final pages, limits of 1/20/60/100, invalid limits, and around loads
  at both ends. Compare complete bidirectional traversal to one deterministic reference ordering.
- More equal-timestamp messages than a page, duplicate IDs across conversations, cursor-row
  deletion, and cursor reuse with the wrong conversation or display policy.
- New latest messages, backdated imports, ID replacement, pending-to-sent timestamp changes,
  message edits, deletion/expiry, and visibility changes while loading or viewing deep history.
- All eligible/suppressed content variants, large excluded runs, missing sender fallbacks, quotes
  edited/deleted outside the loaded window, and attachment completeness transitions.
- Reactions, read counts, legal-hold/member/sender updates, multipart changes, and buttons updating
  retained pages after a different page was most recently loaded.
- Reply/search jumps, first unread with timestamp ties, unread marker updates, jump to latest,
  autoscroll, page eviction, refresh, cancellation, offline indicators, and remote history retries.
- Fresh database versus upgraded database, and Android/iOS equality of cursor timestamp precision,
  ordering, results, and listener cleanup.

Benchmark the old offset/full projection, keyset/full projection for comparison only, and the final
keyset/list projection. Use text, mixed-media, multipart-heavy, reaction-heavy, and large-member-event
fixtures. Add 10,000- and 100,000-message conversations, dense timestamp ties, and sparse eligibility.
Measure ordinary 20-item and initial 60-item loads plus around-message jumps.

The stacked benchmark change adds [JVM and Android coverage](../../test/benchmarks/message-timeline.md)
for offset/full entities, keyset page assembly, public list mapping, and around-message loads.
It includes these fixture shapes and page sizes, remains opt-in, and has not been run. An isolated
keyset/full-view control and the remaining device measurements below still need coverage.

Reuse `test/benchmarks` for local query comparisons and `test/android-benchmarks` for device
SQLCipher measurements, as described in [ADR 8](../adr/0008-androidx-benchmark-for-on-device-dao-benchmarks.md).
Also measure the iOS production driver. Record cold/warm runs, p50/p95 query and end-to-end mapping
time, SQL statement count, mapped object count, allocated bytes, retained heap, index size, write
cost, and frame timing while scrolling and applying incoming messages.

Release gates are semantic parity, no offsets or per-load total count in the new timeline path,
indexed cursor traversal, page-scoped child queries, and lower measured data/allocation cost on the
mixed-content fixtures. Deep-page timing must not grow with the number of earlier pages; investigate
filter selectivity and query plans if it does. First-load and write regressions need an explicit
explanation before adoption. Set device-specific latency and memory thresholds from step 1's
baseline rather than inventing a percentage improvement now.
