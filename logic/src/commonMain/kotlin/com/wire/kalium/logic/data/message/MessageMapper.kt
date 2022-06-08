package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

interface MessageMapper {
    fun fromMessageToEntity(message: Message): MessageEntity
    fun fromEntityToMessage(message: MessageEntity): Message
    fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage
}

class MessageMapperImpl(
    private val idMapper: IdMapper,
    private val memberMapper: MemberMapper,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper()
) : MessageMapper {

    override fun fromMessageToEntity(message: Message): MessageEntity {
        val status = when (message.status) {
            Message.Status.PENDING -> MessageEntity.Status.PENDING
            Message.Status.SENT -> MessageEntity.Status.SENT
            Message.Status.READ -> MessageEntity.Status.READ
            Message.Status.FAILED -> MessageEntity.Status.FAILED
        }
        val visibility = when (message.visibility) {
            Message.Visibility.VISIBLE -> MessageEntity.Visibility.VISIBLE
            Message.Visibility.HIDDEN -> MessageEntity.Visibility.HIDDEN
            Message.Visibility.DELETED -> MessageEntity.Visibility.DELETED
        }
        return when (message) {
            is Message.Client -> MessageEntity.Client(
                id = message.id,
                content = message.content.toMessageEntityContent(),
                conversationId = idMapper.toDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.toDaoModel(message.senderUserId),
                senderClientId = message.senderClientId.value,
                status = status,
                editStatus = when (message.editStatus) {
                    Message.EditStatus.NotEdited -> MessageEntity.EditStatus.NotEdited
                    is Message.EditStatus.Edited -> MessageEntity.EditStatus.Edited(message.editStatus.lastTimeStamp)
                },
                visibility = visibility
            )
            is Message.Server -> MessageEntity.Server(
                id = message.id,
                content = message.content.toMessageEntityContent(),
                conversationId = idMapper.toDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.toDaoModel(message.senderUserId),
                status = status,
                visibility = visibility
            )
        }
    }

    override fun fromEntityToMessage(message: MessageEntity): Message {
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
        return when (message) {
            is MessageEntity.Client -> Message.Client(
                id = message.id,
                content = message.content.toMessageContent(),
                conversationId = idMapper.fromDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.fromDaoModel(message.senderUserId),
                senderClientId = ClientId(message.senderClientId),
                status = status,
                editStatus = when (val editStatus = message.editStatus) {
                    MessageEntity.EditStatus.NotEdited -> Message.EditStatus.NotEdited
                    is MessageEntity.EditStatus.Edited -> Message.EditStatus.Edited(editStatus.lastTimeStamp)
                },
                visibility = visibility
            )
            is MessageEntity.Server -> Message.Server(
                id = message.id,
                content = message.content.toMessageContent(),
                conversationId = idMapper.fromDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.fromDaoModel(message.senderUserId),
                status = status,
                visibility = visibility
            )
        }
    }

    override fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage =
        when (val content = message.content) {
            is MessageContent.Text -> LocalNotificationMessage.Text(author, message.date, content.value)
            // TODO(notifications): Handle other message types
            else -> LocalNotificationMessage.Text(author, message.date, "Something not a text")
        }

    @Suppress("ComplexMethod")
    private fun MessageContent.Client.toMessageEntityContent(): MessageEntityContent.Client = when (this) {
        is MessageContent.Text -> MessageEntityContent.Text(messageBody = this.value)
        is MessageContent.Asset -> with(this.value) {
            val assetWidth = when (metadata) {
                is Image -> metadata.width
                is Video -> metadata.width
                else -> null
            }
            val assetHeight = when (metadata) {
                is Image -> metadata.height
                is Video -> metadata.height
                else -> null
            }
            val assetDurationMs = when (metadata) {
                is Video -> metadata.durationMs
                is Audio -> metadata.durationMs
                else -> null
            }
            MessageEntityContent.Asset(
                assetSizeInBytes = sizeInBytes,
                assetName = name,
                assetMimeType = mimeType,
                assetDownloadStatus = assetMapper.fromDownloadStatusToDaoModel(downloadStatus),
                assetOtrKey = remoteData.otrKey,
                assetSha256Key = remoteData.sha256,
                assetId = remoteData.assetId,
                assetToken = remoteData.assetToken,
                assetEncryptionAlgorithm = remoteData.encryptionAlgorithm?.name,
                assetWidth = assetWidth,
                assetHeight = assetHeight,
                assetDurationMs = assetDurationMs,
                assetNormalizedLoudness = if (metadata is Audio) metadata.normalizedLoudness else null
            )
        }
        is MessageContent.Unknown -> MessageEntityContent.Unknown(this.encodedData)
        else -> MessageEntityContent.Unknown()
    }

    private fun MessageContent.Server.toMessageEntityContent(): MessageEntityContent.Server = when (this) {
        is MessageContent.MemberChange -> {
            val memberUserIdList = this.members.map { memberMapper.toDaoModel(it).user }
            when (this) {
                is MessageContent.MemberChange.Added ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.ADDED)
                is MessageContent.MemberChange.Removed ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.REMOVED)
            }
        }
    }

    private fun MessageEntityContent.Client.toMessageContent(): MessageContent.Client = when (this) {
        is MessageEntityContent.Text -> MessageContent.Text(this.messageBody)
        is MessageEntityContent.Asset -> MessageContent.Asset(
            MapperProvider.assetMapper().fromAssetEntityToAssetContent(this)
        )
        is MessageEntityContent.Unknown -> MessageContent.Unknown(this.encodedData)
    }

    private fun MessageEntityContent.Server.toMessageContent(): MessageContent.Server = when (this) {
        is MessageEntityContent.MemberChange -> {
            val memberList = this.memberUserIdList.map { memberMapper.fromDaoModel(it) }
            when (this.memberChangeType) {
                MessageEntity.MemberChangeType.ADDED -> MessageContent.MemberChange.Added(memberList)
                MessageEntity.MemberChangeType.REMOVED -> MessageContent.MemberChange.Removed(memberList)
            }
        }
    }
}
