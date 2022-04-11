package com.wire.kalium.logic.data.conversation

/**
 * ```
 * 0 -> All notifications are displayed
 * 1 -> Only mentions are displayed (normal messages muted)
 * 2 -> Only normal notifications are displayed (mentions are muted) -- legacy, not used
 * 3 -> No notifications are displayed
 * ```
 */
sealed class MutedConversationStatus(open val status: Int = 0) {
    object AllAllowed : MutedConversationStatus(0)
    object OnlyMentionsAllowed : MutedConversationStatus(1)
    @Deprecated("For legacy mapping purpose only, not used")
    private object MentionsMuted : MutedConversationStatus(2)
    object AllMuted : MutedConversationStatus(3)
}
