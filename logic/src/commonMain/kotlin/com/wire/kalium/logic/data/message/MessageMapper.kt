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

    override fun fromMessageToEntity(message: Message): MessageEntity = when (message) {
        is Message.Client -> message.toMessageEntity()
        is Message.Server -> message.toMessageEntity()
    }

    override fun fromEntityToMessage(message: MessageEntity): Message = when (message) {
        is MessageEntity.Client -> message.toMessage()
        is MessageEntity.Server -> message.toMessage()
    }

    override fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage =
        when (val content = message.content) {
            is MessageContent.Text -> LocalNotificationMessage.Text(author, message.date, content.value)
            // TODO(notifications): Handle other message types
            else -> LocalNotificationMessage.Text(author, message.date, "Something not a text")
        }

    private fun Message.Client.toMessageEntity(): MessageEntity = MessageEntity.Client(
        id = this.id,
        content = this.content.toMessageEntityContent(),
        conversationId = idMapper.toDaoModel(this.conversationId),
        date = this.date,
        senderUserId = idMapper.toDaoModel(this.senderUserId),
        senderClientId = this.senderClientId.value,
        status = this.status.toMessageEntityStatus(),
        visibility = this.visibility.toMessageEntityVisibility()
    )

    private fun Message.Server.toMessageEntity(): MessageEntity = MessageEntity.Server(
        id = this.id,
        content = this.content.toMessageEntityContent(),
        conversationId = idMapper.toDaoModel(this.conversationId),
        date = this.date,
        senderUserId = idMapper.toDaoModel(this.senderUserId),
        status = this.status.toMessageEntityStatus(),
        editStatus = this.editStatus.toMessageEntityEditStatus(),
        visibility = this.visibility.toMessageEntityVisibility()
    )

    private fun MessageEntity.Client.toMessage(): Message = Message.Client(
        id = this.id,
        content = this.content.toMessageContent(),
        conversationId = idMapper.fromDaoModel(this.conversationId),
        date = this.date,
        senderUserId = idMapper.fromDaoModel(this.senderUserId),
        senderClientId = ClientId(this.senderClientId),
        status = this.status.toMessageStatus(),
        editStatus = this.editStatus.toMessageEditStatus(),
        visibility = this.visibility.toMessageVisibility()
    )

    private fun MessageEntity.Server.toMessage(): Message = Message.Server(
        id = this.id,
        content = this.content.toMessageContent(),
        conversationId = idMapper.fromDaoModel(this.conversationId),
        date = this.date,
        senderUserId = idMapper.fromDaoModel(this.senderUserId),
        status = this.status.toMessageStatus(),
        visibility = this.visibility.toMessageVisibility()
    )

    private fun MessageContent.Client.toMessageEntityContent() = when (this) {
        is MessageContent.Text -> MessageEntityContent.Text(messageBody = this.value)
        is MessageContent.Asset -> with(this.value) {
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
                assetWidth = when (metadata) {
                    is Image -> metadata.width
                    is Video -> metadata.width
                    else -> null
                },
                assetHeight = when (metadata) {
                    is Image -> metadata.height
                    is Video -> metadata.height
                    else -> null
                },
                assetDurationMs = when (metadata) {
                    is Video -> metadata.durationMs
                    is Audio -> metadata.durationMs
                    else -> null
                },
                assetNormalizedLoudness = if(metadata is Audio) metadata.normalizedLoudness else null
            )
        }
        else -> MessageEntityContent.Text(messageBody = "")
    }

    private fun MessageContent.Server.toMessageEntityContent() = when (this) {
        is MessageContent.MemberChange -> {
            val memberUserIdList = this.members.map { memberMapper.toDaoModel(it).user }
            when (this) {
                is MessageContent.MemberChange.Join ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.JOIN)
                is MessageContent.MemberChange.Leave ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.LEAVE)
            }
        }
    }

    private fun MessageEntityContent.Client.toMessageContent() = when (this) {
        is MessageEntityContent.Text -> MessageContent.Text(this.messageBody)
        is MessageEntityContent.Asset -> MessageContent.Asset(
            MapperProvider.assetMapper().fromAssetEntityToAssetContent(this)
        )
    }

    private fun MessageEntityContent.Server.toMessageContent() = when (this) {
        is MessageEntityContent.MemberChange -> {
            val memberList = this.memberUserIdList.map { memberMapper.fromDaoModel(it) }
            when (this.memberChangeType) {
                MessageEntity.MemberChangeType.JOIN -> MessageContent.MemberChange.Join(memberList)
                MessageEntity.MemberChangeType.LEAVE -> MessageContent.MemberChange.Leave(memberList)
            }
        }
    }

    private fun Message.Status.toMessageEntityStatus() = when (this) {
        Message.Status.PENDING -> MessageEntity.Status.PENDING
        Message.Status.SENT -> MessageEntity.Status.SENT
        Message.Status.READ -> MessageEntity.Status.READ
        Message.Status.FAILED -> MessageEntity.Status.FAILED
    }

    private fun MessageEntity.Status.toMessageStatus() = when (this) {
        MessageEntity.Status.PENDING -> Message.Status.PENDING
        MessageEntity.Status.SENT -> Message.Status.SENT
        MessageEntity.Status.READ -> Message.Status.READ
        MessageEntity.Status.FAILED -> Message.Status.FAILED
    }

    private fun MessageEntity.Visibility.toMessageVisibility() = when (this) {
        MessageEntity.Visibility.VISIBLE -> Message.Visibility.VISIBLE
        MessageEntity.Visibility.HIDDEN -> Message.Visibility.HIDDEN
        MessageEntity.Visibility.DELETED -> Message.Visibility.DELETED
    }

    private fun Message.Visibility.toMessageEntityVisibility() = when (this) {
        Message.Visibility.VISIBLE -> MessageEntity.Visibility.VISIBLE
        Message.Visibility.HIDDEN -> MessageEntity.Visibility.HIDDEN
        Message.Visibility.DELETED -> MessageEntity.Visibility.DELETED
    }

    private fun MessageEntity.EditStatus.toMessageEditStatus() = when(this) {
        MessageEntity.EditStatus.NotEdited -> Message.EditStatus.NotEdited
        is MessageEntity.EditStatus.Edited -> Message.EditStatus.Edited(editStatus.lastTimeStamp)
    }

    private fun Message.EditStatus.toMessageEntityEditStatus() = when(this) {
        Message.EditStatus.NotEdited -> MessageEntity.EditStatus.NotEdited
        is Message.EditStatus.Edited -> MessageEntity.EditStatus.Edited(editStatus.lastTimeStamp)
    }
}
