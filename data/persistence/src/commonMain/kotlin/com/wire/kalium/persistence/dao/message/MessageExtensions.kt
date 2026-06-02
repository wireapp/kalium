/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.persistence.dao.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import app.cash.sqldelight.paging3.QueryPagingSource
import com.wire.kalium.persistence.MessageAttachmentsQueries
import com.wire.kalium.persistence.MessageAssetViewQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentMapper
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.datetime.Instant

interface MessageExtensions {
    fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessageAssetsWithoutImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity>

    fun getPagerForMessageAssetImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<AssetMessageEntity>
}

internal class MessageExtensionsImpl internal constructor(
    private val messagesQueries: MessagesQueries,
    private val messageAttachmentsQueries: MessageAttachmentsQueries,
    private val messageAssetViewQueries: MessageAssetViewQueries,
    private val messageMapper: MessageMapper,
    private val readDispatcher: ReadDispatcher,
) : MessageExtensions {

    override fun getPagerForConversation(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getPagingSource(conversationId, visibilities, startingOffset) },
            getPagingSource(conversationId, visibilities, startingOffset),
            readDispatcher,
        )
    }

    override fun getPagerForMessagesSearch(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getMessagesSearchPagingSource(searchQuery, conversationId, startingOffset) },
            getMessagesSearchPagingSource(searchQuery, conversationId, startingOffset),
            readDispatcher,
        )
    }

    override fun getPagerForMessageAssetsWithoutImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<MessageEntity> {
        // We could return a Flow directly, but having the PagingSource is the only way to test this
        return KaliumPager(
            Pager(pagingConfig) { getMessageAssetsWithoutImagePagingSource(conversationId, mimeTypes, startingOffset) },
            getMessageAssetsWithoutImagePagingSource(conversationId, mimeTypes, startingOffset),
            readDispatcher,
        )
    }

    override fun getPagerForMessageAssetImage(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<AssetMessageEntity> {
        return KaliumPager(
            Pager(pagingConfig) { getMessageImageAssetsPagingSource(conversationId, mimeTypes, startingOffset) },
            getMessageImageAssetsPagingSource(conversationId, mimeTypes, startingOffset),
            readDispatcher,
        )
    }

    private fun getPagingSource(
        conversationId: ConversationIDEntity,
        visibilities: Collection<MessageEntity.Visibility>,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messagesQueries.countByConversationIdAndVisibility(conversationId, visibilities),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            kaliumLogger.d("[QueryPagingSource] Loading [MessageEntity] data: offset = $offset limit = $limit")
            messagesQueries.selectByConversationIdAndVisibility(
                conversationId,
                visibilities,
                limit,
                offset,
                ::toEntityMessageFromViewWithMultipartAttachments
            )
        }
    )

    private fun getMessagesSearchPagingSource(
        searchQuery: String,
        conversationId: ConversationIDEntity,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messagesQueries.countBySearchedMessageAndConversationId(searchQuery, conversationId),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messagesQueries.selectConversationMessagesFromSearch(
                searchQuery,
                conversationId,
                limit,
                offset,
                ::toEntityMessageFromViewWithMultipartAttachments
            )
        }
    )

    private fun getMessageAssetsWithoutImagePagingSource(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messageAssetViewQueries.countAssetMessagesByConversationIdAndMimeTypes(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            listOf(MessageEntity.ContentType.ASSET),
            mimeTypes
        ),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messageAssetViewQueries.getAssetMessagesByConversationIdAndMimeTypes(
                conversationId,
                listOf(MessageEntity.Visibility.VISIBLE),
                listOf(MessageEntity.ContentType.ASSET),
                mimeTypes,
                limit,
                offset,
                ::toEntityMessageFromViewWithMultipartAttachments
            )
        }
    )

    @Suppress("ReturnCount")
    private fun withMultipartAttachments(message: MessageEntity): MessageEntity {
        val regularMessage = message as? MessageEntity.Regular ?: return message
        val multipartContent = regularMessage.content as? MessageEntityContent.Multipart ?: return message
        val attachments = messageAttachmentsQueries.getAttachments(
            regularMessage.id,
            regularMessage.conversationId,
            MessageAttachmentMapper::toDao
        ).executeAsList()
        return regularMessage.copy(content = multipartContent.copy(attachments = attachments))
    }

    @Suppress("LongParameterList", "LongMethod")
    private fun toEntityMessageFromViewWithMultipartAttachments(
        id: String,
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType,
        date: Instant,
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        status: MessageEntity.Status,
        lastEditTimestamp: Instant?,
        visibility: MessageEntity.Visibility,
        expectsReadConfirmation: Boolean,
        expireAfterMillis: Long?,
        selfDeletionEndDate: Instant?,
        readCount: Long,
        senderName: String?,
        senderHandle: String?,
        senderEmail: String?,
        senderPhone: String?,
        senderAccentId: Int,
        senderTeamId: String?,
        senderConnectionStatus: ConnectionEntity.State,
        senderPreviewAssetId: QualifiedIDEntity?,
        senderCompleteAssetId: QualifiedIDEntity?,
        senderAvailabilityStatus: UserAvailabilityStatusEntity,
        senderUserType: UserTypeEntity,
        senderBotService: BotIdEntity?,
        senderIsDeleted: Boolean,
        senderExpiresAt: Instant?,
        senderDefederated: Boolean,
        senderSupportedProtocols: Set<SupportedProtocolEntity>?,
        senderActiveOneOnOneConversationId: QualifiedIDEntity?,
        senderIsProteusVerified: Long,
        senderIsUnderLegalHold: Long,
        isSelfMessage: Boolean,
        text: String?,
        isQuotingSelfUser: Boolean?,
        assetSize: Long?,
        assetName: String?,
        assetMimeType: String?,
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
        memberChangeList: String?,
        memberChangeType: String?,
        unknownContentTypeName: String?,
        unknownContentData: ByteArray?,
        restrictedAssetMimeType: String?,
        restrictedAssetSize: Long?,
        restrictedAssetName: String?,
        failedToDecryptData: ByteArray?,
        decryptionErrorCode: Long?,
        isDecryptionResolved: Boolean?,
        conversationName: String?,
        reactionsJson: String,
        mentions: String,
        quotedMessageId: String?,
        quotedSenderId: QualifiedIDEntity?,
        isQuoteVerified: Boolean?,
        quotedSenderName: String?,
        quotedSenderAccentId: Int?,
        quotedMessageDateTime: Instant?,
        quotedMessageEditTimestamp: Instant?,
        quotedMessageVisibility: MessageEntity.Visibility?,
        quotedMessageContentType: MessageEntity.ContentType?,
        quotedTextBody: String?,
        quotedAssetMimeType: String?,
        quotedAssetName: String?,
        quotedLocationName: String?,
        isConversationAppsEnabled: Boolean?,
        newConversationReceiptMode: Boolean?,
        conversationReceiptModeChanged: Boolean?,
        messageTimerChanged: Long?,
        recipientsFailedWithNoClientsList: List<QualifiedIDEntity>?,
        recipientsFailedDeliveryList: List<QualifiedIDEntity>?,
        buttonsJson: String,
        federationDomainList: String?,
        federationType: String?,
        conversationProtocolChanged: String?,
        latitude: Float?,
        longitude: Float?,
        locationName: String?,
        locationZoom: Int?,
        legalHoldMemberList: String?,
        legalHoldType: String?,
    ): MessageEntity = withMultipartAttachments(
        messageMapper.toEntityMessageFromView(
            id = id,
            conversationId = conversationId,
            contentType = contentType,
            date = date,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            status = status,
            lastEditTimestamp = lastEditTimestamp,
            visibility = visibility,
            expectsReadConfirmation = expectsReadConfirmation,
            expireAfterMillis = expireAfterMillis,
            selfDeletionEndDate = selfDeletionEndDate,
            readCount = readCount,
            senderName = senderName,
            senderHandle = senderHandle,
            senderEmail = senderEmail,
            senderPhone = senderPhone,
            senderAccentId = senderAccentId,
            senderTeamId = senderTeamId,
            senderConnectionStatus = senderConnectionStatus,
            senderPreviewAssetId = senderPreviewAssetId,
            senderCompleteAssetId = senderCompleteAssetId,
            senderAvailabilityStatus = senderAvailabilityStatus,
            senderUserType = senderUserType,
            senderBotService = senderBotService,
            senderIsDeleted = senderIsDeleted,
            senderExpiresAt = senderExpiresAt,
            senderDefederated = senderDefederated,
            senderSupportedProtocols = senderSupportedProtocols,
            senderActiveOneOnOneConversationId = senderActiveOneOnOneConversationId,
            senderIsProteusVerified = senderIsProteusVerified,
            senderIsUnderLegalHold = senderIsUnderLegalHold,
            isSelfMessage = isSelfMessage,
            text = text,
            isQuotingSelfUser = isQuotingSelfUser,
            assetSize = assetSize,
            assetName = assetName,
            assetMimeType = assetMimeType,
            assetOtrKey = assetOtrKey,
            assetSha256 = assetSha256,
            assetId = assetId,
            assetToken = assetToken,
            assetDomain = assetDomain,
            assetEncryptionAlgorithm = assetEncryptionAlgorithm,
            assetWidth = assetWidth,
            assetHeight = assetHeight,
            assetDuration = assetDuration,
            assetNormalizedLoudness = assetNormalizedLoudness,
            callerId = callerId,
            memberChangeList = memberChangeList,
            memberChangeType = memberChangeType,
            unknownContentTypeName = unknownContentTypeName,
            unknownContentData = unknownContentData,
            restrictedAssetMimeType = restrictedAssetMimeType,
            restrictedAssetSize = restrictedAssetSize,
            restrictedAssetName = restrictedAssetName,
            failedToDecryptData = failedToDecryptData,
            decryptionErrorCode = decryptionErrorCode,
            isDecryptionResolved = isDecryptionResolved,
            conversationName = conversationName,
            reactionsJson = reactionsJson,
            mentions = mentions,
            quotedMessageId = quotedMessageId,
            quotedSenderId = quotedSenderId,
            isQuoteVerified = isQuoteVerified,
            quotedSenderName = quotedSenderName,
            quotedSenderAccentId = quotedSenderAccentId,
            quotedMessageDateTime = quotedMessageDateTime,
            quotedMessageEditTimestamp = quotedMessageEditTimestamp,
            quotedMessageVisibility = quotedMessageVisibility,
            quotedMessageContentType = quotedMessageContentType,
            quotedTextBody = quotedTextBody,
            quotedAssetMimeType = quotedAssetMimeType,
            quotedAssetName = quotedAssetName,
            quotedLocationName = quotedLocationName,
            isConversationAppsEnabled = isConversationAppsEnabled,
            newConversationReceiptMode = newConversationReceiptMode,
            conversationReceiptModeChanged = conversationReceiptModeChanged,
            messageTimerChanged = messageTimerChanged,
            recipientsFailedWithNoClientsList = recipientsFailedWithNoClientsList,
            recipientsFailedDeliveryList = recipientsFailedDeliveryList,
            buttonsJson = buttonsJson,
            federationDomainList = federationDomainList,
            federationType = federationType,
            conversationProtocolChanged = conversationProtocolChanged,
            latitude = latitude,
            longitude = longitude,
            locationName = locationName,
            locationZoom = locationZoom,
            legalHoldMemberList = legalHoldMemberList,
            legalHoldType = legalHoldType,
        )
    )

    private fun getMessageImageAssetsPagingSource(
        conversationId: ConversationIDEntity,
        mimeTypes: Set<String>,
        initialOffset: Long
    ) = AsyncQueryPagingSource(
        countQuery = messageAssetViewQueries.countImageAssetMessagesByConversationIdAndMimeTypes(
            conversationId,
            listOf(MessageEntity.Visibility.VISIBLE),
            listOf(MessageEntity.ContentType.ASSET),
            mimeTypes
        ),
        context = readDispatcher.value,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            messageAssetViewQueries.getImageAssetMessagesByConversationIdAndMimeTypes(
                conversationId,
                listOf(MessageEntity.Visibility.VISIBLE),
                listOf(MessageEntity.ContentType.ASSET),
                mimeTypes,
                limit,
                offset,
                messageMapper::toEntityAssetMessageFromView
            )
        }
    )
}
