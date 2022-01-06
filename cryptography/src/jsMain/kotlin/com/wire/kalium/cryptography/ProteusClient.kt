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

actual class ProteusClient actual constructor(rootDir: String, userId: String) {

    private val userId: String
    private lateinit var box: Cryptobox

    init {
        this.userId = userId
    }

    actual suspend fun open() {
        val engine = MemoryEngine()
        engine.init(userId).await()

        box = Cryptobox(engine)
        box.create().await()
    }

    actual fun close() {}

    actual fun getIdentity(): ByteArray {
        val encodedIdentity = box.getIdentity().serialise()
        return Int8Array(encodedIdentity).unsafeCast<ByteArray>()
    }

    actual fun getLocalFingerprint(): ByteArray {
        return box.identity.public_key.fingerprint().encodeToByteArray()
    }

    actual suspend fun newPreKeys(
        from: Int,
        count: Int
    ): ArrayList<PreKey> {
        val preKeys = box.new_prekeys(from, count).await()
        return preKeys.map { toPreKey(box.getIdentity().public_key, it) } as ArrayList<PreKey>
    }

    actual fun newLastPreKey(): PreKey {
        val preKey = box.lastResortPreKey
        if (preKey != null) {
            return toPreKey(box.getIdentity().public_key, preKey)
        } else {
            throw ProteusException("Local identity doesn't exist", ProteusException.Code.UNKNOWN_ERROR)
        }
    }

    @OptIn(InternalAPI::class)
    actual suspend fun createSession(
        preKey: PreKey,
        sessionId: CryptoSessionId
    ) {
        val preKeyBundle = preKey.encodedData.decodeBase64Bytes()
        box.session_from_prekey(sessionId.value, preKeyBundle.toArrayBuffer()).await()
    }

    actual suspend fun decrypt(
        message: ByteArray,
        sessionId: CryptoSessionId
    ): ByteArray {
        val decryptedMessage = box.decrypt(sessionId.value, message.toArrayBuffer()).await()
        return Int8Array(decryptedMessage.buffer).unsafeCast<ByteArray>()
    }

    actual suspend fun encrypt(
        message: ByteArray,
        sessionId: CryptoSessionId
    ): ByteArray? {
        val encryptedMessage = box.encrypt(sessionId.value, payload = message.toUint8Array())
        return Int8Array(encryptedMessage.await()).unsafeCast<ByteArray>()
    }

    @OptIn(InternalAPI::class)
    actual suspend fun encryptWithPreKey(
        message: ByteArray,
        preKey: PreKey,
        sessionId: CryptoSessionId
    ): ByteArray {
        val preKeyBundle = preKey.encodedData.decodeBase64Bytes()
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
        private fun toPreKey(localIdentityKey: IdentityKey, preKey: com.wire.kalium.cryptography.externals.PreKey): PreKey {
            val preKeyBundle = PreKeyBundle(localIdentityKey, preKey)
            val encodedData = Uint8Array(preKeyBundle.serialise()).unsafeCast<ByteArray>().encodeBase64()
            return PreKey(preKey.key_id.toInt(), encodedData)
        }
    }
}
