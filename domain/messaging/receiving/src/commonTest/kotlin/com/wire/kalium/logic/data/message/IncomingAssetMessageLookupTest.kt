/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.reaction.ReactionDataEntity
import com.wire.kalium.persistence.dao.reaction.ReactionsEntity
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.milliseconds

class IncomingAssetMessageLookupTest {

    private val mapper = IncomingAssetMessageMapper()

    @Test
    fun givenRegularAssetEntity_whenMapping_thenEveryPersistenceRelevantStoredFieldIsPreserved() {
        val entity = regularEntity(content = assetEntityContent)

        val result = assertIs<StoredIncomingAssetMessage.RegularAsset>(mapper.fromEntity(entity))

        assertEquals(
            Message.Regular(
                id = storedMessageId,
                content = MessageContent.Asset(
                    AssetContent(
                        sizeInBytes = 418L,
                        name = "voice-message.ogg",
                        mimeType = "audio/ogg",
                        metadata = AssetContent.AssetMetadata.Audio(
                            durationMs = 8_100L,
                            normalizedLoudness = byteArrayOf(4, 2),
                        ),
                        remoteData = AssetContent.RemoteData(
                            otrKey = byteArrayOf(1, 2),
                            sha256 = byteArrayOf(3, 4),
                            assetId = "stored-asset-id",
                            assetToken = "stored-token",
                            assetDomain = "assets.example",
                            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM,
                        ),
                    )
                ),
                conversationId = storedConversationId,
                date = storedDate,
                senderUserId = storedSenderId,
                status = Message.Status.Read(17L),
                visibility = Message.Visibility.HIDDEN,
                senderUserName = "Stored Sender",
                isSelfMessage = true,
                senderClientId = ClientId("stored-client"),
                editStatus = Message.EditStatus.Edited(editDate),
                expirationData = Message.ExpirationData(
                    expireAfter = 9_000.milliseconds,
                    selfDeletionStatus = Message.ExpirationData.SelfDeletionStatus.Started(selfDeletionEndDate),
                ),
                reactions = Message.Reactions(
                    mapOf("👍" to Message.ReactionData(count = 3, isSelf = true))
                ),
                expectsReadConfirmation = true,
                deliveryStatus = DeliveryStatus.PartialDelivery(
                    recipientsFailedWithNoClients = listOf(noClientUserId),
                    recipientsFailedDelivery = listOf(failedDeliveryUserId),
                ),
            ),
            result.message,
        )
    }

    @Test
    fun givenImageAssetWithMissingDimensions_whenMapping_thenExistingImageMetadataFallbackIsPreserved() {
        val content = assetEntityContent.copy(
            assetMimeType = "image/jpeg",
            assetWidth = null,
            assetHeight = null,
            assetDurationMs = null,
            assetNormalizedLoudness = null,
        )

        val result = assertIs<StoredIncomingAssetMessage.RegularAsset>(mapper.fromEntity(regularEntity(content)))
        val mappedContent = assertIs<MessageContent.Asset>(result.message.content)

        assertEquals(AssetContent.AssetMetadata.Image(width = 0, height = 0), mappedContent.value.metadata)
    }

    @Test
    fun givenRestrictedAssetEntity_whenMapping_thenItIsClassifiedWithoutMappingABroadMessage() {
        val result = mapper.fromEntity(
            regularEntity(MessageEntityContent.RestrictedAsset("application/zip", 10L, "archive.zip"))
        )

        assertEquals(StoredIncomingAssetMessage.RestrictedAsset(storedSenderId), result)
    }

    @Test
    fun givenUnsupportedRegularEntity_whenMapping_thenClassificationContainsExistingLogInformation() {
        val result = mapper.fromEntity(regularEntity(MessageEntityContent.Text("not an asset")))

        assertEquals(
            StoredIncomingAssetMessage.UnsupportedRegular(
                senderUserId = storedSenderId,
                messageId = storedMessageId,
                conversationId = storedConversationId,
                contentType = "Text",
            ),
            result,
        )
    }

    @Test
    fun givenSystemEntity_whenMapping_thenItIsClassifiedSeparately() {
        assertEquals(
            StoredIncomingAssetMessage.System(storedSenderId),
            mapper.fromEntity(systemEntity()),
        )
    }

    @Test
    fun givenStoredRow_whenLookingUp_thenExactDaoArgumentsAndMappedClassificationAreReturned() = runTest {
        val dao = mock<MessageDAO> {
            everySuspend {
                getMessageById(eq(incomingMessageId), eq(incomingConversationEntity))
            } returns regularEntity(MessageEntityContent.RestrictedAsset("application/zip", 10L, "archive.zip"))
        }
        val lookup = IncomingAssetMessageLookupImpl(dao)

        assertEquals(
            Either.Right(StoredIncomingAssetMessage.RestrictedAsset(storedSenderId)),
            lookup.getMessageById(incomingConversationId, incomingMessageId),
        )
        verifySuspend(VerifyMode.exactly(1)) {
            dao.getMessageById(eq(incomingMessageId), eq(incomingConversationEntity))
        }
    }

    @Test
    fun givenMissingRow_whenLookingUp_thenDataNotFoundIsReturned() = runTest {
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } returns null
        }
        val lookup = IncomingAssetMessageLookupImpl(dao)

        assertEquals(
            Either.Left(StorageFailure.DataNotFound),
            lookup.getMessageById(incomingConversationId, incomingMessageId),
        )
    }

    @Test
    fun givenDaoException_whenLookingUp_thenItIsWrapped() = runTest {
        val expected = IllegalStateException("lookup failed")
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected
        }
        val lookup = IncomingAssetMessageLookupImpl(dao)

        assertEquals(
            Either.Left(StorageFailure.Generic(expected)),
            lookup.getMessageById(incomingConversationId, incomingMessageId),
        )
    }

    @Test
    fun givenDaoCancellation_whenLookingUp_thenTheSameCancellationEscapes() = runTest {
        val expected = CancellationException("lookup cancelled")
        val dao = mock<MessageDAO> {
            everySuspend { getMessageById(eq(incomingMessageId), eq(incomingConversationEntity)) } throws expected
        }
        val lookup = IncomingAssetMessageLookupImpl(dao)

        val actual = assertFailsWith<CancellationException> {
            lookup.getMessageById(incomingConversationId, incomingMessageId)
        }

        assertSame(expected, actual)
    }

    private fun regularEntity(content: MessageEntityContent.Regular): MessageEntity.Regular = MessageEntity.Regular(
        id = storedMessageId,
        conversationId = storedConversationEntity,
        date = storedDate,
        senderUserId = storedSenderEntity,
        status = MessageEntity.Status.READ,
        visibility = MessageEntity.Visibility.HIDDEN,
        content = content,
        isSelfMessage = true,
        readCount = 17L,
        expireAfterMs = 9_000L,
        selfDeletionEndDate = selfDeletionEndDate,
        senderName = "Stored Sender",
        senderClientId = "stored-client",
        editStatus = MessageEntity.EditStatus.Edited(editDate),
        reactions = ReactionsEntity(
            mapOf("👍" to ReactionDataEntity(count = 3, isSelf = true))
        ),
        expectsReadConfirmation = true,
        deliveryStatus = DeliveryStatusEntity.PartialDelivery(
            recipientsFailedWithNoClients = listOf(noClientUserEntity),
            recipientsFailedDelivery = listOf(failedDeliveryUserEntity),
        ),
    )

    private fun systemEntity(): MessageEntity.System = MessageEntity.System(
        id = storedMessageId,
        content = MessageEntityContent.MissedCall,
        conversationId = storedConversationEntity,
        date = storedDate,
        senderUserId = storedSenderEntity,
        status = MessageEntity.Status.SENT,
        expireAfterMs = null,
        selfDeletionEndDate = null,
        readCount = 0L,
        senderName = "Stored Sender",
    )

    private companion object {
        const val incomingMessageId = "incoming-message-id"
        const val storedMessageId = "stored-message-id"
        val incomingConversationId = ConversationId("incoming-conversation", "example.com")
        val incomingConversationEntity = QualifiedIDEntity("incoming-conversation", "example.com")
        val storedConversationId = ConversationId("stored-conversation", "example.com")
        val storedConversationEntity = QualifiedIDEntity("stored-conversation", "example.com")
        val storedSenderId = UserId("stored-sender", "example.com")
        val storedSenderEntity = QualifiedIDEntity("stored-sender", "example.com")
        val noClientUserId = UserId("no-client", "example.com")
        val noClientUserEntity = QualifiedIDEntity("no-client", "example.com")
        val failedDeliveryUserId = UserId("failed-delivery", "example.com")
        val failedDeliveryUserEntity = QualifiedIDEntity("failed-delivery", "example.com")
        val storedDate = Instant.parse("2026-08-01T08:00:00Z")
        val editDate = Instant.parse("2026-08-01T08:01:00Z")
        val selfDeletionEndDate = Instant.parse("2026-08-01T08:02:00Z")
        val assetEntityContent = MessageEntityContent.Asset(
            assetSizeInBytes = 418L,
            assetName = "voice-message.ogg",
            assetMimeType = "audio/ogg",
            assetOtrKey = byteArrayOf(1, 2),
            assetSha256Key = byteArrayOf(3, 4),
            assetId = "stored-asset-id",
            assetToken = "stored-token",
            assetDomain = "assets.example",
            assetEncryptionAlgorithm = "AES_GCM",
            assetDurationMs = 8_100L,
            assetNormalizedLoudness = byteArrayOf(4, 2),
        )
    }
}
