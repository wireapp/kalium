# Conversation List

The conversation-list context defines which locally stored conversations appear in the user's conversation overview and the summary shown for each one.

## Language

**Conversation list source**:
The user-selected subset of conversations: all conversations, groups, channels, or one-to-one conversations, optionally viewed from the archive.
_Avoid_: Tab, filter type

**Eligible conversation**:
A locally stored conversation that satisfies the selected conversation list source and the established visibility rules.
_Avoid_: Valid conversation, visible row

**Conversation list item**:
The display summary of one eligible conversation, containing only the identity and state needed to render its row in the conversation list.
_Avoid_: Conversation details, conversation DTO

**Conversation activity time**:
The stored time used to position a conversation by recency in the conversation list. It reflects selected conversation activity rather than every change made to the conversation.
_Avoid_: Last message time, new activity time

**Conversation list position**:
A deterministic boundary in conversation-list recency order used to restore an approximate viewport. It preserves the old neighborhood even if the conversation that supplied the boundary later moves.
_Avoid_: Selected conversation, current conversation position

**Unread state**:
The summarized unread events associated with a conversation, including messages, mentions, replies, missed calls, and knocks.
_Avoid_: New activity

**Conversation list preview**:
The short content summary shown in a conversation list item. A saved draft takes precedence over the latest displayable message.
_Avoid_: Last message, message details

**Joinable call**:
An ongoing call associated with a conversation that the current user can join.
_Avoid_: Active call
