package com.wire.helium

import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider
import com.wire.xenon.MessageHandlerBase
import com.wire.xenon.WireClient
import com.wire.xenon.assets.MessageText
import com.wire.xenon.assets.Picture
import com.wire.xenon.backend.models.Conversation
import com.wire.xenon.backend.models.User
import com.wire.xenon.crypto.CryptoDatabase
import com.wire.xenon.crypto.storage.JdbiStorage
import com.wire.xenon.factories.CryptoFactory
import com.wire.xenon.factories.StorageFactory
import com.wire.xenon.models.AssetKey
import com.wire.xenon.models.PhotoPreviewMessage
import com.wire.xenon.models.RemoteMessage
import com.wire.xenon.models.TextMessage
import com.wire.xenon.state.JdbiState
import com.wire.xenon.tools.Logger
import com.wire.xenon.tools.Util
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.util.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.ClientBuilder

@Disabled("This is an integration test which requires access to Wire account.")
class ApplicationTest : DatabaseTestBase() {
    @Test
    @Throws(Exception::class)
    fun sendMessagesTest() {
        val email = "your email"
        val password = "secret"
        val wsUrl = "wss://prod-nginz-ssl.wire.com"
        val client: Client = ClientBuilder
                .newClient()
                .register(JacksonJsonProvider::class.java)
        val messageHandlerBase: MessageHandlerBase = object : MessageHandlerBase() {
            fun onText(client: WireClient?, msg: TextMessage) {
                Logger.info("onText: received: %s", msg.getText())
            }

            fun onPhotoPreview(client: WireClient?, msg: PhotoPreviewMessage) {
                Logger.info("onImage: received: %d bytes, %s", msg.getSize(), msg.getMimeType())
            }

            fun onAssetData(client: WireClient, msg: RemoteMessage) {
                try {
                    val bytes: ByteArray = client.downloadAsset(msg.getAssetId(), msg.getAssetToken(), msg.getSha256(), msg.getOtrKey())
                } catch (e: Exception) {
                    Logger.exception("It was not possible to download an asset.", e)
                }
            }
        }
        val app = Application(email, password, false, wsUrl)
                .addClient(client)
                .addCryptoFactory(cryptoFactory)
                .addStorageFactory(storageFactory)
                .addHandler(messageHandlerBase)

        // Login, create device if needed, setup token refresh timer, pull missed messages and more
        app.start()

        // Create WireClient without Conversation in order to create one
        var wireClient = app.getWireClient(null)
        val self: User = wireClient.getSelf()
        val ottoUserId = wireClient.getUserId("ottothebot")

        // Create new conversation with Otto
        val conv: Conversation = wireClient.createConversation("Test", null, listOf(ottoUserId))

        // Create new WireClient this time for this newly created conversation
        wireClient = app.getWireClient(conv.id)

        // Add Echo bot into this conv (code: 59d7abe5-3850-4b34-8fe5-0bcd4bfad4e6:aba311a6-fb14-46c9-af1b-3cb454762ef2)
        val serviceId = UUID.fromString("aba311a6-fb14-46c9-af1b-3cb454762ef2")
        val providerId = UUID.fromString("59d7abe5-3850-4b34-8fe5-0bcd4bfad4e6")
        wireClient.addService(serviceId, providerId)

        // Send text
        wireClient.send(MessageText("Hi there!"))

        // Send Image
        val bytes: ByteArray = Util.getResource("moon.jpg")
        val image = Picture(bytes, "image/jpeg")
        val assetKey: AssetKey = wireClient.uploadAsset(image)
        image.setAssetKey(assetKey.id)
        image.setAssetToken(assetKey.token)
        wireClient.send(image)
        Thread.sleep(15 * 1000.toLong())
        app.stop()
    }

    val storageFactory: StorageFactory
        get() = StorageFactory { userId -> JdbiState(userId, jdbi) }
    val cryptoFactory: CryptoFactory
        get() = CryptoFactory { userId -> CryptoDatabase(userId, JdbiStorage(jdbi)) }
}