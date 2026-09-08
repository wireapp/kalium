# Conversation List V2 API Design

Status: Draft for implementation

This document defines the proposed public Kalium API and row model for the keyset-paginated conversation list. The architectural decisions and trade-offs are recorded in [ADR 0011](./adr/0011-keyset-paginated-conversation-list.md).

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
- A saved draft takes precedence over the latest displayable message.
- The five unread counters remain distinct so Android can retain its existing badge priority and mute behavior.
- Joinable-call state, playing audio, selection, search highlighting, and self-user legal-hold state are client-side overlays.
- The internal Paging source supports append, prepend, and centered refresh without exposing its cursor.
- V2 returns no total count and does not populate `itemsBefore` or `itemsAfter`.

## Compatibility

All declarations above are additive. The existing paginated use case, `ConversationQueryConfig`, `ConversationDetailsWithEvents`, and their behavior remain unchanged.

Before release, the concrete declarations must be added to the public API dumps and documented in the changelog. Because these data classes become binary API, field names, order, defaults, and nullability must be finalized before merging the implementation.
