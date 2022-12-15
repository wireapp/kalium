package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.BotEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.reaction.ReactionMapper
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import com.wire.kalium.persistence.util.JsonSerializer
import kotlinx.serialization.decodeFromString

@Suppress("LongParameterList")
object MessageMapper {

    private val serializer = JsonSerializer()

    @Suppress("ComplexMethod")
    private fun toMessagePreviewEntityContent(
        contentType: MessageEntity.ContentType,
        senderName: String?,
        selfUserId: QualifiedIDEntity?,
        isSelfMessage: Boolean,
        memberChangeList: List<QualifiedIDEntity>?,
        memberChangeType: MessageEntity.MemberChangeType?,
        isMentioningSelfUser: Boolean,
        isQuotingSelfUser: Boolean?,
        text: String?,
        assetMimeType: String?
    ) = when (contentType) {
        MessageEntity.ContentType.TEXT -> when {
            isSelfMessage -> MessagePreviewEntityContent.Text(
                senderName = senderName,
                messageBody = text.requireField("text")
            )
            (isQuotingSelfUser ?: false) -> MessagePreviewEntityContent.QuotedSelf(
                senderName = senderName,
                messageBody = text.requireField("text")
            )
            (isMentioningSelfUser) -> MessagePreviewEntityContent.MentionedSelf(
                senderName = senderName, messageBody = text.requireField("text")
            )
            else -> MessagePreviewEntityContent.Text(
                senderName = senderName,
                messageBody = text.requireField("text")
            )
        }
        MessageEntity.ContentType.ASSET -> MessagePreviewEntityContent.Asset(
            senderName = senderName,
            type = assetMimeType?.let {
                when {
                    it.contains("image/") -> AssetTypeEntity.IMAGE
                    it.contains("video/") -> AssetTypeEntity.VIDEO
                    it.contains("audio/") -> AssetTypeEntity.AUDIO
                    else -> AssetTypeEntity.FILE
                }
            } ?: AssetTypeEntity.FILE)

        MessageEntity.ContentType.KNOCK -> MessagePreviewEntityContent.Knock(senderName = senderName)
        MessageEntity.ContentType.MEMBER_CHANGE -> MessagePreviewEntityContent.MemberChange(
            adminName = senderName,
            count = memberChangeList.requireField("memberChangeList").size,
            type = memberChangeType.requireField("memberChangeType")
        )

        MessageEntity.ContentType.MISSED_CALL -> MessagePreviewEntityContent.MissedCall(senderName = senderName)
        MessageEntity.ContentType.RESTRICTED_ASSET -> MessagePreviewEntityContent.Asset(
            senderName = senderName,
            type = AssetTypeEntity.ASSET
        )
        MessageEntity.ContentType.CONVERSATION_RENAMED -> MessagePreviewEntityContent.ConversationNameChange(
            adminName = senderName
        )

        MessageEntity.ContentType.UNKNOWN -> MessagePreviewEntityContent.Unknown
        MessageEntity.ContentType.FAILED_DECRYPTION -> MessagePreviewEntityContent.Unknown
        MessageEntity.ContentType.REMOVED_FROM_TEAM -> MessagePreviewEntityContent.TeamMemberRemoved(userName = senderName)
        MessageEntity.ContentType.CRYPTO_SESSION_RESET -> MessagePreviewEntityContent.CryptoSessionReset
    }

    @Suppress("ComplexMethod")
    fun toPreviewEntity(
        id: String,
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType,
        date: String,
        visibility: MessageEntity.Visibility,
        senderName: String?,
        senderConnectionStatus: ConnectionEntity.State?,
        senderIsDeleted: Boolean?,
        selfUserId: QualifiedIDEntity?,
        isSelfMessage: Boolean,
        memberChangeList: List<QualifiedIDEntity>?,
        memberChangeType: MessageEntity.MemberChangeType?,
        updatedConversationName: String?,
        conversationName: String?,
        isMentioningSelfUser: Boolean,
        isQuotingSelfUser: Boolean?,
        text: String?,
        assetMimeType: String?,
        isUnread: Boolean,
        isNotified: Long,
        mutedStatus: ConversationEntity.MutedStatus?,
        conversationType: ConversationEntity.Type?
    ): MessagePreviewEntity {
        val content = toMessagePreviewEntityContent(
            contentType = contentType,
            senderName = senderName,
            selfUserId = selfUserId,
            isSelfMessage = isSelfMessage,
            memberChangeList = memberChangeList,
            memberChangeType = memberChangeType,
            isMentioningSelfUser = isMentioningSelfUser,
            isQuotingSelfUser = isQuotingSelfUser,
            text = text,
            assetMimeType = assetMimeType
        )

        return MessagePreviewEntity(
            id = id,
            conversationId = conversationId,
            content = content,
            date = date,
            visibility = visibility,
            isSelfMessage = isSelfMessage
        )

    }

    @Suppress("ComplexMethod")
    fun toNotificationEntity(
        id: String,
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType,
        date: String,
        visibility: MessageEntity.Visibility,
        senderName: String?,
        senderConnectionStatus: ConnectionEntity.State?,
        senderIsDeleted: Boolean?,
        selfUserId: QualifiedIDEntity?,
        isSelfMessage: Boolean,
        memberChangeList: List<QualifiedIDEntity>?,
        memberChangeType: MessageEntity.MemberChangeType?,
        updatedConversationName: String?,
        conversationName: String?,
        isMentioningSelfUser: Boolean,
        isQuotingSelfUser: Boolean?,
        text: String?,
        assetMimeType: String?,
        isUnread: Boolean,
        isNotified: Long,
        mutedStatus: ConversationEntity.MutedStatus?,
        conversationType: ConversationEntity.Type?
    ): NotificationMessageEntity {
        val content = toMessagePreviewEntityContent(
            contentType = contentType,
            senderName = senderName,
            selfUserId = selfUserId,
            isSelfMessage = isSelfMessage,
            memberChangeList = memberChangeList,
            memberChangeType = memberChangeType,
            isMentioningSelfUser = isMentioningSelfUser,
            isQuotingSelfUser = isQuotingSelfUser,
            text = text,
            assetMimeType = assetMimeType
        )

        return NotificationMessageEntity(
            id = id,
            content = content,
            conversationId = conversationId,
            conversationName = conversationName,
            conversationType = conversationType,
            date = date
        )

    }

    private fun createMessageEntity(
        id: String,
        conversationId: QualifiedIDEntity,
        date: String,
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        status: MessageEntity.Status,
        lastEditTimestamp: String?,
        visibility: MessageEntity.Visibility,
        content: MessageEntityContent,
        allReactionsJson: String?,
        selfReactionsJson: String?,
        senderName: String?,
        isSelfMessage: Boolean,
        expectsReadConfirmation: Boolean
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
            visibility = visibility,
            reactions = ReactionsEntity(
                totalReactions = ReactionMapper.reactionsCountFromJsonString(allReactionsJson),
                selfUserReactions = ReactionMapper.userReactionsFromJsonString(selfReactionsJson)
            ),
            senderName = senderName,
            isSelfMessage = isSelfMessage,
            expectsReadConfirmation = expectsReadConfirmation
        )

        is MessageEntityContent.System -> MessageEntity.System(
            content = content,
            id = id,
            conversationId = conversationId,
            date = date,
            senderUserId = senderUserId,
            status = status,
            visibility = visibility,
            senderName = senderName,
            isSelfMessage = isSelfMessage
        )
    }

    private fun mapEditStatus(lastEditTimestamp: String?) =
        lastEditTimestamp?.let { MessageEntity.EditStatus.Edited(it) }
            ?: MessageEntity.EditStatus.NotEdited

    @Suppress("LongMethod", "ComplexMethod")
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
        expectsReadConfirmation: Boolean?,
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
        isSelfMessage: Boolean,
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
        isDecryptionResolved: Boolean?,
        conversationName: String?,
        allReactionsJson: String,
        selfReactionsJson: String,
        mentions: String,
        quotedMessageId: String?,
        quotedSenderId: QualifiedIDEntity?,
        isQuotingSelfUser: Boolean?,
        isQuoteVerified: Boolean?,
        quotedSenderName: String?,
        quotedMessageDateTime: String?,
        quotedMessageEditTimestamp: String?,
        quotedMessageVisibility: MessageEntity.Visibility?,
        quotedMessageContentType: MessageEntity.ContentType?,
        quotedTextBody: String?,
        quotedAssetMimeType: String?,
        quotedAssetName: String?,
    ): MessageEntity {
        // If message hsa been deleted, we don't care about the content. Also most of their internal content is null anyways
        val content = if (visibility == MessageEntity.Visibility.DELETED) {
            MessageEntityContent.Unknown()
        } else when (contentType) {
            MessageEntity.ContentType.TEXT -> MessageEntityContent.Text(
                messageBody = text ?: "",
                mentions = messageMentionsFromJsonString(mentions),
                quotedMessageId = quotedMessageId,
                quotedMessage = quotedMessageId?.let {
                    MessageEntityContent.Text.QuotedMessage(
                        id = it,
                        senderId = quotedSenderId.requireField("quotedSenderId"),
                        isQuotingSelfUser = isQuotingSelfUser.requireField("isQuotingSelfUser"),
                        isVerified = isQuoteVerified ?: false,
                        senderName = quotedSenderName.requireField("quotedSenderName"),
                        dateTime = quotedMessageDateTime.requireField("quotedMessageDateTime"),
                        editTimestamp = quotedMessageEditTimestamp,
                        visibility = quotedMessageVisibility.requireField("quotedMessageVisibility"),
                        contentType = quotedMessageContentType.requireField("quotedMessageContentType"),
                        textBody = quotedTextBody,
                        assetMimeType = quotedAssetMimeType,
                        assetName = quotedAssetName,
                    )
                },
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
                encodedData = failedToDecryptData,
                isDecryptionResolved = isDecryptionResolved ?: false,
                senderUserId = senderUserId,
                senderClientId = senderClientId
            )

            MessageEntity.ContentType.RESTRICTED_ASSET -> MessageEntityContent.RestrictedAsset(
                restrictedAssetMimeType.requireField("assetMimeType"),
                restrictedAssetSize.requireField("assetSize"),
                restrictedAssetName.requireField("assetName")
            )

            MessageEntity.ContentType.CONVERSATION_RENAMED -> MessageEntityContent.ConversationRenamed(conversationName.orEmpty())
            MessageEntity.ContentType.REMOVED_FROM_TEAM -> MessageEntityContent.TeamMemberRemoved(senderName.orEmpty())
            MessageEntity.ContentType.CRYPTO_SESSION_RESET -> MessageEntityContent.CryptoSessionReset
        }

        return createMessageEntity(
            id,
            conversationId,
            date,
            senderUserId,
            senderClientId,
            status,
            lastEditTimestamp,
            visibility,
            content,
            allReactionsJson,
            selfReactionsJson,
            senderName,
            isSelfMessage,
            expectsReadConfirmation ?: false
        )
    }

    /**
     * Used when unpacking a value from the database, and it is expected to not be null.
     * For example, if there's a quoted message ID, it is 100% expected that there is a quoted message content type
     * This is basically a verbose !! (🔫🔫 Bang Bang) that provides a more meaningful exception.
     */
    private inline fun <reified T> T?.requireField(fieldName: String): T = requireNotNull(this) {
        "Field $fieldName null when unpacking message content"
    }

    private fun messageMentionsFromJsonString(messageMentions: String?): List<MessageEntity.Mention> =
        messageMentions?.let {
            serializer.decodeFromString(it)
        } ?: emptyList()
}
