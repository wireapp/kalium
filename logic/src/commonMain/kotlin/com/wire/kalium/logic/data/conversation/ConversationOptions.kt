package com.wire.kalium.logic.data.conversation

data class ConversationOptions(
    val access: Set<Access> = emptySet(),
    val accessRole: Set<AccessRole> = emptySet(),
    val readReceiptsEnabled: Boolean = false,
    val protocol: Protocol = Protocol.PROTEUS
) {
    enum class Protocol {
        PROTEUS, MLS
    }

    enum class AccessRole {
        TEAM_MEMBER, NON_TEAM_MEMBER, GUEST, SERVICE
    }

    enum class Access {
        PRIVATE, INVITE, LINK, CODE
    }
}
