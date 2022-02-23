package com.wire.kalium.logic.data.message

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
    fun givenTextContent_whenMappingToProtoDataAndBack_thenTheContentsShouldMatchTheOriginal(){
        val messageContent = MessageContent.Text("Hello")
        val protoContent = ProtoContent(TEST_MESSAGE_UUID, messageContent)

        val encoded = protoContentMapper.encodeToProtobuf(protoContent)
        val decoded = protoContentMapper.decodeFromProtobuf(encoded)

        assertEquals(decoded, protoContent)
    }

    private companion object{
        const val TEST_MESSAGE_UUID = "testUuid"
    }
}
