package com.wire.kalium.cryptography

import com.wire.bots.cryptobox.CryptoBox
import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.util.ArrayList
import java.util.Base64
import java.util.UUID

actual class ProteusClient(rootDir: String, userId: String) {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = String.format("%s/%s", rootDir, userId)
    }

    actual fun open() {
        box = wrapException { CryptoBox.open(path) }
    }

    actual fun close() {
        box.lose()
    }

    actual fun getIdentity(): ByteArray {
        return wrapException { box.identity }
    }

    actual fun getLocalFingerprint(): ByteArray {
        return wrapException { box.localFingerprint }
    }

    actual fun newLastPreKey(): PreKey {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    actual fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKey> }
    }

    actual fun getSession(sessionId: CryptoSessionId): ProteusSession? {
        return box.let { ProteusSession(it, sessionId) }
    }

    actual fun createSession(
        preKey: PreKey,
        sessionId: CryptoSessionId
    ): ProteusSession {
        return wrapException {
            box.encryptFromPreKeys(sessionId.value, toPreKey(preKey), ByteArray(0))
            ProteusSession(box, sessionId)
        }
    }

    actual fun createSessionAndDecrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException { box.decrypt(sessionId.value, message) }
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

actual class ProteusSession(box: CryptoBox, sessionId: CryptoSessionId) {

    private val box: CryptoBox
    private val sessionId: CryptoSessionId

    init {
        this.box = box
        this.sessionId = sessionId
    }

    actual fun encrypt(data: ByteArray): ByteArray {
        return wrapException { box.encryptFromSession(sessionId.value, data) }
    }

    actual fun decrypt(data: ByteArray): ByteArray {
        return wrapException { box.decrypt(sessionId.value, data) }
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

}
