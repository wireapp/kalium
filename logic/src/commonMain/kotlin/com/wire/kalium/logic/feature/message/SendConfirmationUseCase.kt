package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case allows to send a confirmation type [ReceiptType.READ]
 *
 * - For 1:1 we take into consideration [UserPropertyRepository.getReadReceiptsStatus]
 * - For group conversations we have to look for each group conversation configuration.
 */
@Suppress("LongParameterList")
internal class SendConfirmationUseCase internal constructor(
    private val currentClientIdProvider: CurrentClientIdProvider,
    private val syncManager: SyncManager,
    private val messageSender: MessageSender,
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userPropertyRepository: UserPropertyRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {
    private companion object {
        const val TAG = "[SendConfirmationUseCase]"
    }

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.MESSAGES) }

    suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit> = withContext(dispatcher.default) {
        syncManager.waitUntilLive()

        val messageIds = getPendingUnreadMessagesIds(conversationId)
        if (messageIds.isEmpty()) {
            logger.d("$TAG skipping, NO messages to send confirmation signal")
            return@withContext Either.Right(Unit)
        }

        return@withContext currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Receipt(ReceiptType.READ, messageIds),
                conversationId = conversationId,
                date = DateTimeUtil.currentIsoDateTimeString(),
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

    private suspend fun getPendingUnreadMessagesIds(conversationId: ConversationId): List<String> =
        conversationRepository.detailsById(conversationId).fold({
            logger.e("$TAG There was an unknown error trying to get latest messages from conversation $conversationId")
            emptyList()
        }, { conversation ->

            val readReceiptsEnabled = isReceiptsEnabledForConversation(conversation)
            if (!readReceiptsEnabled) {
                emptyList()
            } else {
                messageRepository.getPendingConfirmationMessagesByConversationAfterDate(conversationId, conversation.lastReadDate)
                    .fold({
                        logger.e("$TAG There was an unknown error trying to get messages pending read confirmation $it")
                        emptyList()
                    }, { messages ->
                        messages.map { it.id }
                    })
            }
        })

    private suspend fun isReceiptsEnabledForConversation(conversation: Conversation) =
        if (conversation.type == Conversation.Type.ONE_ON_ONE) {
            userPropertyRepository.getReadReceiptsStatus()
        } else {
            when (conversation.receiptMode) {
                Conversation.ReceiptMode.DISABLED -> false
                Conversation.ReceiptMode.ENABLED -> true
            }
        }

}
