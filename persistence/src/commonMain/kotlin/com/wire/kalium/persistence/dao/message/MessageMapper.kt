package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.BotEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.util.requireField

@Suppress("LongParameterList")
object MessageMapper {

    private fun createMessageEntity(
        id: String,
        conversationId: QualifiedIDEntity,
        date: String,
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        status: MessageEntity.Status,
        lastEditTimestamp: String?,
        visibility: MessageEntity.Visibility,
        content: MessageEntityContent
    ): MessageEntity = when (content) {
        is MessageEntityContent.Regular -> MessageEntity.Regular(
            content = content,
            id = id,
            conversationId = conversationId,
            date = date,
            senderUserId = senderUserId,
            senderClientId = senderClientId!!,
            status = status,
            editStatus = mapEditStatus(lastEditTimestamp),
            visibility = visibility
        )

        is MessageEntityContent.System -> MessageEntity.System(
            content = content,
            id = id,
            conversationId = conversationId,
            date = date,
            senderUserId = senderUserId,
            status = status,
            visibility = visibility
        )
    }

    private fun mapEditStatus(lastEditTimestamp: String?) =
        lastEditTimestamp?.let { MessageEntity.EditStatus.Edited(it) }
            ?: MessageEntity.EditStatus.NotEdited

    fun toEntityMessageFromView(
        id: String,
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType,
        date: String,
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        status: MessageEntity.Status,
        lastEditTimestamp: String?,
        visibility: MessageEntity.Visibility,
        senderName: String?,
        senderHandle: String?,
        senderEmail: String?,
        senderPhone: String?,
        senderAccentId: Int?,
        senderTeamId: String?,
        senderConnectionStatus: ConnectionEntity.State?,
        senderPreviewAssetId: QualifiedIDEntity?,
        senderCompleteAssetId: QualifiedIDEntity?,
        senderAvailabilityStatus: UserAvailabilityStatusEntity?,
        senderUserType: UserTypeEntity?,
        senderBotService: BotEntity?,
        senderIsDeleted: Boolean?,
        text: String?,
        assetSize: Long?,
        assetName: String?,
        assetMimeType: String?,
        assetUploadStatus: MessageEntity.UploadStatus?,
        assetDownloadStatus: MessageEntity.DownloadStatus?,
        assetOtrKey: ByteArray?,
        assetSha256: ByteArray?,
        assetId: String?,
        assetToken: String?,
        assetDomain: String?,
        assetEncryptionAlgorithm: String?,
        assetWidth: Int?,
        assetHeight: Int?,
        assetDuration: Long?,
        assetNormalizedLoudness: ByteArray?,
        callerId: QualifiedIDEntity?,
        memberChangeList: List<QualifiedIDEntity>?,
        memberChangeType: MessageEntity.MemberChangeType?,
        unknownContentTypeName: String?,
        unknownContentData: ByteArray?,
        restrictedAssetMimeType: String?,
        restrictedAssetSize: Long?,
        restrictedAssetName: String?,
        failedToDecryptData: ByteArray?,
        conversationName: String?,
    ) = when (contentType) {
        MessageEntity.ContentType.TEXT -> MessageEntityContent.Text(
            messageBody = text ?: "",
            mentions = listOf()
        )

        MessageEntity.ContentType.ASSET -> MessageEntityContent.Asset(
            assetSizeInBytes = assetSize.requireField("asset_size"),
            assetName = assetName,
            assetMimeType = assetMimeType.requireField("asset_mime_type"),
            assetUploadStatus = assetUploadStatus,
            assetDownloadStatus = assetDownloadStatus,
            assetOtrKey = assetOtrKey.requireField("asset_otr_key"),
            assetSha256Key = assetSha256.requireField("asset_sha256"),
            assetId = assetId.requireField("asset_id"),
            assetToken = assetToken,
            assetDomain = assetDomain,
            assetEncryptionAlgorithm = assetEncryptionAlgorithm,
            assetWidth = assetWidth,
            assetHeight = assetHeight,
            assetDurationMs = assetDuration,
            assetNormalizedLoudness = assetNormalizedLoudness,
        )

        MessageEntity.ContentType.KNOCK -> MessageEntityContent.Knock(false)
        MessageEntity.ContentType.MEMBER_CHANGE -> MessageEntityContent.MemberChange(
            memberUserIdList = memberChangeList.requireField("memberChangeList"),
            memberChangeType = memberChangeType.requireField("memberChangeType")
        )

        MessageEntity.ContentType.MISSED_CALL -> MessageEntityContent.MissedCall
        MessageEntity.ContentType.UNKNOWN -> MessageEntityContent.Unknown(
            typeName = unknownContentTypeName,
            encodedData = unknownContentData
        )

        MessageEntity.ContentType.FAILED_DECRYPTION -> MessageEntityContent.FailedDecryption(
            failedToDecryptData
        )

        MessageEntity.ContentType.RESTRICTED_ASSET -> MessageEntityContent.RestrictedAsset(
            restrictedAssetMimeType.requireField("assetMimeType"),
            restrictedAssetSize.requireField("assetSize"),
            restrictedAssetName.requireField("assetName")
        )

        MessageEntity.ContentType.CONVERSATION_RENAMED -> MessageEntityContent.ConversationRenamed(conversationName.orEmpty())
    }.let {
        createMessageEntity(
            id,
            conversationId,
            date,
            senderUserId,
            senderClientId,
            status,
            lastEditTimestamp,
            visibility,
            it
        )
    }
}
