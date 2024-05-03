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

package com.wire.kalium.api.v0.message

import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapper
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapperImpl
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessagePriority
import com.wire.kalium.network.api.base.authenticated.message.QualifiedUserToClientToEncMsgMap
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.otr.QualifiedNewOtrMessage
import com.wire.kalium.protobuf.otr.QualifiedUserId
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeProtoMapperTest {

    private val envelopeProtoMapper: EnvelopeProtoMapper = EnvelopeProtoMapperImpl()

    @Test
    fun givenEnvelopeWithData_whenMappingToProtobuf_thenBlobShouldMatch() {
        val data = byteArrayOf(0x42, 0x13, 0x69)

        val encoded = envelopeProtoMapper.encodeToProtobuf(
            MessageApi.Parameters.QualifiedDefaultParameters(
                sender = TEST_SENDER,
                recipients = mapOf(),
                nativePush = true,
                priority = MessagePriority.HIGH,
                transient = false,
                externalBlob = data,
                messageOption = MessageApi.QualifiedMessageOption.ReportAll
            )
        )
        val newOtrMessage = QualifiedNewOtrMessage.decodeFromByteArray(encoded)

        assertContentEquals(data, newOtrMessage.blob!!.array)
    }

    @Test
    fun givenEnvelopeWithData_whenMappingToProtobuf_thenClientIdsShouldMatch() {
        val user = UserId("0822753e-dead-4f1a-acfc-a55d223cc76b", "example.com")
        val recipients: QualifiedUserToClientToEncMsgMap = mapOf(
            Pair(
                user, mapOf(
                    Pair("241b5be49179d81b", ByteArray(0)),
                    Pair("8bdacec7398a982e", ByteArray(0)),
                    Pair("e47d908549239b72", ByteArray(0)),
                    Pair("4c8346ce67fa0d7", ByteArray(0))
                )
            )
        )

        val encoded = envelopeProtoMapper.encodeToProtobuf(
            MessageApi.Parameters.QualifiedDefaultParameters(
                sender = TEST_SENDER,
                recipients = recipients,
                nativePush = true,
                priority = MessagePriority.HIGH,
                transient = false,
                externalBlob = null,
                messageOption = MessageApi.QualifiedMessageOption.ReportAll
            )
        )

        val newOtrMessage = QualifiedNewOtrMessage.decodeFromByteArray(encoded)
        val clients = newOtrMessage.recipients[0].entries[0].clients

        assertEquals(2601774246987946011L, clients[0].client.client)
        assertEquals(-8369149602455447506L, clients[1].client.client)
        assertEquals(-1982269358841029774L, clients[2].client.client)
        assertEquals(344583013822079191L, clients[3].client.client)
        assertEquals(3767849907233584983L, newOtrMessage.sender.client)
    }

    @Test
    fun givenMessageWithIgnoreSomeFlag_whenMapping_thenItIsMappedCorrectly() {
        val data = byteArrayOf(0x42, 0x13, 0x69)
        val usersToIgnore = listOf(
            UserId("0822753e-dead-4f1a-acfc-a55d223cc76b", "example.com")
        )
        val expected = usersToIgnore.map { QualifiedUserId(it.value, it.domain) }
        val encoded = envelopeProtoMapper.encodeToProtobuf(
            MessageApi.Parameters.QualifiedDefaultParameters(
                sender = TEST_SENDER,
                recipients = mapOf(),
                nativePush = true,
                priority = MessagePriority.HIGH,
                transient = false,
                externalBlob = data,
                messageOption = MessageApi.QualifiedMessageOption.IgnoreSome(usersToIgnore)
            )
        )
        val newOtrMessage = QualifiedNewOtrMessage.decodeFromByteArray(encoded)
        assertEquals(expected, newOtrMessage.ignoreOnly?.userIds)
    }

    @Test
    fun givenMessageWithReportSomeSomeFlag_whenMapping_thenItIsMappedCorrectly() {
        val data = byteArrayOf(0x42, 0x13, 0x69)
        val usersToIgnore = listOf(
            UserId("0822753e-dead-4f1a-acfc-a55d223cc76b", "example.com")
        )
        val expected = usersToIgnore.map { QualifiedUserId(it.value, it.domain) }
        val encoded = envelopeProtoMapper.encodeToProtobuf(
            MessageApi.Parameters.QualifiedDefaultParameters(
                sender = TEST_SENDER,
                recipients = mapOf(),
                nativePush = true,
                priority = MessagePriority.HIGH,
                transient = false,
                externalBlob = data,
                messageOption = MessageApi.QualifiedMessageOption.ReportSome(usersToIgnore)
            )
        )
        val newOtrMessage = QualifiedNewOtrMessage.decodeFromByteArray(encoded)
        assertEquals(expected, newOtrMessage.reportOnly?.userIds)
    }

    private companion object {
        const val TEST_SENDER = "344a178717a57757"
    }
}
