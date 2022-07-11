package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserId

sealed interface CreateConversationParam {
    val name: String
    val userIdSet: Set<UserId>
    val access: Set<Conversation.Access>?
    val accessRole: Set<Conversation.AccessRole>?
    val readReceiptsEnabled: Boolean
    val teamId: TeamId?
    data class Proteus(
        override val name: String,
        override val userIdSet: Set<UserId>,
        override val access: Set<Conversation.Access>? = null,
        override val accessRole: Set<Conversation.AccessRole>? = null,
        override val readReceiptsEnabled: Boolean = false,
        override val teamId: TeamId?
    ): CreateConversationParam
    data class MLS(
        override val name: String,
        override val userIdSet: Set<UserId>,
        override val access: Set<Conversation.Access>? = null,
        override val accessRole: Set<Conversation.AccessRole>? = null,
        override val readReceiptsEnabled: Boolean = false,
        override val teamId: TeamId?
    ): CreateConversationParam
}
