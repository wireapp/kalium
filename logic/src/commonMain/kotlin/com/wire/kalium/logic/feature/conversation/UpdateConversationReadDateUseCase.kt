package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.call.ConversationType
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.feature.message.SendConfirmationUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.datetime.Instant

/**
 * This use case will update last read date for a conversation.
 * After that, will sync against other user's registered clients, using the self conversation.
 */
class UpdateConversationReadDateUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val selfConversationIdProvider: SelfConversationIdProvider,
    private val sendConfirmation: SendConfirmationUseCase,
) {

    /**
     * @param conversationId The conversation id to update the last read date.
     * @param time The last read date to update.
     */
    suspend operator fun invoke(conversationId: QualifiedID, time: Instant) {
        selfConversationIdProvider().flatMap { selfConversationIds ->
            sendConfirmation(conversationId)
            conversationRepository.updateConversationReadDate(conversationId, time.toIsoDateTimeString())
            selfConversationIds.foldToEitherWhileRight(Unit) { selfConversationId, _ ->
                sendLastReadMessageToOtherClients(conversationId, selfConversationId, time)
            }
        }
        return
    }

    private suspend fun sendLastReadMessageToOtherClients(
        conversationId: QualifiedID,
        selfConversationId: QualifiedID,
        time: Instant
    ): Either<CoreFailure, Unit> {
        val generatedMessageUuid = uuid4().toString()

        return currentClientIdProvider().flatMap { currentClientId ->
            val regularMessage = Message.Signaling(
                id = generatedMessageUuid,
                content = MessageContent.LastRead(
                    messageId = generatedMessageUuid,
                    conversationId = conversationId,
                    time = time
                ),
                conversationId = selfConversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING
            )
            messageSender.sendMessage(regularMessage)
        }
    }

}
