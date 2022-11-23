package com.wire.kalium.logic.data.message

import com.wire.kalium.cryptography.utils.generateRandomAES256Key
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.GenericMessage
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
        val protoContent = ProtoContent.Readable(TEST_MESSAGE_UUID, messageContent)

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
        val protoContent = ProtoContent.Readable(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenProtoAssetContentWithStatusNotUploaded_whenMappingBackFromProtoData_thenTheDecodingGoesCorrectly() {
        val assetName = "Mocked-Asset.bin"
        val mockedAsset = assetName.toByteArray()
        val protobuf = GenericMessage(
            TEST_MESSAGE_UUID, GenericMessage.Content.Asset(
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
                remoteData = defaultRemoteData,
                downloadStatus = Message.DownloadStatus.NOT_DOWNLOADED
            )
        )
        val protoContent = ProtoContent.Readable(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenCallingContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val callingContent = MessageContent.Calling("Calling")
        val protoContent = ProtoContent.Readable(TEST_CALLING_UUID, callingContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenDeleteMessageContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DeleteMessage(TEST_MESSAGE_UUID)
        val protoContent = ProtoContent.Readable(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenHideMessageContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_UUID, TEST_CONVERSATION_ID
        )
        val protoContent = ProtoContent.Readable(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenHideMessageContentWithNullQualifiedId_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DeleteForMe(
            TEST_MESSAGE_UUID, TEST_CONVERSATION_UUID, null
        )
        val protoContent = ProtoContent.Readable(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenEditedTextGenericMessage_whenMappingFromProtoData_thenTheReturnValueShouldHaveTheCorrectEditedMessageId() {
        val replacedMessageId = "replacedMessageId"
        val textContent = MessageEdit.Content.Text(Text("textContent"))
        val genericMessage = GenericMessage(TEST_MESSAGE_UUID, GenericMessage.Content.Edited(MessageEdit(replacedMessageId, textContent)))
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
        val genericMessage = GenericMessage(TEST_MESSAGE_UUID, GenericMessage.Content.Edited(MessageEdit(replacedMessageId, textContent)))
        val protobufBlob = PlainMessageBlob(genericMessage.encodeToByteArray())

        val result = protoContentMapper.decodeFromProtobuf(protobufBlob)

        assertIs<ProtoContent.Readable>(result)
        val content = result.messageContent
        assertIs<MessageContent.TextEdited>(content)
        assertEquals(textContent.value.content, content.newContent)
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

    private companion object {
        const val TEST_MESSAGE_UUID = "testUuid"
        const val TEST_CONVERSATION_UUID = "testConversationUuid"
        val TEST_CONVERSATION_ID = TestConversation.ID
        const val TEST_CALLING_UUID = "callingUuid"
    }
}
