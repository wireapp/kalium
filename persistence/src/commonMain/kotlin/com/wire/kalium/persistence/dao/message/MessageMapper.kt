package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.MessageAssetContent
import com.wire.kalium.persistence.MessageFailedToDecryptContent
import com.wire.kalium.persistence.MessageRestrictedAssetContent
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.BotEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity

class MessageMapper(private val queries: MessagesQueries) {

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

    private fun toModel(content: MessageRestrictedAssetContent) = MessageEntityContent.RestrictedAsset(
        content.asset_mime_type, content.asset_size, content.asset_name
    )

    private fun toModel(content: MessageAssetContent) = MessageEntityContent.Asset(
        assetSizeInBytes = content.asset_size,
        assetName = content.asset_name,
        assetMimeType = content.asset_mime_type,
        assetDownloadStatus = content.asset_download_status,
        assetOtrKey = content.asset_otr_key,
        assetSha256Key = content.asset_sha256,
        assetId = content.asset_id,
        assetToken = content.asset_token,
        assetDomain = content.asset_domain,
        assetEncryptionAlgorithm = content.asset_encryption_algorithm,
        assetWidth = content.asset_width,
        assetHeight = content.asset_height,
        assetDurationMs = content.asset_duration_ms,
        assetNormalizedLoudness = content.asset_normalized_loudness,
    )

    fun toModel(content: MessageFailedToDecryptContent) = MessageEntityContent.FailedDecryption(
        encodedData = content.unknown_encoded_data
    )

    private fun mapEditStatus(lastEditTimestamp: String?) =
        lastEditTimestamp?.let { MessageEntity.EditStatus.Edited(it) }
            ?: MessageEntity.EditStatus.NotEdited


    fun toEntityMessageFromView(
        conversationId: QualifiedIDEntity,
        failedToDecryptData: ByteArray?,
        id: String,
        contentType: MessageEntity.ContentType,
        conversationId_______: QualifiedIDEntity,
        date: String,
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        status: MessageEntity.Status,
        lastEditTimestamp: String?,
        visibility: MessageEntity.Visibility,
        userId: QualifiedIDEntity?,
        name: String?,
        handle: String?,
        email: String?,
        phone: String?,
        accentId: Int?,
        team: String?,
        connectionStatus: ConnectionEntity.State?,
        previewAssetId: QualifiedIDEntity?,
        completeAssetId: QualifiedIDEntity?,
        userAvailabilityStatus: UserAvailabilityStatusEntity?,
        userType: UserTypeEntity?,
        botService: BotEntity?,
        deleted: Boolean?,
        messageId: String?,
        conversationId_: QualifiedIDEntity?,
        textBody: String?,
        messageId_: String?,
        conversationId__: QualifiedIDEntity?,
        assetSize: Long?,
        assetName: String?,
        assetMimeType: String?,
        assetDownloadStatus: MessageEntity.DownloadStatus?,
        assetOtrKey: ByteArray?,
        assetSha256: ByteArray?,
        assetId: String?,
        assetToken: String?,
        assetDomain: String?,
        assetEncryptionAlgorithm: String?,
        assetWidth: Int?,
        assetHeight: Int?,
        assetDurationMs: Long?,
        assetNormalizedLoudness: ByteArray?,
        messageId__: String?,
        conversationId___: QualifiedIDEntity?,
        callerId: QualifiedIDEntity?,
        messageId___: String?,
        conversationId____: QualifiedIDEntity?,
        memberChangeList: List<QualifiedIDEntity>?,
        memberChangeType: MessageEntity.MemberChangeType?,
        messageId____: String?,
        conversationId_____: QualifiedIDEntity?,
        unknownTypeName: String?,
        unknownEncodedData: ByteArray?,
        messageId_____: String?,
        conversationId______: QualifiedIDEntity?,
        assetMimeType_: String?,
        assetSize_: Long?,
        assetName_: String?,
        message_id______: String?,
        conversation_id_______: QualifiedIDEntity?,
        unknown_encoded_data_: ByteArray?,
    ) = when (contentType) {
            MessageEntity.ContentType.TEXT -> MessageEntityContent.Text(
                messageBody = textBody ?: "",
                mentions = listOf()
            )

            MessageEntity.ContentType.ASSET -> MessageEntityContent.Asset(
                assetSizeInBytes = assetSize.requireField("asset_size"),
                assetName = assetName,
                assetMimeType = assetMimeType.requireField("asset_mime_type"),
                assetDownloadStatus = assetDownloadStatus,
                assetOtrKey = assetOtrKey.requireField("asset_otr_key"),
                assetSha256Key = assetSha256.requireField("asset_sha256"),
                assetId = assetId.requireField("asset_id"),
                assetToken = assetToken,
                assetDomain = assetDomain,
                assetEncryptionAlgorithm = assetEncryptionAlgorithm,
                assetWidth = assetWidth,
                assetHeight = assetHeight,
                assetDurationMs = assetDurationMs,
                assetNormalizedLoudness = assetNormalizedLoudness,
            )

            MessageEntity.ContentType.KNOCK -> MessageEntityContent.Knock(false)
            MessageEntity.ContentType.MEMBER_CHANGE -> MessageEntityContent.MemberChange(
                memberUserIdList = memberChangeList.requireField("memberChangeList"),
                memberChangeType = memberChangeType.requireField("memberChangeType")
            )

            MessageEntity.ContentType.MISSED_CALL -> MessageEntityContent.MissedCall
            MessageEntity.ContentType.UNKNOWN -> MessageEntityContent.Unknown(
                typeName = unknownTypeName,
                encodedData = unknownEncodedData
            )

            MessageEntity.ContentType.FAILED_DECRYPTION -> MessageEntityContent.FailedDecryption(
                failedToDecryptData
            )

            MessageEntity.ContentType.RESTRICTED_ASSET -> MessageEntityContent.RestrictedAsset(
                assetMimeType.requireField("assetMimeType"),
                assetSize.requireField("assetSize"),
                assetName.requireField("assetName")
            )
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

    private inline fun <reified T> T?.requireField(fieldName: String): T = requireNotNull(this) {
        "Fild $fieldName null when unpacking message content"
    }
}
