package com.wire.kalium

import com.wire.kalium.assets.MessageText
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.helium.Application
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder

@Disabled("This is an integration test which requires access to Wire account.")
class ApplicationSenderTest {
    @Test
    @Throws(Exception::class)
    fun sendMessagesTest() {
        val email = "your email"
        val password = "secret"
        val conversationId = UUID.fromString("")

        val client: Client = ClientBuilder
                .newClient()

        val crypto = CryptoFile("/tmp/joker")

        val storage = Storage()

        val app = Application(email, password)
                .addClient(client)
                .addCrypto(crypto)
                .addStorage(storage)

        // Login, create device if needed, setup token refresh timer, pull missed messages and more
        app.start()

        // Create WireClient for this conversationId
        val wireClient = app.getWireClient(conversationId)

        // Send text
        wireClient.send(MessageText("Hi there!"))

        app.stop()
    }

    class Storage : IState {
        var newBot: NewBot? = null

        override fun saveState(newBot: NewBot): Boolean {
            this.newBot = newBot
            return true
        }

        override fun getState(): NewBot {
            return newBot!!
        }

        override fun removeState(): Boolean {
            return true
        }
    }
}
