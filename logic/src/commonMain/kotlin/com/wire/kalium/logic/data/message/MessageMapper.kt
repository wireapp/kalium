package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.MessageEntityContent.AssetMessageContent
import com.wire.kalium.persistence.dao.message.MessageEntity.MessageEntityContent.TextMessageContent

interface MessageMapper {
    fun fromMessageToEntity(message: Message): MessageEntity
    fun fromEntityToMessage(message: MessageEntity): Message
    fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage
}

class MessageMapperImpl(private val idMapper: IdMapper) : MessageMapper {
    override fun fromMessageToEntity(message: Message): MessageEntity {
        val status = when (message.status) {
            Message.Status.PENDING -> MessageEntity.Status.PENDING
            Message.Status.SENT -> MessageEntity.Status.SENT
            Message.Status.READ -> MessageEntity.Status.READ
            Message.Status.FAILED -> MessageEntity.Status.FAILED
        }
        val messageContent = when (message.content) {
            is MessageContent.Text -> TextMessageContent(messageBody = message.content.value)
            is MessageContent.Asset -> {
                with(message.content.value) {
                    AssetMessageContent(
                        assetMimeType = mimeType,
                        assetSizeInBytes = sizeInBytes,
                        assetName = name,
                        assetImageWidth = metadata?.let { if (it is Image) it.width else null },
                        assetImageHeight = metadata?.let { if (it is Image) it.height else null },
                        assetVideoWidth = metadata?.let { if (it is Video) it.width else null },
                        assetVideoHeight = metadata?.let { if (it is Video) it.height else null },
                        assetVideoDurationMs = metadata?.let { if (it is Video) it.durationMs else null },
                        assetAudioDurationMs = metadata?.let { if (it is Audio) it.durationMs else null },
                        assetAudioNormalizedLoudness = metadata?.let { if (it is Audio) it.normalizedLoudness else null },
                        assetOtrKey = remoteData.otrKey,
                        assetSha256Key = remoteData.sha256,
                        assetId = remoteData.assetId,
                        assetToken = remoteData.assetToken,
                        assetEncryptionAlgorithm = remoteData.encryptionAlgorithm?.name
                    )
                }
            }
            else -> TextMessageContent(messageBody = "")
        }

        return MessageEntity(
            content = messageContent,
            id = message.id,
            conversationId = idMapper.toDaoModel(message.conversationId),
            date = message.date,
            senderUserId = idMapper.toDaoModel(message.senderUserId),
            senderClientId = message.senderClientId.value,
            status = status
        )
    }

    override fun fromEntityToMessage(message: MessageEntity): Message {
        val content = when (val messageContent = message.content) {
            // It's a text message
            is TextMessageContent -> {
                MessageContent.Text(messageContent.messageBody)
            }

            // It's an asset message
            is AssetMessageContent -> {
                MessageContent.Asset(
                    MapperProvider.assetMapper().fromAssetEntityToAssetContent(messageContent)
                )
            }

            else -> MessageContent.Unknown
        }
        val status = when (message.status) {
            MessageEntity.Status.PENDING -> Message.Status.PENDING
            MessageEntity.Status.SENT -> Message.Status.SENT
            MessageEntity.Status.READ -> Message.Status.READ
            MessageEntity.Status.FAILED -> Message.Status.FAILED
        }
        val visibility = when (message.visibility) {
            MessageEntity.Visibility.VISIBLE -> Message.Visibility.VISIBLE
            MessageEntity.Visibility.HIDDEN -> Message.Visibility.HIDDEN
            MessageEntity.Visibility.DELETED -> Message.Visibility.DELETED
        }
        return Message(
            message.id,
            content,
            idMapper.fromDaoModel(message.conversationId),
            message.date,
            idMapper.fromDaoModel(message.senderUserId),
            ClientId(message.senderClientId),
            status,
            visibility
        )
    }

    override fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage {
        val time = message.date

        return when (message.content) {
            is MessageContent.Text -> LocalNotificationMessage.Text(author, time, message.content.value)
            else -> LocalNotificationMessage.Text(author, time, "Something not a text") //TODO
        }
    }
}
