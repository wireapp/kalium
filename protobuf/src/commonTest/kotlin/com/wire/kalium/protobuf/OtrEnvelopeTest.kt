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
