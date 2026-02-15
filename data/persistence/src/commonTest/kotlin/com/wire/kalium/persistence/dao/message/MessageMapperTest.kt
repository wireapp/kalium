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

import com.wire.kalium.persistence.adapter.QualifiedIDListAdapter
import com.wire.kalium.persistence.adapter.StringListAdapter
import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertContentEquals
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

    @Test
    fun givenMessageDetailsViewWithLegalHoldMemberMessage_whenMappingToEntityMessage_thenMessageEntityHasExpectedData() {
        // given
        val membersList = listOf(QualifiedIDEntity("someValue", "someDomain"))
        val type = MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS
        // when
        val messageEntity = Arrangement().toEntityFromView(
            contentType = MessageEntity.ContentType.LEGAL_HOLD,
            legalHoldMemberList = membersList,
            legalHoldType = type,
        )
        // then
        val content = messageEntity.content
        assertIs<MessageEntityContent.LegalHold>(content)
        assertContentEquals(content.memberUserIdList, membersList)
        assertEquals(content.type, type)
    }

    @Test
    fun givenThreadTextRowWithMentionsAndQuote_whenMappingToThreadEntity_thenQuotedMetadataIsPreserved() {
        val message = Arrangement().toThreadEntityFromView(
            contentType = MessageEntity.ContentType.TEXT,
            text = "hello @wire",
            mentions = """[{"start":6,"length":5,"userId":{"value":"mention","domain":"domain"}}]""",
            quotedMessageId = "quoted-id",
            quotedSenderId = QualifiedIDEntity("quoted", "domain"),
            isQuoteVerified = true,
            isQuotingSelfUser = false,
            quotedSenderName = "Quoted Sender",
            quotedSenderAccentId = 7,
            quotedMessageDateTime = Instant.parse("2026-01-01T00:00:00Z"),
            quotedMessageEditTimestamp = Instant.parse("2026-01-01T00:00:01Z"),
            quotedMessageVisibility = MessageEntity.Visibility.VISIBLE,
            quotedMessageContentType = MessageEntity.ContentType.ASSET,
            quotedTextBody = "quoted-text",
            quotedAssetMimeType = "image/png",
            quotedAssetName = "quoted.png",
            quotedLocationName = "Zurich",
        )

        val content = message.message.content
        assertIs<MessageEntityContent.Text>(content)
        assertEquals("hello @wire", content.messageBody)
        assertEquals(1, content.mentions.size)
        assertEquals("mention", content.mentions.first().userId.value)
        assertEquals("quoted-id", content.quotedMessageId)
        assertEquals("quoted-id", content.quotedMessage?.id)
        assertEquals("quoted.png", content.quotedMessage?.assetName)
        assertEquals("Zurich", content.quotedMessage?.locationName)
    }

    @Test
    fun givenThreadAssetRow_whenMappingToThreadEntity_thenRemoteAndLocalDataArePreserved() {
        val message = Arrangement().toThreadEntityFromView(
            contentType = MessageEntity.ContentType.ASSET,
            assetSize = 1024,
            assetName = "photo.png",
            assetMimeType = "image/png",
            assetOtrKey = byteArrayOf(1, 2),
            assetSha256 = byteArrayOf(3, 4),
            assetId = "asset-id",
            assetToken = "token",
            assetDomain = "wire.com",
            assetEncryptionAlgorithm = "AES",
            assetWidth = 100,
            assetHeight = 200,
            assetDuration = 300,
            assetNormalizedLoudness = byteArrayOf(9),
            assetDataPath = "/tmp/path/photo.png",
        )

        val content = message.message.content
        assertIs<MessageEntityContent.Asset>(content)
        assertEquals(1024, content.assetSizeInBytes)
        assertEquals("photo.png", content.assetName)
        assertEquals("image/png", content.assetMimeType)
        assertEquals("asset-id", content.assetId)
        assertEquals("/tmp/path/photo.png", content.assetDataPath)
    }

    @Test
    fun givenThreadMultipartRowWithAttachments_whenMappingToThreadEntity_thenAttachmentsArePreserved() {
        val message = Arrangement().toThreadEntityFromView(
            contentType = MessageEntity.ContentType.MULTIPART,
            text = "multipart",
            attachments =
                """[{"id":"asset-1","version_id":"v1","cell_asset":1,"mime_type":"image/png","asset_path":"remote/path","asset_size":1000,"local_path":"/tmp/local.png","asset_width":200,"asset_height":100,"asset_transfer_status":"UPLOADED","asset_duration_ms":0,"content_hash":"hash","content_url":"url","preview_url":"preview","content_url_expires_at":1234,"edit_supported":0}]"""
        )

        val content = message.message.content
        assertIs<MessageEntityContent.Multipart>(content)
        assertEquals("multipart", content.messageBody)
        assertEquals(1, content.attachments.size)
        assertEquals("asset-1", content.attachments.first().assetId)
        assertEquals("/tmp/local.png", content.attachments.first().localPath)
    }

    @Test
    fun givenThreadCompositeRowWithButtons_whenMappingToThreadEntity_thenButtonsArePreserved() {
        val message = Arrangement().toThreadEntityFromView(
            contentType = MessageEntity.ContentType.COMPOSITE,
            text = "poll",
            buttonsJson = """[{"text":"Accept","id":"btn-1","is_selected":1}]"""
        )

        val content = message.message.content
        assertIs<MessageEntityContent.Composite>(content)
        assertEquals("poll", content.text?.messageBody)
        assertEquals(1, content.buttonList.size)
        assertEquals("Accept", content.buttonList.first().text)
        assertEquals("btn-1", content.buttonList.first().id)
        assertTrue(content.buttonList.first().isSelected)
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
            selfDeletionEndDate: Instant? = null,
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
            senderExpiresAt: Instant? = null,
            senderDefederated: Boolean = false,
            senderSupportedProtocols: Set<SupportedProtocolEntity>? = null,
            senderActiveOneOnOneConversationId: QualifiedIDEntity? = null,
            senderIsProteusVerified: Long = 0,
            senderIsUnderLegalHold: Long = 0,
            isSelfMessage: Boolean = false,
            text: String? = null,
            isQuotingSelfUser: Boolean? = null,
            assetSize: Long? = null,
            assetName: String? = null,
            assetMimeType: String? = null,
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
            assetDataPath: String? = null,
            callerId: QualifiedIDEntity? = null,
            memberChangeList: List<QualifiedIDEntity>? = null,
            memberChangeType: MessageEntity.MemberChangeType? = null,
            unknownContentTypeName: String? = null,
            unknownContentData: ByteArray? = null,
            restrictedAssetMimeType: String? = null,
            restrictedAssetSize: Long? = null,
            restrictedAssetName: String? = null,
            failedToDecryptData: ByteArray? = null,
            decryptionErrorCode: Long? = null,
            isDecryptionResolved: Boolean? = null,
            conversationName: String? = null,
            reactionsJson: String = "[]",
            mentions: String = "[]",
            quotedMessageId: String? = null,
            quotedSenderId: QualifiedIDEntity? = null,
            isQuoteVerified: Boolean? = null,
            quotedSenderName: String? = null,
            quotedSenderAccentId: Int? = null,
            quotedMessageDateTime: Instant? = null,
            quotedMessageEditTimestamp: Instant? = null,
            quotedMessageVisibility: MessageEntity.Visibility? = null,
            quotedMessageContentType: MessageEntity.ContentType? = null,
            quotedTextBody: String? = null,
            quotedAssetMimeType: String? = null,
            quotedAssetName: String? = null,
            quotedLocationName: String? = null,
            isConversationAppsEnabled: Boolean? = null,
            newConversationReceiptMode: Boolean? = null,
            conversationReceiptModeChanged: Boolean? = null,
            messageTimerChanged: Long? = null,
            recipientsFailedWithNoClientsList: List<QualifiedIDEntity>? = null,
            recipientsFailedDeliveryList: List<QualifiedIDEntity>? = null,
            buttonsJson: String = "[]",
            federationDomainList: List<String>? = null,
            federationType: MessageEntity.FederationType? = null,
            conversationProtocolChanged: ConversationEntity.Protocol? = null,
            latitude: Float? = null,
            longitude: Float? = null,
            locationName: String? = null,
            locationZoom: Int? = null,
            legalHoldMemberList: List<QualifiedIDEntity>? = null,
            legalHoldType: MessageEntity.LegalHoldType? = null,
            attachments: String? = null,
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
                selfDeletionEndDate,
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
                senderExpiresAt,
                senderDefederated,
                senderSupportedProtocols,
                senderActiveOneOnOneConversationId,
                senderIsProteusVerified,
                senderIsUnderLegalHold,
                isSelfMessage,
                text,
                isQuotingSelfUser,
                assetSize,
                assetName,
                assetMimeType,
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
                assetDataPath,
                callerId,
                memberChangeList?.let { QualifiedIDListAdapter.encode(it) },
                memberChangeType?.name,
                unknownContentTypeName,
                unknownContentData,
                restrictedAssetMimeType,
                restrictedAssetSize,
                restrictedAssetName,
                failedToDecryptData,
                decryptionErrorCode,
                isDecryptionResolved,
                conversationName,
                reactionsJson,
                mentions,
                attachments,
                quotedMessageId,
                quotedSenderId,
                isQuoteVerified,
                quotedSenderName,
                quotedSenderAccentId,
                quotedMessageDateTime,
                quotedMessageEditTimestamp,
                quotedMessageVisibility,
                quotedMessageContentType,
                quotedTextBody,
                quotedAssetMimeType,
                quotedAssetName,
                quotedLocationName,
                isConversationAppsEnabled,
                newConversationReceiptMode,
                conversationReceiptModeChanged,
                messageTimerChanged,
                recipientsFailedWithNoClientsList,
                recipientsFailedDeliveryList,
                buttonsJson,
                federationDomainList?.let { StringListAdapter.encode(it) },
                federationType?.name,
                conversationProtocolChanged?.name,
                latitude,
                longitude,
                locationName,
                locationZoom,
                legalHoldMemberList?.let { QualifiedIDListAdapter.encode(it) },
                legalHoldType?.name,
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
                memberChangeList = null,
                memberChangeType = null,
                updateConversationName = null,
                conversationName = null,
                isMentioningSelfUser = false,
                isQuotingSelfUser = null,
                text = null,
                assetMimeType = null,
                isUnread = false,
                shouldNotify = 0,
                mutedStatus = null,
                conversationType = conversationType
            )
        }

        @Suppress("LongParameterList")
        fun toThreadEntityFromView(
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
            selfDeletionEndDate: Instant? = null,
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
            senderExpiresAt: Instant? = null,
            senderDefederated: Boolean = false,
            senderSupportedProtocols: Set<SupportedProtocolEntity>? = null,
            senderActiveOneOnOneConversationId: QualifiedIDEntity? = null,
            senderIsProteusVerified: Long = 0,
            senderIsUnderLegalHold: Long = 0,
            isSelfMessage: Boolean = false,
            text: String? = null,
            isQuotingSelfUser: Boolean? = null,
            assetSize: Long? = null,
            assetName: String? = null,
            assetMimeType: String? = null,
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
            assetDataPath: String? = null,
            reactionsJson: String = "[]",
            mentions: String = "[]",
            attachments: String = "[]",
            quotedMessageId: String? = null,
            quotedSenderId: QualifiedIDEntity? = null,
            isQuoteVerified: Boolean? = null,
            quotedSenderName: String? = null,
            quotedSenderAccentId: Int? = null,
            quotedMessageDateTime: Instant? = null,
            quotedMessageEditTimestamp: Instant? = null,
            quotedMessageVisibility: MessageEntity.Visibility? = null,
            quotedMessageContentType: MessageEntity.ContentType? = null,
            quotedTextBody: String? = null,
            quotedAssetMimeType: String? = null,
            quotedAssetName: String? = null,
            quotedLocationName: String? = null,
            recipientsFailedWithNoClientsList: List<QualifiedIDEntity>? = null,
            recipientsFailedDeliveryList: List<QualifiedIDEntity>? = null,
            buttonsJson: String = "[]",
        ): ThreadMessageEntity {
            return MessageMapper.toThreadMessageEntityFromView(
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
                assetDataPath = assetDataPath,
                reactionsJson = reactionsJson,
                mentions = mentions,
                attachments = attachments,
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
                recipientsFailedWithNoClientsList = recipientsFailedWithNoClientsList,
                recipientsFailedDeliveryList = recipientsFailedDeliveryList,
                buttonsJson = buttonsJson,
            )
        }
    }
}
