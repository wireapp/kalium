package com.wire.kalium.logic.data.conversation

data class ConversationOptions(
    val access: Set<Access>? = null,
    val accessRole: Set<AccessRole>? = null,
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
