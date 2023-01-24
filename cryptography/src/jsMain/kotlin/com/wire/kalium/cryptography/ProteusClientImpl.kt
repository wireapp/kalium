package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.cryptography.externals.Cryptobox
import com.wire.kalium.cryptography.externals.IdentityKey
import com.wire.kalium.cryptography.externals.MemoryEngine
import com.wire.kalium.cryptography.externals.PreKeyBundle
import io.ktor.util.InternalAPI
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.Uint8Array
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
actual class ProteusClientImpl actual constructor(
    rootDir: String,
    databaseKey: ProteusDBSecret?,
    defaultContext: CoroutineContext,
    ioContext: CoroutineContext
) : ProteusClient {

    private lateinit var box: Cryptobox

    override fun clearLocalFiles(): Boolean {
        TODO("Not yet implemented")
    }

    override fun needsMigration(): Boolean {
        return false
    }

    override suspend fun openOrCreate() {
        val engine = MemoryEngine()
        engine.init("in-memory").await()

        box = Cryptobox(engine)
        box.create().await()
    }

    override suspend fun openOrError() {
        openOrCreate() // JS cryptobox is in-memory only
    }

    override fun getIdentity(): ByteArray {
        val encodedIdentity = box.getIdentity().serialise()
        return Int8Array(encodedIdentity).unsafeCast<ByteArray>()
    }

    override fun getLocalFingerprint(): ByteArray {
        return box.identity.public_key.fingerprint().encodeToByteArray()
    }

    override suspend fun newPreKeys(
        from: Int,
        count: Int
    ): ArrayList<PreKeyCrypto> {
        val preKeys = box.new_prekeys(from, count).await()
        return preKeys.map { toPreKey(box.getIdentity().public_key, it) } as ArrayList<PreKeyCrypto>
    }

    override suspend fun newLastPreKey(): PreKeyCrypto {
        val preKey = box.lastResortPreKey
        if (preKey != null) {
            return toPreKey(box.getIdentity().public_key, preKey)
        } else {
            throw ProteusException("Local identity doesn't exist", ProteusException.Code.UNKNOWN_ERROR)
        }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean = try {
        box.session_load(sessionId.value).await()
        true
        // TODO check the internals of cryptobox.js to see what happens if the session doesn't exist
    } catch (e: Exception) {
        false
    }

    @OptIn(InternalAPI::class)
    override suspend fun createSession(
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ) {
        val preKeyBundle = preKeyCrypto.encodedData.decodeBase64Bytes()
        box.session_from_prekey(sessionId.value, preKeyBundle.toArrayBuffer()).await()
    }

    override suspend fun decrypt(
        message: ByteArray,
        sessionId: CryptoSessionId
    ): ByteArray {
        val decryptedMessage = box.decrypt(sessionId.value, message.toArrayBuffer()).await()
        return Int8Array(decryptedMessage.buffer).unsafeCast<ByteArray>()
    }

    override suspend fun encrypt(
        message: ByteArray,
        sessionId: CryptoSessionId
    ): ByteArray {
        val encryptedMessage = box.encrypt(sessionId.value, payload = message.toUint8Array())
        return Int8Array(encryptedMessage.await()).unsafeCast<ByteArray>()
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) {
        box.session_delete(sessionId.value)
    }

    @OptIn(InternalAPI::class)
    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        val preKeyBundle = preKeyCrypto.encodedData.decodeBase64Bytes()
        val encryptedMessage = box.encrypt(sessionId.value, payload = message.toUint8Array(), preKeyBundle = preKeyBundle.toArrayBuffer())
        return Int8Array(encryptedMessage.await()).unsafeCast<ByteArray>()
    }

    fun ByteArray.toUint8Array(): Uint8Array {
        val int8Array = this.unsafeCast<Int8Array>()
        return Uint8Array(int8Array.buffer)
    }

    fun ByteArray.toArrayBuffer(): ArrayBuffer {
        val int8Array = this.unsafeCast<Int8Array>()
        return int8Array.buffer
    }

    companion object {
        @OptIn(InternalAPI::class)
        private fun toPreKey(localIdentityKey: IdentityKey, preKey: com.wire.kalium.cryptography.externals.PreKey): PreKeyCrypto {
            val preKeyBundle = PreKeyBundle(localIdentityKey, preKey)
            val encodedData = Uint8Array(preKeyBundle.serialise()).unsafeCast<ByteArray>().encodeBase64()
            return PreKeyCrypto(preKey.key_id.toInt(), encodedData)
        }
    }
}
