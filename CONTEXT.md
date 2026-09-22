# Super Kalium

Super Kalium provides messaging behavior for Wire clients across platforms.

## Language

### Message timeline

**Message timeline**:
The ordered messages and system events inside one conversation, including deleted-message markers where the app displays them.
_Avoid_: Conversation list, which means the list of conversations.

**Message list item**:
The information needed to display one message or system event in the message timeline.
_Avoid_: Message preview, which can mean the last-message summary in a conversation list.

**Message details**:
The information about an individual message needed to inspect it or perform an action on it, such as downloading its attachment or retrying delivery.

### Conversation list

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

### Account ownership

**Conversation member**: A user who belongs to a conversation. The user's devices may each have their own cryptographic membership.

**MLS client**: One device identity in an MLS group. Removing this client does not, by itself, remove the user from the conversation.

**Prepared account**: An account whose app has completed the setup needed for an extension to receive using its existing state.
