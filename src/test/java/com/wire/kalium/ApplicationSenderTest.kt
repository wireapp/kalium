package com.wire.kalium

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider
import com.wire.kalium.crypto.CryptoFile
import com.wire.kalium.models.inbound.PhotoPreviewMessage
import com.wire.kalium.models.inbound.TextMessage
import com.wire.kalium.tools.Logger
import org.junit.jupiter.api.Test
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder

class MessageHandlerImpl : MessageHandler {
    override fun onText(client: IWireClient, msg: TextMessage) {
        Logger.info("onText: received: %s", msg.text)
    }

    override fun onPhotoPreview(msg: PhotoPreviewMessage) {
        Logger.info("onImage: received: %d bytes, %s", msg.getSize(), msg.getMimeType())
    }
}

class ApplicationSenderTest {
    @Test
    @Throws(Exception::class)
    fun sendMessagesTest() {
        val email = "dejan@wire.com"
        val password = "12345678"

        val client: Client = ClientBuilder
                .newClient()
                .register(JacksonJsonProvider::class.java)

        val crypto = CryptoFile("./data/joker")

        val app = WebSocketApplication(email, password)
                .addClient(client)
                .addCrypto(crypto)
                .addWSUrl("wss://prod-nginz-ssl.wire.com")
                .addHandler(MessageHandlerImpl())

        // Login, create device if needed, setup token refresh timer, pull missed messages and more
        app.start()

        Thread.sleep(1000 * 60 * 10)
    }
}
