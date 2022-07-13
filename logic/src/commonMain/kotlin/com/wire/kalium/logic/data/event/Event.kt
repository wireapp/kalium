package com.wire.kalium.logic.data.event

import com.wire.kalium.cryptography.utils.EncryptedData
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.Connection
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.conversation.ConversationResponse
import kotlinx.datetime.Clock

sealed class Event(open val id: String) {

    sealed class Conversation(
        id: String,
        open val conversationId: ConversationId
    ) : Event(id) {
        data class NewMessage(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val senderClientId: ClientId,
            val timestampIso: String,
            val content: String,
            val encryptedExternalContent: EncryptedData?
        ) : Conversation(id, conversationId)

        data class NewMLSMessage(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val timestampIso: String,
            val content: String
        ) : Conversation(id, conversationId)

        data class NewConversation(
            override val id: String,
            override val conversationId: ConversationId,
            val timestampIso: String,
            val conversation: ConversationResponse
        ) : Conversation(id, conversationId)

        data class MemberJoin(
            override val id: String,
            override val conversationId: ConversationId,
            val addedBy: UserId,
            val members: List<Member>,
            val timestampIso: String
        ) : Conversation(id, conversationId)

        data class MemberLeave(
            override val id: String,
             override val conversationId: ConversationId,
            val removedBy: UserId,
            val removedList: List<UserId>,
            val timestampIso: String
        ) : Conversation(id, conversationId)

        data class MLSWelcome(
            override val id: String,
            override val conversationId: ConversationId,
            val senderUserId: UserId,
            val message: String,
            val timestampIso: String = Clock.System.now().toString()
        ) : Conversation(id, conversationId)


    }

    sealed class FeatureConfig(
        id: String,
    ) : Event(id) {
        data class FeatureConfigUpdated(
            override val id: String,
            val name: String,
            val status: String,
        ) : FeatureConfig(id)
    }

    sealed class User(
        id: String,
    ) : Event(id) {

        data class NewConnection(
            override val id: String,
            val connection: Connection
        ) : User(id)
    }

    data class Unknown(override val id: String) : Event(id)
}
