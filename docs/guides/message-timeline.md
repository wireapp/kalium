# Loading a message timeline

`session.messages.openMessageTimeline(conversationId)` opens the additive keyset API from
[ADR 11](../adr/0011-keyset-pagination-for-message-timelines.md). Existing offset APIs continue to
return full messages with their existing positional behavior.

## Pages and anchors

```kotlin
val timeline = session.messages.openMessageTimeline(conversationId)
try {
    when (val result = timeline.load(MessageTimelineRequest.Latest, limit = 60)) {
        is MessageTimelineResult.Success -> {
            val page = result.page
            render(page.items)
            page.olderCursor?.let { cursor ->
                when (val older = timeline.load(MessageTimelineRequest.Older(cursor), limit = 20)) {
                    is MessageTimelineResult.Success -> appendOlder(older.page.items)
                    MessageTimelineResult.Invalidated -> refreshAroundVisibleMessage()
                    else -> handleTimelineResult(older)
                }
            }
        }
        else -> handleTimelineResult(result)
    }
} finally {
    timeline.close()
}
```

`Latest`, `Older`, and `Newer` return items in descending creation-date and ID order. Newer loads
select the nearest newer messages first. Positive limits are capped at 100. A null continuation
cursor means that direction is exhausted locally. It says nothing about remote Nomad history.

Use `AroundMessage(messageId)` for a reply or search jump. It includes the anchor once and fills
unused space from the other side near either end. An existing but suppressed anchor opens its
neighborhood. A missing anchor without a saved cursor returns `AnchorNotFound`.

Use `AtReadBoundary(lastReadDate)` for the first unread entry. It selects the earliest eligible
message strictly newer than the read date, with ID as the timestamp tie-breaker, then loads around
it. If nothing is unread, it returns latest. Keep unread badge counts in their existing observation
path.

The default policy includes visible content and deleted regular-message markers. `VISIBLE_ONLY`
omits deleted markers. Both policies exclude hidden content, deleted system events, incomplete
incoming displayable images, and admin promotions that do not include self. Filtering occurs before
the page limit, so a run of excluded rows does not produce a false end of history.

## Session lifetime and refresh

Every `MessageTimeline` is one database generation. Observe its `invalidated` state while showing
its pages. Relevant message, child-table, user, and client writes invalidate the generation. The
session keeps dependencies for all pages and uses the existing batched database notifications.

When invalidated:

1. Save the visible item's ID, cursor, and native UI pixel offset.
2. Discard the generation's pages and close the old session.
3. Open a new session with the same conversation and policy.
4. Load `AroundMessage(visibleId, lastKnownCursor = visibleCursor)`.
5. Restore the visible message and pixel offset, or the returned neighbor anchor if it was removed.

Refresh resolves a message's current tuple by ID before using its saved tuple. This handles send
acknowledgements that move pending messages, edits, expiry, deletion, and imported history.
A saved tuple does not freeze a message's position across writes.

Continuation cursors belong to the session that issued them. Another generation rejects them for
`Older` or `Newer`; the new generation accepts them only as an around-message fallback. Another
account/database instance, conversation, or display policy rejects them. Cursors are ephemeral.
Do not serialize them or retain them across account changes.

`close()` releases listeners, invalidates the session, and cancels outstanding reads. A caller's
coroutine cancellation propagates. `Invalidated`, `InvalidLimit`, `InvalidCursor`, `AnchorNotFound`,
`Failure`, and `Success` are concrete result variants. Swift can use this page API without
`PagingData`; connect `invalidated` through the app's existing Flow integration and close the session
when the view leaves.

## Rendering and actions

`MessageListItem` contains sender display, status, expiry, typed content, reactions, and delivery
failure recipients. System events reuse the existing typed event values and include member display
data. Users are deduplicated for each page; missing related users keep their IDs with `isMissing`.

Text and mention ranges remain complete. Link previews keep URL offsets, titles, summaries, image
dimensions, and local paths. A quoted ID with no quote details means the original is unavailable
locally. Quoted multipart details remain a separate load.

Assets and multipart attachments contain display metadata and references. Resolve credentials and
operational data through existing message and attachment APIs at the point of use, including
automatic image loading. Use conversation/message IDs for assets and an additional URL offset for
link-preview images. Keep transfer progress, audio playback, and waveform observation on their
existing paths. The list model carries no encryption keys, tokens, hashes, or raw unknown/decryption
payloads.

A page limit bounds row count. It does not truncate large text, attachments, reactions, or member
lists. Measure their allocation and retained-memory cost before adoption.

## Paging adoption

`session.messages.getPaginatedFlowOfMessageListItems(conversationId)` supplies a custom-key Paging
source. Defaults are page size 20, initial load 60, prefetch distance 30, and retained maximum 200.
Placeholders and positional jumps are disabled. Refresh uses the item nearest the viewport anchor.
The returned flow closes its active source when collection ends.

Adopting apps must migrate list mapping, search/reply navigation, unread entry, and scroll restoration
together. Store stable message identities in the UI and deduplicate by conversation/message ID.
Opening a historical window does not make its first item the latest message. Scroll-to-latest must
start a latest load. Page loading and dropping also change item count, so item-count growth alone
must not trigger autoscroll.

After local older-page exhaustion, apps can use the existing remote-history fetch API. Keep its
cursor, retry state, and loading indicators separate. Imported messages invalidate the local session.

The new API introduction and offset API deprecation releases are still pending release planning. Support
both APIs for the adoption period described in ADR 11; deprecation and eventual removal are separate
release decisions. Benchmark results and client navigation checks remain required before rollout.
