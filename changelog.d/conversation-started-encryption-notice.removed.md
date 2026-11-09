Removed the stored conversation-started encryption notice. Kalium wrote it only when the client saw a conversation being created or joined, so conversations that arrived through slow sync, such as every existing conversation after a new login, never had it. Apps now draw the notice themselves at the start of a conversation's history.

  - ABI: breaking.
  - Source: removed `MessageContent.ConversationStartedUnverifiedWarning`, `MessageEntityContent.ConversationStartedUnverifiedWarning`, `MessageEntity.ContentType.CONVERSATION_STARTED_UNVERIFIED_WARNING`, `MessageDAO.getConversationStartedUnverifiedWarning`, `SystemMessageInserter.insertConversationStartedUnverifiedWarning` and `NewGroupConversationSystemMessagesCreator.conversationStartedUnverifiedWarning`. `persistAcceptedConnection` now takes only the connection.
  - Behavior: creating or joining a conversation, accepting a connection and resolving a one-to-one conversation no longer store the notice.
  - Migration: user database migration 166 deletes stored notices. Consumers that rendered the message should draw the notice at the top of the timeline instead.
