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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProtocolInfoMapperTest {
    private val protocolInfoMapper = ProtocolInfoMapperImpl()

    @Test
    fun givenConversationMixedProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.toEntity(CONVERSATION_MIXED_PROTOCOL_INFO)
        assertIs<ConversationEntity.ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONV_ENTITY_MIXED_PROTOCOL_INFO)
    }

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
    fun givenEntityMixedProtocolInfo_WhenMapToConversationProtocolInfo_ResultShouldBeEqual() = runTest {
        val mappedValue = protocolInfoMapper.fromEntity(CONV_ENTITY_MIXED_PROTOCOL_INFO)
        assertIs<Conversation.ProtocolInfo>(mappedValue)
        assertEquals(mappedValue, CONVERSATION_MIXED_PROTOCOL_INFO)
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
        val CONVERSATION_MIXED_PROTOCOL_INFO = Conversation.ProtocolInfo.Mixed(
            GroupID("GROUP_ID"),
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            5UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )
        val CONVERSATION_MLS_PROTOCOL_INFO = Conversation.ProtocolInfo.MLS(
            GroupID("GROUP_ID"),
            Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
            5UL,
            Instant.parse("2021-03-30T15:36:00.000Z"),
            cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
        )
        val CONVERSATION_PROTEUS_PROTOCOL_INFO = Conversation.ProtocolInfo.Proteus

        val CONV_ENTITY_MIXED_PROTOCOL_INFO =
            ConversationEntity.ProtocolInfo.Mixed(
                "GROUP_ID",
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                5UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        val CONV_ENTITY_MLS_PROTOCOL_INFO =
            ConversationEntity.ProtocolInfo.MLS(
                "GROUP_ID",
                groupState = ConversationEntity.GroupState.ESTABLISHED,
                5UL,
                Instant.parse("2021-03-30T15:36:00.000Z"),
                cipherSuite = ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        val CONV_ENTITY_PROTEUS_PROTOCOL_INFO = ConversationEntity.ProtocolInfo.Proteus
    }
}
