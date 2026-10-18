# Loading the conversation list

`session.conversations.observeConversationListItems` opens the keyset-paginated conversation list
from [ADR 15](../adr/0015-keyset-paginated-conversation-list.md). The existing paginated use case,
`ConversationQueryConfig`, `ConversationDetailsWithEvents`, and their behavior remain unchanged.

## Entry point

```kotlin
import com.wire.kalium.logic.feature.conversation.observeConversationListItems

val items: Flow<PagingData<ConversationListItem>> =
    session.conversations.observeConversationListItems(
        pagingConfig = PagingConfig(pageSize = 30, enablePlaceholders = false),
        query = ConversationListQuery(source = ConversationListSource.GROUPS),
        initialPosition = savedPosition,
    )
```

```kotlin
public data class ConversationListQuery(
    public val source: ConversationListSource = ConversationListSource.ALL,
    public val searchQuery: String = "",
    public val fromArchive: Boolean = false,
    public val onlyInteractionEnabled: Boolean = false,
    public val strictMlsFilter: Boolean = true,
    public val unreadOnly: Boolean = false,
)
```

`ConversationListSource` is `ALL`, `GROUPS`, `CHANNELS`, or `ONE_ON_ONE`. Favorites and custom
folders are not sources; they keep their existing flows. `unreadOnly` keeps conversations with at
least one unread event.

`PagingConfig` stays caller-supplied, so the client controls page size, initial load size, prefetch
distance, and retained pages. Placeholders must be disabled and `jumpThreshold` left at its default;
the use case rejects other configurations.

## Restoring a position

`ConversationListPosition(activityTime, conversationId)` is an old boundary in list order, not a
reference to the conversation's current location. Kalium loads around both stored values. The
conversation ID only breaks ties between equal activity times; it is never resolved to a newer
timestamp.

Save the position from `item.position` together with the query and the first visible item's pixel
offset. Pass it as `initialPosition` only when the source, archive mode, search, and other filters
still match. Discard it when the query changes.

## Items

`ConversationListItem` is a sealed hierarchy:

- `RegularGroup` and `Channel` implement `Group`: name, self membership, and
  `adminlessGroupDeletionTimestamp`. A `Channel` also has `access` (`PUBLIC` or `PRIVATE`).
- `OneOnOne` carries the other user as a `ConversationListUser`.
- These three implement `WithMessages`: mute status, legal-hold status, a
  `ConversationListUnreadState`, and an optional `ConversationListPreview`.
- `PendingConnection` carries only the other user. Its row is driven by user and connection state,
  so it does not implement `WithMessages`.

The preview reuses `MessagePreviewContent`, which already represents every row-preview state,
including `Draft`. The row has no action-menu details such as folders or member roles; load current
conversation details when opening that menu.

## Ordering and refresh

- Items are ordered by `activityTime DESC, conversationId ASC`. `activityTime` is the stored
  `Conversation.last_modified_date`; it is not calculated from messages.
- A saved nonblank draft takes precedence over the latest displayable message. Archived rows have no
  draft or message preview.
- Draft writes and adminless-deletion reminders invalidate loaded pages, so previews and reminders
  update without a client refresh. Clearing messages does not remove the reminder timestamp.
- The five unread counters stay distinct, so a client keeps its badge priority and mute behavior.
- The paging source supports append, prepend, and centered refresh without exposing its cursor. It
  returns no total count and does not populate `itemsBefore` or `itemsAfter`.
- Joinable calls, playing audio, selection, search highlighting, and self-user legal-hold state are
  client-side overlays. Call changes and folder changes do not invalidate the list.

## Adoption

Clients adopt this API separately. Android replaces activity-section grouping with recency order,
keeps call and audio indicators as overlays, and saves the position with its scroll offset.

The list uses `AS MATERIALIZED`, which needs SQLite 3.35 or newer. The SQLDelight dialect is not a
runtime guarantee, and the unencrypted Android path uses platform SQLite, so restrict adoption to
supported runtimes.

The [Android benchmarks](../../test/android-benchmarks/README.md) compare the legacy and new lists
on first, middle, and deep loads, including filtered sources and contains-search, and measure the
optional type-prefixed index's write cost. No results are recorded yet, so the ADR's performance
gates remain unverified and the type-prefixed index stays out of production.
