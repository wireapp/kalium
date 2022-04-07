package com.wire.kalium.logic.data.conversation

enum class MutedConversationStatus {
    ALL_MUTED, ONLY_MENTIONS_ALLOWED, ALL_ALLOWED;

    companion object {
        fun fromOrdinal(ordinal: Int): MutedConversationStatus? = values().firstOrNull { ordinal == it.ordinal }
    }
}
