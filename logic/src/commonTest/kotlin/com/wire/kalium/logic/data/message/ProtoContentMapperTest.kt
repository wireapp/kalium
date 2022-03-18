package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.ConversationId
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtoContentMapperTest {

    private lateinit var protoContentMapper: ProtoContentMapper

    @BeforeTest
    fun setup(){
        protoContentMapper = provideProtoContentMapper()
    }

    @Test
    fun givenTextContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.Text("Hello")
        val protoContent = ProtoContent(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenDeleteMessageContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.DeleteMessage(TEST_MESSAGE_UUID)
        val protoContent = ProtoContent(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenHideMessageContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal() {
        val messageContent = MessageContent.HideMessage(
            TEST_MESSAGE_UUID, conversationId = ConversationId(
                TEST_MESSAGE_UUID,
                TEST_MESSAGE_UUID
            )
        )
        val protoContent = ProtoContent(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    private companion object {
        const val TEST_MESSAGE_UUID = "testUuid"
    }
}
