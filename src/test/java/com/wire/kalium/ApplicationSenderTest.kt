package com.wire.kalium

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.outbound.MessageText
import org.junit.jupiter.api.Test
import java.util.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder

class ApplicationSenderTest {
    @Test
    @Throws(Exception::class)
    fun sendMessagesTest() {
        val email = "dejan@wire.com"
        val password = "12345678"
        val conversationId = UUID.fromString("c2aba93d-56ad-4992-915d-e66e69d96418")

        val client: Client = ClientBuilder
                .newClient()
                .register(JacksonJsonProvider::class.java)

        val crypto = CryptoFile("./joker")

        val app = Application(email, password)
                .addClient(client)
                .addCrypto(crypto)

        // Login, create device if needed, setup token refresh timer, pull missed messages and more
        app.start()

        // Create WireClient for this conversationId
        val wireClient = app.getWireClient(conversationId)

        // Send text
        wireClient.send(MessageText("Is that you, John Wayne? Is this me?"))

        app.stop()
    }
}
