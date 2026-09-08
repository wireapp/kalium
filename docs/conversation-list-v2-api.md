# Conversation List V2 API Design

Status: Implemented in Kalium; performance benchmark execution pending

This document defines the public Kalium API and row model for the keyset-paginated conversation list. The architectural decisions and trade-offs are recorded in [ADR 0011](./adr/0011-keyset-paginated-conversation-list.md).

## Goals

- Expose only the domain data needed to render a conversation-list row.
- Keep the keyset cursor and persistence entities internal to Kalium.
- Support the four currently paginated sources without changing legacy APIs.
- Let a client restore an approximate viewport without following a conversation that moved.

## Public entry point

```kotlin
public val ConversationScope.observeConversationListItems: ObserveConversationListItemsUseCase

public interface ObserveConversationListItemsUseCase {
    public operator fun invoke(
        pagingConfig: PagingConfig,
        query: ConversationListQuery = ConversationListQuery(),
        initialPosition: ConversationListPosition? = null,
    ): Flow<PagingData<ConversationListItem>>
}

public data class ConversationListQuery(
    public val source: ConversationListSource = ConversationListSource.ALL,
    public val searchQuery: String = "",
    public val fromArchive: Boolean = false,
    public val onlyInteractionEnabled: Boolean = false,
    public val strictMlsFilter: Boolean = true,
)

public enum class ConversationListSource {
    ALL,
    GROUPS,
    CHANNELS,
    ONE_ON_ONE,
}
```

`PagingConfig` remains caller-supplied so clients control page size, initial load size, prefetch distance, and maximum retained pages. V2 requires placeholders to be disabled and does not support a jump threshold; invalid configurations should fail fast.

Favorites and custom folders are intentionally absent from `ConversationListSource`. They continue to use their existing flows.

## Restoration position

```kotlin
public data class ConversationListPosition(
    public val activityTime: Instant,
    public val conversationId: ConversationId,
)
```

The position is an old boundary in list order, not a reference to the conversation's current location. Kalium must use both stored values directly when loading around it. The conversation ID disambiguates conversations with equal activity times; it must not be resolved to a newer timestamp.

Android saves the position together with the query identity and first-visible-item pixel offset. If the query changes, the saved position is discarded.

## List item model

```kotlin
public sealed interface ConversationListItem {
    public val conversationId: ConversationId
    public val activityTime: Instant

    public val position: ConversationListPosition
        get() = ConversationListPosition(activityTime, conversationId)

    public sealed interface WithMessages : ConversationListItem {
        public val mutedStatus: MutedConversationStatus
        public val legalHoldStatus: Conversation.LegalHoldStatus
        public val unreadState: ConversationListUnreadState
        public val preview: ConversationListPreview?
    }

    public sealed interface Group : WithMessages {
        public val name: String?
        public val isSelfUserMember: Boolean
    }

    public data class RegularGroup(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        override val name: String?,
        override val isSelfUserMember: Boolean,
        override val mutedStatus: MutedConversationStatus,
        override val legalHoldStatus: Conversation.LegalHoldStatus,
        override val unreadState: ConversationListUnreadState,
        override val preview: ConversationListPreview?,
    ) : Group

    public data class Channel(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        override val name: String?,
        override val isSelfUserMember: Boolean,
        public val access: ConversationListChannelAccess,
        override val mutedStatus: MutedConversationStatus,
        override val legalHoldStatus: Conversation.LegalHoldStatus,
        override val unreadState: ConversationListUnreadState,
        override val preview: ConversationListPreview?,
    ) : Group

    public data class OneOnOne(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        public val otherUser: ConversationListUser,
        override val mutedStatus: MutedConversationStatus,
        override val legalHoldStatus: Conversation.LegalHoldStatus,
        override val unreadState: ConversationListUnreadState,
        override val preview: ConversationListPreview?,
    ) : WithMessages

    public data class PendingConnection(
        override val conversationId: ConversationId,
        override val activityTime: Instant,
        public val otherUser: ConversationListUser,
    ) : ConversationListItem
}

public data class ConversationListUser(
    public val id: UserId,
    public val name: String?,
    public val avatarAssetId: UserAssetId?,
    public val accentId: Int,
    public val availabilityStatus: UserAvailabilityStatus,
    public val userType: UserTypeInfo,
    public val connectionStatus: ConnectionState,
    public val deleted: Boolean,
    public val defederated: Boolean,
)

public enum class ConversationListChannelAccess {
    PUBLIC,
    PRIVATE,
}

public data class ConversationListUnreadState(
    public val messages: Int = 0,
    public val mentions: Int = 0,
    public val replies: Int = 0,
    public val missedCalls: Int = 0,
    public val knocks: Int = 0,
)

public data class ConversationListPreview(
    public val content: MessagePreviewContent,
    public val isSelfMessage: Boolean,
)
```

`MessagePreviewContent` is reused because it already represents every row-preview state, including `Draft`. The larger legacy `MessagePreview` is not exposed by V2.

`PendingConnection` deliberately does not implement `WithMessages`: its row is driven by user and connection state, not mute, legal-hold, unread, or message-preview data.

## Behavioral invariants

- Items are ordered by `activityTime DESC, conversationId ASC`.
- `activityTime` is the stored `Conversation.last_modified_date`; V2 does not calculate it from messages.
- A saved nonblank draft takes precedence over the latest displayable message.
- Archived rows have no draft or last-message preview, matching the legacy list.
- Draft writes do not invalidate V2. A client can request a refresh when returning from a conversation to display the latest draft.
- The five unread counters remain distinct so Android can retain its existing badge priority and mute behavior.
- Joinable-call state, playing audio, selection, search highlighting, and self-user legal-hold state are client-side overlays.
- The internal Paging source supports append, prepend, and centered refresh without exposing its cursor.
- V2 returns no total count and does not populate `itemsBefore` or `itemsAfter`.

## Compatibility

All declarations above are additive. The existing paginated use case, `ConversationQueryConfig`, `ConversationDetailsWithEvents`, and their behavior remain unchanged.

The declarations are recorded in the public API dumps and changelog. Field names, order, defaults, and nullability are part of the binary API.

## Client adoption

Import `com.wire.kalium.logic.feature.conversation.observeConversationListItems` and supply a `PagingConfig` with
`enablePlaceholders = false`. Leave `jumpThreshold` at its default. Pass the saved `ConversationListPosition`
as `initialPosition` only when the source, archive mode, search, and other filters still match.

Clients adopt this API separately. Android must replace activity-section grouping with recency order,
retain call and audio indicators as overlays, and save the position together with its scroll offset.
The narrow row does not contain action-menu details such as folders or member roles; clients can load
current conversation details when opening that menu. Favorites and custom folders keep their existing flows.

V2 requires SQLite 3.35 or newer for `AS MATERIALIZED`. The SQLDelight dialect is not a runtime version
guarantee. The unencrypted Android path still uses platform SQLite, so clients must restrict V2 adoption
to supported runtimes. This change does not bundle a replacement SQLite library.

## Performance validation

The implementation includes the global composite recency index and correctness tests. The Android
[benchmarks and run guide](../test/android-benchmarks/README.md) compare legacy and V2 first,
middle, and deep loads on deterministic SQLCipher fixtures, including filtered sources and contains-search.
A separate message-write benchmark evaluates the optional type-prefixed index in the fixture only.

The benchmarks have not been run. The performance targets in ADR 0011 remain unverified, and the
optional type-prefixed index remains excluded from production until measurements justify it.
