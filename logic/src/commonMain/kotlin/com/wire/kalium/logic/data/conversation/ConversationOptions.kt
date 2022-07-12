package com.wire.kalium.logic.data.conversation

data class ConversationOptions(
    val access: Set<Conversation.Access>? = null,
    val accessRole: Set<Conversation.AccessRole>? = null,
    val readReceiptsEnabled: Boolean = false,
    val protocol: Protocol = Protocol.PROTEUS,
    val creatorClientId: ClientId? = null
) {
    enum class Protocol {
        PROTEUS, MLS
    }
}
