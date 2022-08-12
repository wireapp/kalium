package com.wire.kalium.logic.data.conversation

import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProtocolInfoMapperTest {
    private val protocolInfoMapper = ProtocolInfoMapperImpl()

    @Test
    fun givenConversationMLSProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.toEntity(CONVERSATION_MLS_PROTOCOL_INFO)
        assertIs<ConversationEntity.ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONV_ENTITY_MLS_PROTOCOL_INFO)
    }

    @Test
    fun givenConversationProteusProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.toEntity(CONVERSATION_PROTEUS_PROTOCOL_INFO)
        assertIs<ConversationEntity.ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONV_ENTITY_PROTEUS_PROTOCOL_INFO)
    }

    @Test
    fun givenEntityMLSProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.fromEntity(CONV_ENTITY_MLS_PROTOCOL_INFO)
        assertIs<Conversation.ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONVERSATION_MLS_PROTOCOL_INFO)
    }

    @Test
    fun givenEntityProteusProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.fromEntity(CONV_ENTITY_MLS_PROTOCOL_INFO)
        assertIs<Conversation.ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONVERSATION_MLS_PROTOCOL_INFO)
    }

    companion object {
        val CONVERSATION_MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            "GROUP_ID",
            Conversation.ProtocolInfo.MLS.GroupState.ESTABLISHED,
            5UL,
            Instant.parse("2021-03-30T15:36:00.000Z")
        )
        val CONVERSATION_PROTEUS_PROTOCOL_INFO = Conversation.ProtocolInfo.Proteus

        val CONV_ENTITY_MLS_PROTOCOL_INFO =
            ConversationEntity.ProtocolInfo.MLS(
                "GROUP_ID",
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                5UL,
                Instant.parse("2021-03-30T15:36:00.000Z")
            )
        val CONV_ENTITY_PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus

    }
}
