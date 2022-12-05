package com.wire.kalium.logic.data.conversation

/**
 * Conversation muting settings type
 */
sealed class MutedConversationStatus(open val status: Int = 0) {
    /**
     * 0 -> All notifications are displayed
     */
    object AllAllowed : MutedConversationStatus(0)

    /**
     * 1 -> Only mentions and replies are displayed (normal messages muted)
     */
    object OnlyMentionsAndRepliesAllowed : MutedConversationStatus(1)

    /**
     * 3 -> No notifications are displayed
     */
    object AllMuted : MutedConversationStatus(3)
}
