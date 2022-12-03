package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
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
 * This use case allows to send a confirmation type [ReceiptType.READ] or [ReceiptType.DELIVERED] accordingly to
 * configurations criteria.
 *
 * - For 1:1 we take into consideration [ObserveReadReceiptsEnabled]
 * - For group conversations we have to look for each group conversation configuration.
 */
internal class SendConfirmationUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
) {
    private companion object {
        const val TAG = "[SendConfirmationUseCase]"
    }

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.MESSAGES) }

    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> {
        syncManager.waitUntilLive()

        // todo: handle toggles for 1:1 and convo config
        val messageIds = conversationRepository.detailsById(conversationId).fold({
            logger.e("$TAG There was an unknown error trying to get latest messages $it")
            emptyList()
        }, { conversation ->
            messageRepository.getMessagesByConversationIdAndVisibilityAfterDate(conversationId, conversation.lastReadDate).firstOrNull()
                ?.map { it.id }
                ?: emptyList()
        })

        if (messageIds.isEmpty()) {
            logger.d("$TAG Skipping, NO messages to send confirmation signal")
            return Either.Right(Unit)
        }

        return currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Receipt(ReceiptType.READ, messageIds),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.PENDING,
            )

            messageSender.sendMessage(message)
        }.onFailure {
            logger.e("$TAG there was an error trying to send the confirmation signal $it")
        }.onSuccess {
            logger.d("$TAG confirmation signal sent successful")
        }
    }
}
