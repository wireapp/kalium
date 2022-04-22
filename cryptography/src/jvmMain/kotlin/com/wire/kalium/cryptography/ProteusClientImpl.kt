package com.wire.kalium.cryptography

import com.wire.bots.cryptobox.CryptoBox
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.io.File
import java.util.Base64

actual class ProteusClientImpl actual constructor(rootDir: String) : ProteusClient {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = rootDir
    }

    override fun clearLocalFiles(): Boolean {
        box.close()
        return File(path).deleteRecursively()
    }

    override suspend fun open() {
        val directory = File(path)
        box = wrapException {
            directory.mkdirs()
            CryptoBox.open(path)
        }
    }

    override fun getIdentity(): ByteArray {
        return wrapException { box.identity }
    }

    override fun getLocalFingerprint(): ByteArray {
        return wrapException { box.localFingerprint }
    }

    override fun newLastPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKeyCrypto> }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        return wrapException { box.doesSessionExist(sessionId.value) }
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { box.encryptFromPreKeys(sessionId.value, toPreKey(preKeyCrypto), ByteArray(0)) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException { box.decrypt(sessionId.value, message) }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException { box.encryptFromSession(sessionId.value, message) }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException { box.encryptFromPreKeys(sessionId.value, toPreKey(preKeyCrypto), message) }
    }

    private fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            throw ProteusException(e.message, e.code.ordinal)
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        }
    }

    companion object {
        private fun toPreKey(preKeyCrypto: PreKeyCrypto): com.wire.bots.cryptobox.PreKey =
            com.wire.bots.cryptobox.PreKey(preKeyCrypto.id, Base64.getDecoder().decode(preKeyCrypto.encodedData))

        private fun toPreKey(preKey: com.wire.bots.cryptobox.PreKey): PreKeyCrypto =
            PreKeyCrypto(preKey.id, Base64.getEncoder().encodeToString(preKey.data))
    }

}
