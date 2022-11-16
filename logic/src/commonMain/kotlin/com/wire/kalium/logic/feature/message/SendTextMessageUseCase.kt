package com.wire.kalium.logic.feature.message

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.sync.SlowSyncRepository
import com.wire.kalium.logic.data.sync.SlowSyncStatus
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class SendTextMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageContentEncryptor: MessageContentEncryptor,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        text: String,
        mentions: List<MessageMention> = emptyList(),
        quotedMessageId: String? = null
    ): Either<CoreFailure, Unit> = withContext(dispatchers.io) {
        slowSyncRepository.slowSyncStatus.first {
            it is SlowSyncStatus.Complete
        }

        val generatedMessageUuid = uuid4().toString()

        provideClientId().flatMap { clientId ->
            val message = Message.Regular(
                id = generatedMessageUuid,
                content = MessageContent.Text(
                    value = text,
                    mentions = mentions,
                    quotedMessageReference = quotedMessageId?.let {
                        MessageContent.QuoteReference(
                            quotedMessageId = it,
                            quotedMessageSha256 = null
                        )
                    }
                ),
                conversationId = conversationId,
                date = Clock.System.now().toString(),
                senderUserId = selfUserId,
                senderClientId = clientId,
                status = Message.Status.PENDING,
                editStatus = Message.EditStatus.NotEdited
            )
            persistMessage(message)
        }.flatMap {
            messageSender.sendPendingMessage(conversationId, generatedMessageUuid)
        }.onFailure {
            if (it is CoreFailure.Unknown) {
                kaliumLogger.e("There was an unknown error trying to send the message $it", it.rootCause)
                it.rootCause?.printStackTrace()
            } else {
                kaliumLogger.e("There was an error trying to send the message $it")
            }
        }
    }

}


class MessageContentEncryptor(private val messageRepository: MessageRepository) {

    suspend fun encryptMessageContent(conversationId: ConversationId, messageId: String): Either<CoreFailure, String> {
        val messageResult = messageRepository.getMessageById(conversationId, messageId)

        return messageResult.flatMap { message ->
            val messageContent = message.content
            val messageTimeStamp = message.date


            Either.Right("test")
        }


    }

}
