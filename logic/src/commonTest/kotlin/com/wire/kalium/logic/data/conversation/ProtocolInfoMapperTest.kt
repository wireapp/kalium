package com.wire.kalium.logic.data.conversation

import com.wire.kalium.persistence.dao.ConversationEntity
import kotlinx.coroutines.test.runTest
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
        assertIs<ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONVERSATION_MLS_PROTOCOL_INFO)
    }

    @Test
    fun givenEntityProteusProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.fromEntity(CONV_ENTITY_MLS_PROTOCOL_INFO)
        assertIs<ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONVERSATION_MLS_PROTOCOL_INFO)
    }

    companion object {
        val CONVERSATION_MLS_PROTOCOL_INFO = ProtocolInfo.MLS("GROUP_ID", groupState = GroupState.ESTABLISHED)
        val CONVERSATION_PROTEUS_PROTOCOL_INFO = ProtocolInfo.Proteus

        val CONV_ENTITY_MLS_PROTOCOL_INFO =
            ConversationEntity.ProtocolInfo.MLS("GROUP_ID", groupState = ConversationEntity.GroupState.ESTABLISHED)
        val CONV_ENTITY_PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus

    }
}
