package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Clock

/**
 * This use case can be used by QA to send read and delivery receipts. This debug function can be used to test correct
 * client behaviour. It should not be used by clients itself.
 */
class SendConfirmationUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {

    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()

        // todo: handle toggles for 1:1 and convo config
        val messageIds = conversationRepository.detailsById(conversationId).fold({
            kaliumLogger.e("[SendConfirmationUseCase] There was an unknown error trying to get latest messages $it")
            emptyList()
        }, { conversation ->
            messageRepository.getMessagesByConversationIdAndVisibilityAfterDate(conversationId, conversation.lastReadDate)
                .firstOrNull()
                ?.map {
                    it.id
                } ?: emptyList()
        })

        // Skip in case no new messages to send confirmations receipts
        if (messageIds.isEmpty()) {
            kaliumLogger.d("[SendConfirmationUseCase] No messages to send confirmation signal")
            return Either.Right(Unit)
        }

        return currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Confirmation(
                    Message.ConfirmationType.READ,
                    messageIds.first(),
                    messageIds.drop(1)
                ), // todo: handle first and type read toggle config
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
            )

            kaliumLogger.d("[SendConfirmationUseCase] Sending message")
            messageSender.sendMessage(message)
        }.onFailure {
            if (it is CoreFailure.Unknown) {
                kaliumLogger.e("[SendConfirmationUseCase] There was an unknown error trying to send the message $it", it.rootCause)
                it.rootCause?.printStackTrace()
            } else {
                kaliumLogger.e("T[SendConfirmationUseCase] here was an error trying to send the message $it")
            }
        }.onSuccess {
            kaliumLogger.d("[SendConfirmationUseCase] Confirmation signal sent successful")
        }
    }
}
