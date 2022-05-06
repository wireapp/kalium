package com.wire.kalium.logic.data.message

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import com.wire.kalium.logic.data.id.ConversationId

class ProtoContentMapperTest {

    private lateinit var protoContentMapper: ProtoContentMapper

    @BeforeTest
    fun setup(){
        protoContentMapper = ProtoContentMapperImpl()
    }

    @Test
    fun givenTextContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal(){
        val messageContent = MessageContent.Text("Hello")
        val protoContent = ProtoContent(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    @Test
    fun givenCallingContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal(){
        val callingContent = MessageContent.Calling("Calling")
        val protoContent = ProtoContent(TEST_CALLING_UUID, callingContent)

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
        val messageContent = MessageContent.DeleteForMe(TEST_MESSAGE_UUID)
        val protoContent = ProtoContent(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }


    private companion object{
        const val TEST_MESSAGE_UUID = "testUuid"
        const val TEST_CALLING_UUID = "callingUuid"
    }
}
