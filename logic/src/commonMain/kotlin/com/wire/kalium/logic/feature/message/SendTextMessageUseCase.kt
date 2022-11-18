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
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.toTimeInMillis
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.util.string.toUTF16BEByteArray
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import okio.ByteString.Companion.encodeUtf8

class SendTextMessageUseCase internal constructor(
    private val persistMessage: PersistMessageUseCase,
    private val selfUserId: QualifiedID,
    private val provideClientId: CurrentClientIdProvider,
    private val slowSyncRepository: SlowSyncRepository,
    private val messageSender: MessageSender,
    private val messageContentEncryptor: MessageContentEncryptor,
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
                    quotedMessageReference = quotedMessageId?.let { createQoutedMessageReference(it) }
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

    private suspend fun createQoutedMessageReference(conversationId: ConversationId, messageId: String): MessageContent.QuoteReference? {
        val messageResult = messageRepository.getMessageById(conversationId, messageId)

        return messageResult.fold({ }, { message: Message ->

        })
    }

}

class MessageContentEncryptor(private val messageRepository: MessageRepository) {

    suspend fun encryptMessageContent(conversationId: ConversationId, messageId: String): Either<CoreFailure, String> {
        val messageResult = messageRepository.getMessageById(conversationId, messageId)

        return messageResult.flatMap { message ->
            val messageContent = message.content
            val messageTimeStampInSec = (message.date.toTimeInMillis() / 1000)

            when (messageContent) {
                is MessageContent.Asset -> encryptMessageAsset()
                is MessageContent.Text -> {
                    encodeMessageText(messageContent.value, messageTimeStampInSec)
//                     val messageBody = messageContent.value
//                     val utf16BEMessageBody = messageBody.toUTF16BEByteArray()
//                     val utf16BEMessageTimeStampInSec = (messageTimeStamp.toTimeInMillis() / 1000).toString().toUTF16BEByteArray()
                }

                is MessageContent.Calling -> TODO()
                is MessageContent.Cleared -> TODO()
                is MessageContent.DeleteForMe -> TODO()
                is MessageContent.DeleteMessage -> TODO()
                MessageContent.Empty -> TODO()
                is MessageContent.FailedDecryption -> TODO()
                is MessageContent.Knock -> TODO()
                is MessageContent.LastRead -> TODO()
                is MessageContent.Reaction -> TODO()
                is MessageContent.RestrictedAsset -> TODO()

                is MessageContent.TextEdited -> TODO()
                is MessageContent.Unknown -> TODO()
                is MessageContent.Availability -> TODO()
                MessageContent.Ignored -> TODO()
                is MessageContent.ConversationRenamed -> TODO()
                is MessageContent.MemberChange.Added -> TODO()
                is MessageContent.MemberChange.Removed -> TODO()
                MessageContent.MissedCall -> TODO()
                is MessageContent.TeamMemberRemoved -> TODO()
            }
            Either.Right("test")
        }
    }

    private fun encodeMessageText(value: String, messageTimeStampInSec: Long) {

    }

}
