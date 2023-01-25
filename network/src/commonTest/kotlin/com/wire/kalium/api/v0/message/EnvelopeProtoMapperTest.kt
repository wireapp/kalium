/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessagePriority
import com.wire.kalium.network.api.base.authenticated.message.provideEnvelopeProtoMapper
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.otr.QualifiedNewOtrMessage
import kotlin.test.Test
import kotlin.test.assertContentEquals

@IgnoreIOS
class EnvelopeProtoMapperTest {

    private val envelopeProtoMapper = provideEnvelopeProtoMapper()

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

    private companion object {
        const val TEST_SENDER = "9AFBD180"
    }
}
