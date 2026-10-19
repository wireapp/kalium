Fixed apps-history notices appearing at catch-up/restart time. Initial conversation
apps availability now uses `MessageContent.NewConversationAppsEnabled`, separately
from actor-attributed `ConversationAppsEnabledChanged` actions. Creation notices
retain the creation event's time; access-change notices retain the server's time
when supplied.

  - Source: consumers exhaustively matching `MessageContent.System`, persistence
    `MessageEntityContent.System`, or `MessageEntity.ContentType` must handle the
    additive initial-status variant/discriminator. Initial status should be
    rendered neutrally, without claiming someone changed apps access.
  - ABI: the new sealed variant and persisted enum entry are additive.
    Public `EventContentDTO.Conversation.AccessUpdate` gains nullable `time` with a
    default of `null`; existing source calls remain valid, but its constructor and
    generated data-class method signatures change. Recompile binary consumers of
    that DTO. Missing legacy payload timestamps remain unknown; insertion retains
    the existing current-time fallback when no server time is available.
  - Internal API: `Event.Conversation.AccessUpdate` gains nullable `dateTime`.
    Internal `NewGroupConversationSystemMessagesCreator.conversationAppsAccessIfEnabled`
    overloads and `SystemMessageInserter.insertConversationAppsAccessChanged` gain
    an `instant` parameter defaulting to now for local operations. Internal call
    sites, overrides and mocks must update their signatures; these declarations
    are not part of the checked public ABI surface.
  - Storage: `NEW_CONVERSATION_APPS_ENABLED` is a new discriminator in the existing
    `Message.content_type` column with no payload or SQL schema change. Existing
    apps-change rows and their timestamps remain untouched because their initial
    or mutation provenance cannot be established reliably. Older SDK readers
    fall back to text for unknown content types and can render this marker as an
    empty text message; downgrade compatibility is not established. Use untouched
    pre-upgrade stores for rollback rather than opening newly written stores in
    an older SDK.
