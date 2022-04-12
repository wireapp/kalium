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
     * 1 -> Only mentions are displayed (normal messages muted)
     */
    object OnlyMentionsAllowed : MutedConversationStatus(1)

    /**
     * 2 -> Only normal notifications are displayed (mentions are muted) -- legacy, not used
     */
    @Deprecated("For legacy mapping purpose only, not used")
    private object MentionsMuted : MutedConversationStatus(2)

    /**
     * 3 -> No notifications are displayed
     */
    object AllMuted : MutedConversationStatus(3)
}
