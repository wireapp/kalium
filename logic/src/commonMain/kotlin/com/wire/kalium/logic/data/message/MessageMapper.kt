package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.*
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.BaseMessageEntity

interface MessageMapper {
    fun fromMessageToEntity(message: Message): BaseMessageEntity
    fun fromEntityToMessage(message: BaseMessageEntity): Message
}

class MessageMapperImpl(private val idMapper: IdMapper) : MessageMapper {
    override fun fromMessageToEntity(message: Message): BaseMessageEntity {
        val status = when (message.status) {
            Message.Status.PENDING -> BaseMessageEntity.Status.PENDING
            Message.Status.SENT -> BaseMessageEntity.Status.SENT
            Message.Status.READ -> BaseMessageEntity.Status.READ
            Message.Status.FAILED -> BaseMessageEntity.Status.FAILED
        }
        val messageEntity = when (message.content) {
            is MessageContent.Text -> {
                BaseMessageEntity.TextMessageEntity(
                    content = message.content.value,
                    id = message.id,
                    conversationId = idMapper.toDaoModel(message.conversationId),
                    date = message.date,
                    senderUserId = idMapper.toDaoModel(message.senderUserId),
                    senderClientId = message.senderClientId.value,
                    status = status
                )
            }
            is MessageContent.Asset -> {
                with(message.content.value) {
                    BaseMessageEntity.AssetMessageEntity(
                        assetMimeType = mimeType,
                        assetSize = size,
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
                        id = message.id,
                        conversationId = idMapper.toDaoModel(message.conversationId),
                        date = message.date,
                        senderUserId = idMapper.toDaoModel(message.senderUserId),
                        senderClientId = message.senderClientId.value,
                        status = status
                    )
                }
            }
            else -> BaseMessageEntity.TextMessageEntity(
                content = null,
                id = message.id,
                conversationId = idMapper.toDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.toDaoModel(message.senderUserId),
                senderClientId = message.senderClientId.value,
                status = status
            )
        }
        return messageEntity
    }

    override fun fromEntityToMessage(message: BaseMessageEntity): Message {
        val content = when {
            // If there is text content is a Text Message
            message.content != null -> {
                MessageContent.Text(message.content ?: "")
            }

            // If the asset size is not null and there is a defined mime type and asset Id, it is an Asset Message
            message.assetMimeType != null && message.assetId != null && message.assetSize != null -> {
                MessageContent.Asset(MapperProvider.assetMapper().fromMessageEntityToAssetContent(message))
            }

            else -> MessageContent.Unknown
        }
        val status = when (message.status) {
            BaseMessageEntity.Status.PENDING -> Message.Status.PENDING
            BaseMessageEntity.Status.SENT -> Message.Status.SENT
            BaseMessageEntity.Status.READ -> Message.Status.READ
            BaseMessageEntity.Status.FAILED -> Message.Status.FAILED
        }
        return Message(
            message.id,
            content,
            idMapper.fromDaoModel(message.conversationId),
            message.date,
            idMapper.fromDaoModel(message.senderUserId),
            ClientId(message.senderClientId),
            status
        )
    }
}
