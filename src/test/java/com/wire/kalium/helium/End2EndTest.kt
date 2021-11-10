package com.wire.helium

import com.google.protobuf.InvalidProtocolBufferException
import com.waz.model.Messages.GenericMessage
import com.wire.bots.cryptobox.IStorage
import com.wire.helium.helpers.DummyAPI
import com.wire.helium.helpers.MemStorage
import com.wire.helium.helpers.Util.deleteDir
import com.wire.xenon.assets.MessageText
import com.wire.xenon.backend.models.NewBot
import com.wire.xenon.crypto.CryptoDatabase
import com.wire.xenon.crypto.storage.JdbiStorage
import com.wire.xenon.models.otr.OtrMessage
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.*

class End2EndTest : DatabaseTestBase() {
    private var storage: IStorage? = null
    private var rootFolder: String? = null

    @BeforeEach
    fun beforeEach() {
        rootFolder = "helium-unit-test-" + UUID.randomUUID()
        storage = JdbiStorage(jdbi)
        flyway.migrate()
    }

    @AfterEach
    @Throws(IOException::class)
    fun afterEach() {
        flyway.clean()
        deleteDir(rootFolder)
    }

    @Test
    @Throws(Exception::class)
    fun testAliceToAlice() {
        val aliceId = UUID.randomUUID()
        val client1 = "alice1_" + UUID.randomUUID()
        val state = NewBot()
        state.id = aliceId
        state.client = aliceId.toString()
        val aliceCrypto = CryptoDatabase(aliceId, storage, "$rootFolder/testAliceToAlice/1")
        val aliceCrypto1 = CryptoDatabase(aliceId, storage, "$rootFolder/testAliceToAlice/2")
        val api = DummyAPI()
        api.addDevice(aliceId, client1, aliceCrypto1.box().newLastPreKey())
        val aliceClient = WireClientImp(api, aliceCrypto, state, null)
        for (i in 0..9) {
            val text = "Hello Alice, This is Alice!"
            aliceClient.send(MessageText(text))
            val msg: OtrMessage? = api.getMsg()
            val cipher1: String = msg.get(aliceId, client1)
            val decrypt: String = aliceCrypto1.decrypt(aliceId, msg.getSender(), cipher1)
            val s1 = getText(decrypt)
            assert(text == s1)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testAliceToBob() {
        val bobId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()
        val client1 = "bob1"
        val storage = MemStorage()
        val aliceCrypto = CryptoDatabase(aliceId, storage, "$rootFolder/testAliceToBob")
        val bobCrypto = CryptoDatabase(bobId, storage, "$rootFolder/testAliceToBob")
        val api = DummyAPI()
        api.addDevice(bobId, client1, bobCrypto.box().newLastPreKey())
        val state = NewBot()
        state.id = aliceId
        state.client = "alice1"
        val aliceClient = WireClientImp(api, aliceCrypto, state, null)
        for (i in 0..9) {
            val text = "Hello Bob, This is Alice!"
            aliceClient.send(MessageText(text))
            val msg: OtrMessage? = api.getMsg()
            val cipher1: String = msg.get(bobId, client1)
            val decrypt: String = bobCrypto.decrypt(aliceId, msg.getSender(), cipher1)
            val s1 = getText(decrypt)
            assert(text == s1)
        }
    }

    @Test
    @Throws(Exception::class)
    fun testMultiDevicePostgres() {
        val bobId = UUID.randomUUID()
        val aliceId = UUID.randomUUID()
        val client1 = "bob1_" + UUID.randomUUID()
        val client2 = "bob2_" + UUID.randomUUID()
        val client3 = "alice3_" + UUID.randomUUID()
        val aliceCl = "alice_" + UUID.randomUUID()
        val aliceCrypto1 = CryptoDatabase(aliceId, storage, "$rootFolder/testMultiDevicePostgres/alice/1")
        val bobCrypto1 = CryptoDatabase(bobId, storage, "$rootFolder/testMultiDevicePostgres/bob/1")
        val bobCrypto2 = CryptoDatabase(bobId, storage, "$rootFolder/testMultiDevicePostgres/bob/2")
        val api = DummyAPI()
        api.addDevice(bobId, client1, bobCrypto1.box().newLastPreKey())
        api.addDevice(bobId, client2, bobCrypto2.box().newLastPreKey())
        api.addDevice(aliceId, client3, aliceCrypto1.box().newLastPreKey())
        val aliceCrypto = CryptoDatabase(aliceId, storage, "$rootFolder/testMultiDevicePostgres/alice")
        val state = NewBot()
        state.id = aliceId
        state.client = aliceCl
        val aliceClient = WireClientImp(api, aliceCrypto, state, null)
        for (i in 0..9) {
            val text = "Hello Bob, This is Alice!"
            aliceClient.send(MessageText(text))
            val msg: OtrMessage? = api.getMsg()
            val sender: String = msg.getSender()
            val cipher1: String = msg.get(bobId, client1)
            val decrypt: String = bobCrypto1.decrypt(aliceId, sender, cipher1)
            val s1 = getText(decrypt)
            assert(text == s1)
            val cipher2: String = msg.get(bobId, client2)
            val decrypt2: String = bobCrypto2.decrypt(aliceId, sender, cipher2)
            val s2 = getText(decrypt2)
            assert(text == s2)
            val cipher3: String = msg.get(aliceId, client3)
            val decrypt3: String = aliceCrypto1.decrypt(aliceId, sender, cipher3)
            val s3 = getText(decrypt3)
            assert(text == s3)
        }
    }

    @Throws(InvalidProtocolBufferException::class)
    private fun getText(decrypt: String): String {
        val decoded = Base64.getDecoder().decode(decrypt)
        val genericMessage = GenericMessage.parseFrom(decoded)
        return genericMessage.text.content
    }
}