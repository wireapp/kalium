package com.wire.kalium.cryptography

import com.wire.bots.cryptobox.CryptoBox
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.io.File
import java.util.ArrayList
import java.util.Base64
import java.util.UUID

actual class ProteusClientImpl actual constructor(rootDir: String, userId: String): ProteusClient {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = String.format("%s/%s", rootDir, userId)
    }

    override suspend fun open() {
        val directory = File(path)
        box = wrapException {
            directory.mkdirs()
            CryptoBox.open(path)
        }
    }

    override fun close() {
        box.close()
    }

    override fun getIdentity(): ByteArray {
        return wrapException { box.identity }
    }

    override fun getLocalFingerprint(): ByteArray {
        return wrapException { box.localFingerprint }
    }

    override fun newLastPreKey(): PreKey {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKey> }
    }

    override suspend fun createSession(preKey: PreKey, sessionId: CryptoSessionId) {
        wrapException { box.encryptFromPreKeys(sessionId.value, toPreKey(preKey), ByteArray(0)) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException { box.decrypt(sessionId.value, message) }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray? {
        return wrapException { box.encryptFromSession(sessionId.value, message) }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKey: PreKey,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException { box.encryptFromPreKeys(sessionId.value, toPreKey(preKey), message) }
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
        private fun toPreKey(preKey: PreKey): com.wire.bots.cryptobox.PreKey =
            com.wire.bots.cryptobox.PreKey(preKey.id, Base64.getDecoder().decode(preKey.encodedData))

        private fun toPreKey(preKey: com.wire.bots.cryptobox.PreKey): PreKey =
            PreKey(preKey.id, Base64.getEncoder().encodeToString(preKey.data))

        private fun createId(userId: UUID?, clientId: String?): String? {
            return String.format("%s_%s", userId, clientId)
        }

        private fun createId(userId: String, clientId: String): String {
            return String.format("%s_%s", userId, clientId)
        }
    }

}
