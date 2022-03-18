package com.wire.kalium.protobuf

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

        GenericMessage().encodeToByteArray()

        assertEquals(clientId, decoded.sender)
    }

    private companion object {
        val clientId = ClientId(946518)
    }
}
