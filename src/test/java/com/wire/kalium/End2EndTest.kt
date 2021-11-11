//package com.wire.kalium
//
//import com.google.protobuf.InvalidProtocolBufferException
//import com.waz.model.Messages.GenericMessage
//import com.wire.helium.helpers.DummyAPI
//import com.wire.helium.helpers.MemStorage
//import com.wire.helium.helpers.Util.deleteDir
//import com.wire.kalium.assets.MessageText
//import com.wire.kalium.backend.models.NewBot
//import com.wire.kalium.crypto.CryptoFile
//import com.wire.kalium.helium.WireClientImp
//import com.wire.kalium.models.otr.OtrMessage
//import org.junit.jupiter.api.AfterEach
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
//import java.io.IOException
//import java.util.*
//
//class End2EndTest {
//    private var rootFolder: String? = null
//
//    @BeforeEach
//    fun beforeEach() {
//        rootFolder = "helium-unit-test-" + UUID.randomUUID()
//    }
//
//    @AfterEach
//    @Throws(IOException::class)
//    fun afterEach() {
//        deleteDir(rootFolder)
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testAliceToAlice() {
//        val aliceId = UUID.randomUUID()
//        val client1 = "alice1_" + UUID.randomUUID()
//        val state = NewBot(id = aliceId, client = aliceId.toString(), token = null)
//        val aliceCrypto = CryptoFile("$rootFolder/testAliceToAlice/1", aliceId)
//        val aliceCrypto1 = CryptoFile("$rootFolder/testAliceToAlice/2", aliceId)
//        val api = DummyAPI()
//        api.addDevice(aliceId, client1, aliceCrypto1.box().newLastPreKey())
//        val aliceClient = WireClientImp(api, aliceCrypto, state, null)
//        for (i in 0..9) {
//            val text = "Hello Alice, This is Alice!"
//            aliceClient.send(MessageText(text))
//            val msg: OtrMessage? = api.getMsg()
//            val cipher1: String = msg.get(aliceId, client1)
//            val decrypt: String = aliceCrypto1.decrypt(aliceId, msg.getSender(), cipher1)
//            val s1 = getText(decrypt)
//            assert(text == s1)
//        }
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testAliceToBob() {
//        val bobId = UUID.randomUUID()
//        val aliceId = UUID.randomUUID()
//        val client1 = "bob1"
//        val storage = MemStorage()
//        val aliceCrypto = CryptoFile("$rootFolder/testAliceToBob", aliceId)
//        val bobCrypto = CryptoFile("$rootFolder/testAliceToBob", bobId)
//        val api = DummyAPI()
//        api.addDevice(bobId, client1, bobCrypto.box().newLastPreKey())
//        val state = NewBot(id = aliceId, client = "alice1")
//        val aliceClient = WireClientImp(api, aliceCrypto, state, null)
//        for (i in 0..9) {
//            val text = "Hello Bob, This is Alice!"
//            aliceClient.send(MessageText(text))
//            val msg: OtrMessage? = api.getMsg()
//            val cipher1: String = msg.get(bobId, client1)
//            val decrypt: String = bobCrypto.decrypt(aliceId, msg.getSender(), cipher1)
//            val s1 = getText(decrypt)
//            assert(text == s1)
//        }
//    }
//
//    @Test
//    @Throws(Exception::class)
//    fun testMultiDevicePostgres() {
//        val bobId = UUID.randomUUID()
//        val aliceId = UUID.randomUUID()
//        val client1 = "bob1_" + UUID.randomUUID()
//        val client2 = "bob2_" + UUID.randomUUID()
//        val client3 = "alice3_" + UUID.randomUUID()
//        val aliceCl = "alice_" + UUID.randomUUID()
//        val aliceCrypto1 = CryptoFile("$rootFolder/testMultiDevicePostgres/alice/1", aliceId)
//        val bobCrypto1 = CryptoFile("$rootFolder/testMultiDevicePostgres/bob/1", bobId)
//        val bobCrypto2 = CryptoFile("$rootFolder/testMultiDevicePostgres/bob/2", bobId)
//        val api = DummyAPI()
//        api.addDevice(bobId, client1, bobCrypto1.box().newLastPreKey())
//        api.addDevice(bobId, client2, bobCrypto2.box().newLastPreKey())
//        api.addDevice(aliceId, client3, aliceCrypto1.box().newLastPreKey())
//        val aliceCrypto = CryptoFile("$rootFolder/testMultiDevicePostgres/alice", aliceId)
//        val state = NewBot(id = aliceId, client = aliceCl)
//        val aliceClient = WireClientImp(api, aliceCrypto, state, null)
//        for (i in 0..9) {
//            val text = "Hello Bob, This is Alice!"
//            aliceClient.send(MessageText(text))
//            val msg: OtrMessage? = api.getMsg()
//            val sender: String = msg.getSender()
//            val cipher1: String = msg.get(bobId, client1)
//            val decrypt: String = bobCrypto1.decrypt(aliceId, sender, cipher1)
//            val s1 = getText(decrypt)
//            assert(text == s1)
//            val cipher2: String = msg.get(bobId, client2)
//            val decrypt2: String = bobCrypto2.decrypt(aliceId, sender, cipher2)
//            val s2 = getText(decrypt2)
//            assert(text == s2)
//            val cipher3: String = msg.get(aliceId, client3)
//            val decrypt3: String = aliceCrypto1.decrypt(aliceId, sender, cipher3)
//            val s3 = getText(decrypt3)
//            assert(text == s3)
//        }
//    }
//
//    @Throws(InvalidProtocolBufferException::class)
//    private fun getText(decrypt: String): String {
//        val decoded = Base64.getDecoder().decode(decrypt)
//        val genericMessage = GenericMessage.parseFrom(decoded)
//        return genericMessage.text.content
//    }
//}
