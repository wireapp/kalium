package com.wire.kalium.logic.data.conversation

// TODO(qol): rename to CreateConversationParam
data class ConversationOptions(
    val access: Set<Conversation.Access>? = null,
    val accessRole: Set<Conversation.AccessRole>? = null,
    val readReceiptsEnabled: Boolean = false,
    val protocol: Protocol = Protocol.PROTEUS,
    // TODO(qol): use ClientId class
    val creatorClientId: String? = null
) {
    enum class Protocol {
        PROTEUS, MLS
    }
}
