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

package com.wire.kalium.logic.data.message

import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.GenericMessage.UnknownStrategy
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.Text
import io.ktor.utils.io.core.toByteArray
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProtoContentMapperTest {

    private lateinit var protoContentMapper: ProtoContentMapper
    val selfUserId = UserId("user-id", "domain")
    val idMapper: IdMapper = IdMapperImpl()

    @BeforeTest
    fun setup() {
        protoContentMapper = ProtoContentMapperImpl(selfUserId = selfUserId)
    }

    @Test
    fun givenTextContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.Text("Hello")
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenTextContentWithQuoteReference_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.Text(
            value = "Hello",
            quotedMessageReference = MessageContent.QuoteReference(
                quotedMessageId = "quotedMessageId", quotedMessageSha256 = null, true
            )
        )
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenProtoAssetContentWithStatusNotUploaded_whenMappingBackFromProtoData_thenTheDecodingGoesCorrectly() {
        val assetName = "Mocked-Asset.bin"
        val mockedAsset = assetName.toByteArray()
        val protobuf = GenericMessage(
            messageId = TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Asset(
                Asset(
                    original = Asset.Original(
                        mimeType = "file/binary",
                        size = mockedAsset.size.toLong(),
                        name = assetName,
                    ),
                    status = Asset.Status.NotUploaded(Asset.NotUploaded.CANCELLED),
                )
            )
        )
        val encoded = PlainMessageBlob(protobuf.encodeToByteArray())
        protoContentMapper.decodeFromProtobuf(encoded)
    }

    @Test
    fun givenProtoAssetContent_whenMappingBack_thenTheContentsShouldMatchTheOriginal() {
        val assetName = "Mocked-Asset.bin"
        val mockedAsset = assetName.toByteArray()
        val defaultRemoteData = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(0),
            assetId = "",
            assetDomain = null,
            assetToken = null,
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC
        )
        val messageContent = MessageContent.Asset(
            AssetContent(
                sizeInBytes = mockedAsset.size.toLong(),
                name = assetName,
                mimeType = "file/binary",
                remoteData = defaultRemoteData
            )
        )
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenCallingContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val callingContent = MessageContent.Calling("Calling")
        val protoContent = ProtoContent.Readable(
            TEST_CALLING_UUID,
            callingContent,
            false,
            Conversation.LegalHoldStatus.UNKNOWN
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenDeleteMessageContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DeleteMessage(TEST_MESSAGE_UUID)
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            Conversation.LegalHoldStatus.UNKNOWN
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenHideMessageContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_ID
        )
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            Conversation.LegalHoldStatus.UNKNOWN
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenEditedTextGenericMessage_whenMappingFromProtoData_thenTheReturnValueShouldHaveTheCorrectEditedMessageId() {
        val replacedMessageId = "replacedMessageId"
        val textContent = MessageEdit.Content.Text(Text("textContent"))
        val genericMessage = GenericMessage(
            messageId = TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Edited(
                MessageEdit(replacedMessageId, textContent)
            )
        )
        val protobufBlob = PlainMessageBlob(genericMessage.encodeToByteArray())

        val result = protoContentMapper.decodeFromProtobuf(protobufBlob)

        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.TextEdited>(content)
        assertEquals(replacedMessageId, content.editMessageId)
    }

    @Test
    fun givenEditedTextGenericMessage_whenMappingFromProtoData_thenTheReturnValueShouldHaveTheCorrectUpdatedContent() {
        val replacedMessageId = "replacedMessageId"
        val textContent = MessageEdit.Content.Text(Text("textContent"))
        val genericMessage = GenericMessage(
            messageId = TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Edited(MessageEdit(replacedMessageId, textContent))
        )
        val protobufBlob = PlainMessageBlob(genericMessage.encodeToByteArray())

        val result = protoContentMapper.decodeFromProtobuf(protobufBlob)

        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.TextEdited>(content)
        assertEquals(textContent.value.content, content.newContent)
    }

    @Test
    fun givenReadReceipt_whenMappingToProtoAndBack_thenShouldMaintainSameValues() {
        val messageUid = "uid"
        val content = MessageContent.Receipt(
            ReceiptType.READ,
            listOf("messageI", "messageII", "messageIII")
        )

        val originalContent = ProtoContent.Readable(
            messageUid,
            content,
            false,
            Conversation.LegalHoldStatus.UNKNOWN
        )
        val encoded = protoContentMapper.encodeToProtobuf(originalContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(originalContent, decoded)
    }

    @Test
    fun givenReactionContent_whenMappingToProtoAndBack_thenShouldMaintainSameValues() {
        val messageUid = "uid"
        val emojis = setOf("👍", "👎")
        val content = MessageContent.Reaction(
            messageUid,
            emojis
        )

        val originalContent = ProtoContent.Readable(
            messageUid,
            content,
            false,
            Conversation.LegalHoldStatus.ENABLED
        )
        val encoded = protoContentMapper.encodeToProtobuf(originalContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(originalContent, decoded)
    }

    @Test
    fun givenKnockContent_whenMappingToProtoAndBack_thenShouldMaintainSameValues() {
        val messageUid = "uid"
        val content = MessageContent.Knock(true)

        val originalContent = ProtoContent.Readable(
            messageUid,
            content,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )
        val encoded = protoContentMapper.encodeToProtobuf(originalContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(originalContent, decoded)
    }

    @Test
    fun givenCompositeContent_whenMappingToProtoAndBack_thenShouldMaintainSameValues() {
        val messageUid = "uid"
        val textContent = MessageContent.Text("Hello")
        val buttons = listOf(
            MessageContent.Composite.Button("button1", "button1", false),
            MessageContent.Composite.Button("button2", "button2", false)
        )
        val content = MessageContent.Composite(textContent, buttons)

        val originalContent = ProtoContent.Readable(
            messageUid,
            content,
            false,
            Conversation.LegalHoldStatus.ENABLED
        )
        val encoded = protoContentMapper.encodeToProtobuf(originalContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(originalContent, decoded)
    }

    @Test
    fun givenDeliveryReceipt_whenMappingToProtoAndBack_thenShouldMaintainSameValues() {
        val messageUid = "uid"
        val content = MessageContent.Receipt(
            ReceiptType.DELIVERED,
            listOf("messageI", "messageII", "messageIII")
        )

        val originalContent = ProtoContent.Readable(
            messageUid,
            content,
            false,
            Conversation.LegalHoldStatus.UNKNOWN
        )
        val encoded = protoContentMapper.encodeToProtobuf(originalContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(originalContent, decoded)
    }

    @Test
    fun givenReceiptOfUnknownType_whenMappingFromProto_thenShouldReturnIgnoredContent() {
        val messageUid = "uid"

        val protobuf = GenericMessage(
            messageId = messageUid,
            content = GenericMessage.Content.Confirmation(Confirmation(Confirmation.Type.fromValue(-1), messageUid))
        )
        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(protobuf.encodeToByteArray()))

        assertIs<ProtoContent.Readable>(decoded)
        assertIs<MessageContent.Ignored>(decoded.messageContent)
    }

    @Test
    fun givenNonParseableContentWithDefaultUnknownStrategy_whenMappingFromProto_thenShouldReturnIgnoredContent() {
        val protobuf = GenericMessage(
            messageId = "uid"
        )

        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(protobuf.encodeToByteArray()))

        assertIs<ProtoContent.Readable>(decoded)
        assertIs<MessageContent.Ignored>(decoded.messageContent)
    }

    @Test
    fun givenNonParseableContentWithUnknownStrategyIgnore_whenMappingFromProto_thenShouldReturnIgnoredContent() {
        val protobuf = GenericMessage(
            messageId = "uid",
            unknownStrategy = UnknownStrategy.IGNORE
        )

        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(protobuf.encodeToByteArray()))

        assertIs<ProtoContent.Readable>(decoded)
        assertIs<MessageContent.Ignored>(decoded.messageContent)
    }

    @Test
    fun givenNonParseableContentWithUnknownStrategyDiscardAndWarn_whenMappingFromProto_thenShouldReturnUnknownContentWithoutByteData() {
        val protobuf = GenericMessage(
            messageId = "uid",
            unknownStrategy = UnknownStrategy.DISCARD_AND_WARN
        )

        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(protobuf.encodeToByteArray()))

        assertIs<ProtoContent.Readable>(decoded)
        val content = decoded.messageContent
        assertIs<MessageContent.Unknown>(content)
        assertEquals(content.encodedData, null)
    }

    @Test
    fun givenNonParseableContentWithUnknownStrategyWarnUserAllowEntry_whenMappingFromProto_thenShouldReturnUnknownContentWithByteData() {
        val protobuf = GenericMessage(
            messageId = "uid",
            unknownStrategy = UnknownStrategy.WARN_USER_ALLOW_RETRY
        )
        val protobufByteArray = protobuf.encodeToByteArray()

        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(protobufByteArray))

        assertIs<ProtoContent.Readable>(decoded)
        val content = decoded.messageContent
        assertIs<MessageContent.Unknown>(content)
        assertEquals(content.encodedData, protobufByteArray)
    }

    @Test
    fun givenExternalMessageInstructions_whenEncodingToProtoAndBack_thenTheResultContentShouldEqualTheOriginal() {
        val messageUid = TEST_MESSAGE_UUID
        val otrKey = generateRandomAES256Key()
        val sha256 = byteArrayOf(0x20, 0x42, 0x31)
        val encryptionAlgorithm = MessageEncryptionAlgorithm.AES_GCM

        val instructions = ProtoContent.ExternalMessageInstructions(messageUid, otrKey.data, sha256, encryptionAlgorithm)
        val encoded = protoContentMapper.encodeToProtobuf(instructions)
        val result = protoContentMapper.decodeFromProtobuf(encoded)

        assertIs<ProtoContent.ExternalMessageInstructions>(result)
        assertEquals(messageUid, result.messageUid)
        assertContentEquals(otrKey.data, result.otrKey)
        assertContentEquals(sha256, result.sha256)
        assertEquals(encryptionAlgorithm, result.encryptionAlgorithm)
    }

    @Test
    fun givenExpirableTextContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.Text("Hello")
        val expiresAfterMillis = 1000L

        val protoContent = ProtoContent.Readable(
            messageUid = TEST_MESSAGE_UUID,
            messageContent = messageContent,
            expectsReadConfirmation = false,
            expiresAfterMillis = expiresAfterMillis,
            legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertIs<ProtoContent.Readable>(decoded)
        assertEquals(decoded.expiresAfterMillis, expiresAfterMillis)
        assertEquals(protoContent, decoded)
    }

    @Test
    fun givenExpiringAssetContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val assetName = "Mocked-Asset.bin"
        val mockedAsset = assetName.toByteArray()
        val defaultRemoteData = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(0),
            assetId = "",
            assetDomain = null,
            assetToken = null,
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC
        )
        val messageContent = MessageContent.Asset(
            AssetContent(
                sizeInBytes = mockedAsset.size.toLong(),
                name = assetName,
                mimeType = "file/binary",
                remoteData = defaultRemoteData
            )
        )
        val expiresAfterMillis = 1000L
        val protoContent = ProtoContent.Readable(
            messageUid = TEST_MESSAGE_UUID,
            messageContent = messageContent,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN,
            expiresAfterMillis = expiresAfterMillis
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertIs<ProtoContent.Readable>(decoded)
        assertEquals(decoded.expiresAfterMillis, expiresAfterMillis)
        assertEquals(protoContent, decoded)
    }

    @Test
    fun givenProtoLocationContent_whenMappingProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.Location(
            latitude = 1.0f,
            longitude = 2.0f,
            zoom = 3,
            name = "name"
        )
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            Conversation.LegalHoldStatus.DISABLED
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenDataTransferContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DataTransfer(
            MessageContent.DataTransfer.TrackingIdentifier(
                "abcd-1234"
            )
        )
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenInCallEmojiContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.InCallEmoji(
            emojis = mapOf("emoji" to 999)
        )
        val protoContent = ProtoContent.Readable(
            TEST_MESSAGE_UUID,
            messageContent,
            false,
            legalHoldStatus = Conversation.LegalHoldStatus.UNKNOWN
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    private companion object {
        const val TEST_MESSAGE_UUID = "testUuid"
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_CALLING_UUID = "callingUuid"
    }
}
