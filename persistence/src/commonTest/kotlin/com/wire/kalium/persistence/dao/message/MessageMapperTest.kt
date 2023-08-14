/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MessageMapperTest {

    @Test
    fun givenEphemeralOneOnOneConversation_whenMappingToMessagePreviewEntity_thenMessagePreviewEntityContentIsEphemeral() {
        // given / when
        val messagePreviewEntity = Arrangement().toPreviewEntity(ConversationEntity.Type.GROUP, true)

        // then
        val content = messagePreviewEntity.content
        assertIs<MessagePreviewEntityContent.Ephemeral>(content)
        assertTrue(content.isGroupConversation)
    }

    @Test
    fun givenEphemeralGroupConversation_whenMappingToMessagePreviewEntity_thenMessagePreviewEntityContentIsEphemeral() {
        // given / when
        val messagePreviewEntity = Arrangement().toPreviewEntity(ConversationEntity.Type.ONE_ON_ONE, true)

        // then
        val content = messagePreviewEntity.content
        assertIs<MessagePreviewEntityContent.Ephemeral>(content)
        assertTrue(!content.isGroupConversation)
    }

    @Test
    fun givenMessageDetailsViewWithDeliveredStatusAndText_whenMappingToEntityMessage_thenMessageEntityHasExpectedData() {
        // given / when
        val messageEntity = Arrangement().toEntityFromView(
            text = "Test  text",
            status = MessageEntity.Status.DELIVERED
        )

        // then
        assertEquals(messageEntity.status, MessageEntity.Status.DELIVERED)
    }

    @Test
    fun givenMessageDetailsViewWithReadStatusAndText_whenMappingToEntityMessage_thenMessageEntityHasExpectedData() {
        // given / when
        val messageEntity = Arrangement().toEntityFromView(
            text = "Test  text",
            status = MessageEntity.Status.READ,
            readCount = 10
        )

        // then
        assertEquals(messageEntity.status, MessageEntity.Status.READ)
        assertEquals(messageEntity.readCount, 10)
    }

    private class Arrangement {
        @Suppress("LongParameterList")
        fun toEntityFromView(
            id: String = "",
            conversationId: QualifiedIDEntity = QualifiedIDEntity("someValue", "someDomain"),
            contentType: MessageEntity.ContentType = MessageEntity.ContentType.TEXT,
            date: Instant = Instant.DISTANT_FUTURE,
            senderUserId: QualifiedIDEntity = QualifiedIDEntity("someValue", "someDomain"),
            senderClientId: String? = "someId",
            status: MessageEntity.Status = MessageEntity.Status.READ,
            lastEditTimestamp: Instant? = null,
            visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
            expectsReadConfirmation: Boolean = false,
            expireAfterMillis: Long? = null,
            selfDeletionStartDate: Instant? = null,
            readCount: Long = 0,
            senderName: String? = null,
            senderHandle: String? = null,
            senderEmail: String? = null,
            senderPhone: String? = null,
            senderAccentId: Int = 0,
            senderTeamId: String? = null,
            senderConnectionStatus: ConnectionEntity.State = ConnectionEntity.State.ACCEPTED,
            senderPreviewAssetId: QualifiedIDEntity? = null,
            senderCompleteAssetId: QualifiedIDEntity? = null,
            senderAvailabilityStatus: UserAvailabilityStatusEntity = UserAvailabilityStatusEntity.AVAILABLE,
            senderUserType: UserTypeEntity = UserTypeEntity.STANDARD,
            senderBotService: BotIdEntity? = null,
            senderIsDeleted: Boolean = false,
            isSelfMessage: Boolean = false,
            text: String? = null,
            isQuotingSelfUser: Boolean? = null,
            assetSize: Long? = null,
            assetName: String? = null,
            assetMimeType: String? = null,
            assetUploadStatus: MessageEntity.UploadStatus? = MessageEntity.UploadStatus.UPLOADED,
            assetDownloadStatus: MessageEntity.DownloadStatus? = MessageEntity.DownloadStatus.IN_PROGRESS,
            assetOtrKey: ByteArray? = null,
            assetSha256: ByteArray? = null,
            assetId: String? = null,
            assetToken: String? = null,
            assetDomain: String? = null,
            assetEncryptionAlgorithm: String? = null,
            assetWidth: Int? = null,
            assetHeight: Int? = null,
            assetDuration: Long? = null,
            assetNormalizedLoudness: ByteArray? = null,
            callerId: QualifiedIDEntity? = null,
            memberChangeList: List<QualifiedIDEntity>? = null,
            memberChangeType: MessageEntity.MemberChangeType? = null,
            unknownContentTypeName: String? = null,
            unknownContentData: ByteArray? = null,
            restrictedAssetMimeType: String? = null,
            restrictedAssetSize: Long? = null,
            restrictedAssetName: String? = null,
            failedToDecryptData: ByteArray? = null,
            isDecryptionResolved: Boolean? = null,
            conversationName: String? = null,
            allReactionsJson: String = "{}",
            selfReactionsJson: String = "[]",
            mentions: String = "[]",
            quotedMessageId: String? = null,
            quotedSenderId: QualifiedIDEntity? = null,
            isQuoteVerified: Boolean? = null,
            quotedSenderName: String? = null,
            quotedMessageDateTime: Instant? = null,
            quotedMessageEditTimestamp: Instant? = null,
            quotedMessageVisibility: MessageEntity.Visibility? = null,
            quotedMessageContentType: MessageEntity.ContentType? = null,
            quotedTextBody: String? = null,
            quotedAssetMimeType: String? = null,
            quotedAssetName: String? = null,
            newConversationReceiptMode: Boolean? = null,
            conversationReceiptModeChanged: Boolean? = null,
            messageTimerChanged: Long? = null,
            recipientsFailedWithNoClientsList: List<QualifiedIDEntity>? = null,
            recipientsFailedDeliveryList: List<QualifiedIDEntity>? = null,
            buttonsJson: String = "[]"
        ): MessageEntity {
            return MessageMapper.toEntityMessageFromView(
                id,
                conversationId,
                contentType,
                date,
                senderUserId,
                senderClientId,
                status,
                lastEditTimestamp,
                visibility,
                expectsReadConfirmation,
                expireAfterMillis,
                selfDeletionStartDate,
                readCount,
                senderName,
                senderHandle,
                senderEmail,
                senderPhone,
                senderAccentId,
                senderTeamId,
                senderConnectionStatus,
                senderPreviewAssetId,
                senderCompleteAssetId,
                senderAvailabilityStatus,
                senderUserType,
                senderBotService,
                senderIsDeleted,
                isSelfMessage,
                text,
                isQuotingSelfUser,
                assetSize,
                assetName,
                assetMimeType,
                assetUploadStatus,
                assetDownloadStatus,
                assetOtrKey,
                assetSha256,
                assetId,
                assetToken,
                assetDomain,
                assetEncryptionAlgorithm,
                assetWidth,
                assetHeight,
                assetDuration,
                assetNormalizedLoudness,
                callerId,
                memberChangeList,
                memberChangeType,
                unknownContentTypeName,
                unknownContentData,
                restrictedAssetMimeType,
                restrictedAssetSize,
                restrictedAssetName,
                failedToDecryptData,
                isDecryptionResolved,
                conversationName,
                allReactionsJson,
                selfReactionsJson,
                mentions,
                quotedMessageId,
                quotedSenderId,
                isQuoteVerified,
                quotedSenderName,
                quotedMessageDateTime,
                quotedMessageEditTimestamp,
                quotedMessageVisibility,
                quotedMessageContentType,
                quotedTextBody,
                quotedAssetMimeType,
                quotedAssetName,
                newConversationReceiptMode,
                conversationReceiptModeChanged,
                messageTimerChanged,
                recipientsFailedWithNoClientsList,
                recipientsFailedDeliveryList,
                buttonsJson
            )
        }

        fun toPreviewEntity(conversationType: ConversationEntity.Type, isEphemeral: Boolean): MessagePreviewEntity {
            return MessageMapper.toPreviewEntity(
                id = "someId",
                conversationId = ConversationIDEntity("someValue", "someDomain"),
                contentType = MessageEntity.ContentType.TEXT,
                date = Instant.DISTANT_FUTURE,
                visibility = MessageEntity.Visibility.VISIBLE,
                senderUserId = QualifiedIDEntity("someValue", "someDomain"),
                isEphemeral = isEphemeral,
                senderName = "someName",
                senderConnectionStatus = null,
                senderIsDeleted = null,
                selfUserId = null,
                isSelfMessage = false,
                memberChangeList = listOf(),
                memberChangeType = null,
                updatedConversationName = null,
                conversationName = null,
                isMentioningSelfUser = false,
                isQuotingSelfUser = null,
                text = null,
                assetMimeType = null,
                isUnread = false,
                isNotified = 0,
                mutedStatus = null,
                conversationType = conversationType
            )
        }
    }
}
