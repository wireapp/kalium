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
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.Composite
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.GenericMessage.UnknownStrategy
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.Text
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun givenThreadedTextContent_whenMappingToProtoDataAndBack_thenThreadIdShouldBePreserved() {
        assertReadableThreadRoundTrip(
            content = MessageContent.Text("Threaded hello")
        )
    }

    @Test
    fun givenThreadedAssetContent_whenMappingToProtoDataAndBack_thenThreadIdShouldBePreserved() {
        val assetName = "Mocked-Asset.bin"
        val mockedAsset = assetName.encodeToByteArray()
        val defaultRemoteData = AssetContent.RemoteData(
            otrKey = ByteArray(0),
            sha256 = ByteArray(0),
            assetId = "",
            assetDomain = null,
            assetToken = null,
            encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC
        )
        assertReadableThreadRoundTrip(
            content = MessageContent.Asset(
                AssetContent(
                    sizeInBytes = mockedAsset.size.toLong(),
                    name = assetName,
                    mimeType = "file/binary",
                    remoteData = defaultRemoteData
                )
            )
        )
    }

    @Test
    fun givenThreadedMultipartContent_whenMappingToProtoDataAndBack_thenThreadIdShouldBePreserved() {
        assertReadableThreadRoundTrip(
            content = MessageContent.Multipart(
                value = "Multipart body"
            )
        )
    }

    @Test
    fun givenThreadedCompositeContent_whenMappingToProtoDataAndBack_thenThreadIdShouldBePreserved() {
        assertReadableThreadRoundTrip(
            content = MessageContent.Composite(
                textContent = MessageContent.Text("Composite text"),
                buttonList = listOf(Button(text = "Open", id = "btn-1", isSelected = false))
            )
        )
    }

    @Test
    fun givenNonThreadedRegularContent_whenDecoding_thenThreadIdIsNull() {
        val genericMessage = GenericMessage(
            messageId = TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Knock(
                com.wire.kalium.protobuf.messages.Knock(hotKnock = true)
            )
        )
        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(genericMessage.encodeToByteArray()))

        assertIs<ProtoContent.Readable>(decoded)
        assertEquals(null, decoded.threadId)
        assertIs<MessageContent.Knock>(decoded.messageContent)
    }

    @Test
    fun givenThreadedTextContent_whenDecoding_thenKeepsOriginalRegularContent() {
        val genericMessage = GenericMessage(
            messageId = TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Text(
                Text(
                    content = "threaded text",
                    threadId = TEST_THREAD_ID
                )
            )
        )
        val decoded = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(genericMessage.encodeToByteArray()))

        assertIs<ProtoContent.Readable>(decoded)
        assertEquals(TEST_THREAD_ID, decoded.threadId)
        assertIs<MessageContent.Text>(decoded.messageContent)
    }

    @Test
    fun givenThreadedExpiringMessage_whenEncoding_thenItFails() {
        val protoContent = ProtoContent.Readable(
            messageUid = TEST_MESSAGE_UUID,
            messageContent = MessageContent.Text("hello"),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
            expiresAfterMillis = 1000L,
            threadId = TEST_THREAD_ID
        )

        assertFailsWith<IllegalArgumentException> {
            protoContentMapper.encodeToProtobuf(protoContent)
        }
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
        val mockedAsset = assetName.encodeToByteArray()
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
        val mockedAsset = assetName.encodeToByteArray()
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
    fun givenEditedCompositeGenericMessage_whenMappingFromProtoData_thenTheReturnValueShouldHaveTheCorrectEditedMessageId() {
        val replacedMessageId = "replacedMessageId"
        val compositeContent = MessageEdit.Content.Composite(
            Composite(
                listOf(
                    Composite.Item(Composite.Item.Content.Text(Text("textContent"))),
                    Composite.Item(
                        Composite.Item.Content.Button(
                            com.wire.kalium.protobuf.messages.Button(
                                text = "button1",
                                id = "button1",
                            )
                        )
                    )
                )
            )
        )
        val genericMessage = GenericMessage(
            messageId = TEST_MESSAGE_UUID,
            content = GenericMessage.Content.Edited(
                MessageEdit(replacedMessageId, compositeContent)
            )
        )
        val protobufBlob = PlainMessageBlob(genericMessage.encodeToByteArray())

        val result = protoContentMapper.decodeFromProtobuf(protobufBlob)

        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.CompositeEdited>(content)
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
            Button("button1", "button1", false),
            Button("button2", "button2", false)
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
        val mockedAsset = assetName.encodeToByteArray()
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
        const val TEST_THREAD_ID = "d1dbe8e0-7e8f-4d85-9af5-2f913f2d0eb9"
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_CALLING_UUID = "callingUuid"
    }

    private fun assertReadableThreadRoundTrip(content: MessageContent.Regular) {
        val protoContent = ProtoContent.Readable(
            messageUid = TEST_MESSAGE_UUID,
            messageContent = content,
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
            threadId = TEST_THREAD_ID
        )

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val genericMessage = GenericMessage.decodeFromByteArray(encoded.data)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        val genericContent = genericMessage.content
        when (content) {
            is MessageContent.Text -> {
                assertIs<GenericMessage.Content.Text>(genericContent)
                assertEquals(TEST_THREAD_ID, genericContent.value.threadId)
            }

            is MessageContent.Asset -> {
                assertIs<GenericMessage.Content.Asset>(genericContent)
                assertEquals(TEST_THREAD_ID, genericContent.value.threadId)
            }

            is MessageContent.Multipart -> {
                assertIs<GenericMessage.Content.Multipart>(genericContent)
                assertEquals(TEST_THREAD_ID, genericContent.value.threadId)
            }

            is MessageContent.Composite -> {
                assertIs<GenericMessage.Content.Composite>(genericContent)
                assertEquals(TEST_THREAD_ID, genericContent.value.threadId)
            }

            else -> error("Unexpected threaded content type: ${content.getType()}")
        }
        assertEquals(protoContent, decoded)
    }
}
