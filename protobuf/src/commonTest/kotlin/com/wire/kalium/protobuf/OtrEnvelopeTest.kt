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

package com.wire.kalium.protobuf

import com.wire.kalium.protobuf.otr.ClientId
import com.wire.kalium.protobuf.otr.QualifiedNewOtrMessage
import pbandk.decodeFromByteArray
import pbandk.encodeToByteArray
import kotlin.test.Test
import kotlin.test.assertEquals

class OtrEnvelopeTest {

    @Test
    fun givenASender_whenEncodingAndAndDecodingEnvelope_thenTheSenderShouldMatch(){
        val otrMessage = QualifiedNewOtrMessage(
            sender = clientId
        )

        val byteArray = otrMessage.encodeToByteArray()
        val decoded = QualifiedNewOtrMessage.decodeFromByteArray(byteArray)

        assertEquals(clientId, decoded.sender)
    }

    private companion object {
        val clientId = ClientId(946518)
    }
}
