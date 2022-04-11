package com.wire.kalium.logic.data.conversation

/**
 * ```
 * 0 -> All notifications are displayed
 * 1 -> Only mentions are displayed (normal messages muted)
 * 2 -> Only normal notifications are displayed (mentions are muted) -- legacy, not used
 * 3 -> No notifications are displayed
 * ```
 */
enum class MutedConversationStatus {
    ALL_ALLOWED,
    ONLY_MENTIONS_ALLOWED,
    MENTIONS_MUTED,
    ALL_MUTED;

    companion object {
        fun fromOrdinal(ordinal: Int): MutedConversationStatus? = values().firstOrNull { ordinal == it.ordinal }
    }
}
