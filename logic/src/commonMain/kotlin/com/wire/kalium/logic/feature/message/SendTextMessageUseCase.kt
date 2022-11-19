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
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.toTimeInMillis
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.util.string.toUTF16BEByteArray
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

class SendTextMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageContentEncryptor: MessageContentEncoder,
    private val messageRepository: MessageRepository,
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
                    quotedMessageReference = quotedMessageId?.let { quotedMessageId ->
                        createQuotedMessageReference(
                            conversationId = conversationId,
                            quotedMessageId = quotedMessageId
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

    private suspend fun createQuotedMessageReference(
        conversationId: ConversationId,
        quotedMessageId: String
    ): MessageContent.QuoteReference? {
        val messageResult = messageRepository.getMessageById(conversationId, quotedMessageId)

        return if (messageResult.isLeft()) {
            null
        } else {
            val message = messageResult.value
            val messageTimeStampInMillis = message.date.toTimeInMillis()

            val quotedMessageSha256 = when (val messageContent = message.content) {
                is MessageContent.Asset ->
                    messageContentEncryptor.encryptMessageAsset(
                        messageTimeStampInMillis = messageTimeStampInMillis,
                        assetId = messageContent.value.remoteData.assetId
                    )

                is MessageContent.Text ->
                    messageContentEncryptor.encryptMessageTextBody(
                        messageTimeStampInMillis = messageTimeStampInMillis,
                        messageTextBody = messageContent.value
                    )

                else -> null
            }

            MessageContent.QuoteReference(
                quotedMessageId = quotedMessageId,
                quotedMessageSha256 = quotedMessageSha256
            )
        }
    }

}

class MessageContentEncoder {
    fun encryptMessageAsset(
        messageTimeStampInMillis: Long,
        assetId: String
    ): ByteArray {
        val messageTimeStampInSec = messageTimeStampInMillis / MILLIS_IN_SEC
        val messageTimeUTF16BE = messageTimeStampInSec.toString().toUTF16BEByteArray()

        val assetIdUTF16BE = assetId.toUTF16BEByteArray()

        return assetIdUTF16BE + messageTimeUTF16BE
    }

    fun encryptMessageTextBody(
        messageTimeStampInMillis: Long,
        messageTextBody: String
    ): ByteArray {
        val messageTimeStampInSec = messageTimeStampInMillis / MILLIS_IN_SEC
        val messageTimeUTF16BE = messageTimeStampInSec.toString().toUTF16BEByteArray()

        val messageTextBodyUTF16BE = messageTextBody.toUTF16BEByteArray()

        return messageTextBodyUTF16BE + messageTimeUTF16BE
    }

    private companion object {
        const val MILLIS_IN_SEC = 1000
    }


}
