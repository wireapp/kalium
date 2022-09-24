package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.SelfConversationIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onSuccess
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class UpdateConversationReadDateUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val clientRepository: ClientRepository,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider
) {

    suspend operator fun invoke(conversationId: QualifiedID, time: Instant) {
        selfConversationIdProvider().onSuccess {
            conversationRepository.updateConversationReadDate(conversationId, time.toString())
            sendLastReadMessageToOtherClients(conversationId, it, time)
        }
    }

    private suspend fun sendLastReadMessageToOtherClients(conversationId: QualifiedID, selfConversationId: QualifiedID, time: Instant) {
        val generatedMessageUuid = uuid4().toString()

        clientRepository.currentClientId().flatMap { currentClientId ->
            val regularMessage = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.LastRead(
                    messageId = generatedMessageUuid,
                    unqualifiedConversationId = conversationId.value,
                    conversationId = conversationId,
                    time = time
                ),
                conversationId = selfConversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited,
            )
            messageSender.sendMessage(regularMessage)
        }
    }

}
