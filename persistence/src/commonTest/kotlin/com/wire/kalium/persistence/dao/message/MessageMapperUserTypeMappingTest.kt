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

import com.wire.kalium.persistence.dao.BotIdEntity
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.SupportedProtocolEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * Tests for MessageMapper focusing specifically on the toUserTypeInfoEntity mapping.
 * Verifies that UserTypeEntity values are correctly mapped to UserTypeInfoEntity
 * when mapping message entities from database views.
 */
class MessageMapperUserTypeMappingTest {

    @Test
    fun givenMessageWithStandardUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.STANDARD
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.STANDARD, senderUserType)
    }

    @Test
    fun givenMessageWithAdminUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.ADMIN
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.ADMIN, senderUserType)
    }

    @Test
    fun givenMessageWithOwnerUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.OWNER
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.OWNER, senderUserType)
    }

    @Test
    fun givenMessageWithExternalUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.EXTERNAL
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.EXTERNAL, senderUserType)
    }

    @Test
    fun givenMessageWithFederatedUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.FEDERATED
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.FEDERATED, senderUserType)
    }

    @Test
    fun givenMessageWithGuestUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.GUEST
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.GUEST, senderUserType)
    }

    @Test
    fun givenMessageWithNoneUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToRegular() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.NONE
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.NONE, senderUserType)
    }

    @Test
    fun givenMessageWithServiceUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToBot() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.SERVICE
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.SERVICE, senderUserType)
        assertEquals(UserTypeEntity.SERVICE, senderUserType)
    }

    @Test
    fun givenMessageWithAppUserType_whenMappingToEntityMessage_thenSenderUserTypeIsMappedToApp() {
        // given / when
        val messageEntity = toEntityFromView(
            senderUserType = UserTypeEntity.APP
        )

        // then
        assertNotNull(messageEntity.sender)
        val senderUserType = messageEntity.sender!!.userType
        assertEquals(UserTypeEntity.APP, senderUserType)
        assertEquals(UserTypeEntity.APP, senderUserType)
    }

    @Test
    fun givenMessagesWithDifferentUserTypes_whenMappingToEntityMessages_thenAllSenderUserTypesAreMappedCorrectly() {
        // given / when
        val standardMessage = toEntityFromView(senderUserType = UserTypeEntity.STANDARD)
        val adminMessage = toEntityFromView(senderUserType = UserTypeEntity.ADMIN)
        val botMessage = toEntityFromView(senderUserType = UserTypeEntity.SERVICE)
        val appMessage = toEntityFromView(senderUserType = UserTypeEntity.APP)
        val externalMessage = toEntityFromView(senderUserType = UserTypeEntity.EXTERNAL)
        val guestMessage = toEntityFromView(senderUserType = UserTypeEntity.GUEST)
        val federatedMessage = toEntityFromView(senderUserType = UserTypeEntity.FEDERATED)

        // then
        assertNotNull(standardMessage.sender)
        assertEquals(UserTypeEntity.STANDARD, standardMessage.sender!!.userType)

        assertNotNull(adminMessage.sender)
        assertEquals(UserTypeEntity.ADMIN, adminMessage.sender!!.userType)

        assertNotNull(botMessage.sender)
        assertEquals(UserTypeEntity.SERVICE, botMessage.sender!!.userType)

        assertNotNull(appMessage.sender)
        assertEquals(UserTypeEntity.APP, appMessage.sender!!.userType)

        assertNotNull(externalMessage.sender)
        assertEquals(UserTypeEntity.EXTERNAL, externalMessage.sender!!.userType)

        assertNotNull(guestMessage.sender)
        assertEquals(UserTypeEntity.GUEST, guestMessage.sender!!.userType)

        assertNotNull(federatedMessage.sender)
        assertEquals(UserTypeEntity.FEDERATED, federatedMessage.sender!!.userType)
    }

    @Test
    fun givenTextMessageFromBot_whenMappingToEntity_thenBotUserTypeIsPreserved() {
        // given / when
        val messageEntity = toEntityFromView(
            contentType = MessageEntity.ContentType.TEXT,
            text = "Hello from bot",
            senderUserType = UserTypeEntity.SERVICE,
            senderName = "Bot Assistant"
        )

        // then
        assertNotNull(messageEntity.sender)
        assertEquals(UserTypeEntity.SERVICE, messageEntity.sender!!.userType)
        val content = messageEntity.content
        assertIs<MessageEntityContent.Text>(content)
        assertEquals("Hello from bot", content.messageBody)
    }

    @Test
    fun givenTextMessageFromApp_whenMappingToEntity_thenAppUserTypeIsPreserved() {
        // given / when
        val messageEntity = toEntityFromView(
            contentType = MessageEntity.ContentType.TEXT,
            text = "Message from app",
            senderUserType = UserTypeEntity.APP,
            senderName = "App Integration"
        )

        // then
        assertNotNull(messageEntity.sender)
        assertEquals(UserTypeEntity.APP, messageEntity.sender!!.userType)
        val content = messageEntity.content
        assertIs<MessageEntityContent.Text>(content)
        assertEquals("Message from app", content.messageBody)
    }

    @Test
    fun givenMessageWithReadStatus_whenSenderIsExternal_thenUserTypeIsMappedCorrectly() {
        // given / when
        val messageEntity = toEntityFromView(
            text = "External user message",
            status = MessageEntity.Status.READ,
            readCount = 5,
            senderUserType = UserTypeEntity.EXTERNAL
        )

        // then
        assertEquals(MessageEntity.Status.READ, messageEntity.status)
        assertEquals(5, messageEntity.readCount)
        assertNotNull(messageEntity.sender)
        assertEquals(UserTypeEntity.EXTERNAL, messageEntity.sender!!.userType)
    }

    @Suppress("LongParameterList")
    private fun toEntityFromView(
        id: String = "test-message-id",
        conversationId: QualifiedIDEntity = QualifiedIDEntity("conv", "domain.com"),
        contentType: MessageEntity.ContentType = MessageEntity.ContentType.TEXT,
        date: Instant = Instant.DISTANT_FUTURE,
        senderUserId: QualifiedIDEntity = QualifiedIDEntity("sender", "domain.com"),
        senderClientId: String? = "client1",
        status: MessageEntity.Status = MessageEntity.Status.SENT,
        lastEditTimestamp: Instant? = null,
        visibility: MessageEntity.Visibility = MessageEntity.Visibility.VISIBLE,
        expectsReadConfirmation: Boolean = false,
        expireAfterMillis: Long? = null,
        selfDeletionEndDate: Instant? = null,
        readCount: Long = 0,
        senderName: String? = "Test User",
        senderHandle: String? = "testhandle",
        senderEmail: String? = null,
        senderPhone: String? = null,
        senderAccentId: Int = 1,
        senderTeamId: String? = null,
        senderConnectionStatus: ConnectionEntity.State = ConnectionEntity.State.ACCEPTED,
        senderPreviewAssetId: QualifiedIDEntity? = null,
        senderCompleteAssetId: QualifiedIDEntity? = null,
        senderAvailabilityStatus: UserAvailabilityStatusEntity = UserAvailabilityStatusEntity.NONE,
        senderUserType: UserTypeEntity = UserTypeEntity.STANDARD,
        senderBotService: BotIdEntity? = null,
        senderIsDeleted: Boolean = false,
        senderExpiresAt: Instant? = null,
        senderDefederated: Boolean = false,
        senderSupportedProtocols: Set<SupportedProtocolEntity>? = setOf(SupportedProtocolEntity.PROTEUS),
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
        allReactionsJson: String = "{}",
        selfReactionsJson: String = "[]",
        mentions: String = "[]",
        attachments: String? = null,
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
        conversationProtocolChanged: com.wire.kalium.persistence.dao.conversation.ConversationEntity.Protocol? = null,
        latitude: Float? = null,
        longitude: Float? = null,
        locationName: String? = null,
        locationZoom: Int? = null,
        legalHoldMemberList: List<QualifiedIDEntity>? = null,
        legalHoldType: MessageEntity.LegalHoldType? = null,
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
            memberChangeList,
            memberChangeType,
            unknownContentTypeName,
            unknownContentData,
            restrictedAssetMimeType,
            restrictedAssetSize,
            restrictedAssetName,
            failedToDecryptData,
            decryptionErrorCode,
            isDecryptionResolved,
            conversationName,
            allReactionsJson,
            selfReactionsJson,
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
            federationDomainList,
            federationType,
            conversationProtocolChanged,
            latitude,
            longitude,
            locationName,
            locationZoom,
            legalHoldMemberList,
            legalHoldType,
        )
    }
}

