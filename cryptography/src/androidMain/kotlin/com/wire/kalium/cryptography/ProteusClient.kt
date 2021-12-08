package com.wire.kalium.cryptography

import android.util.Base64
import com.wire.cryptobox.CryptoBox
import com.wire.cryptobox.CryptoException
import com.wire.cryptobox.CryptoSession
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.util.UUID

actual class ProteusSession {

    val session: CryptoSession

    constructor(session: CryptoSession) {
        this.session = session
    }

    actual fun encrypt(data: ByteArray): ByteArray {
        return wrapException { session.encrypt(data) }
    }

    actual fun decrypt(data: ByteArray): ByteArray {
        return wrapException { session.decrypt(data) }
    }

    fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            throw ProteusException(e.message, e.code.ordinal)
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        }
    }
}

actual class ProteusClient {

    val path: String
    var box: CryptoBox? = null

    actual constructor(rootDir: String, userId: String) {
        path = String.format("%s/%s", rootDir, userId)
    }

    actual fun open() {
        box = wrapException { CryptoBox.open(path) }
    }

    actual fun close() {
        box?.close()
    }

    actual fun getIdentity(): ByteArray {
        return wrapException { box!!.copyIdentity() }
    }

    actual fun getLocalFingerprint(): ByteArray {
        return wrapException { box!!.localFingerprint }
    }

    actual fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        return wrapException { box!!.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKey> }
    }

    actual fun newLastPreKey(): PreKey {
        return wrapException { toPreKey(box!!.newLastPreKey()) }
    }

    actual fun getSession(sessionId: CryptoSessionId): ProteusSession? {
        return try {
            ProteusSession(box!!.getSession(sessionId.value))
        } catch (e: Exception){
            null
        }
    }

    actual fun createSession(preKey: PreKey, sessionId: CryptoSessionId): ProteusSession {
        return wrapException { ProteusSession(box!!.initSessionFromPreKey(sessionId.value, toPreKey(preKey))) }
    }

    actual fun createSessionAndDecrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            box!!.getSession(sessionId.value).let {
                it.decrypt(message)
            } ?: run {
                box!!.initSessionFromMessage(sessionId.value, message).message
            }
        }
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
        private fun toPreKey(preKey: PreKey): com.wire.cryptobox.PreKey =
            com.wire.cryptobox.PreKey(preKey.id, Base64.decode(preKey.encodedData, Base64.DEFAULT))

        private fun toPreKey(preKey: com.wire.cryptobox.PreKey): PreKey =
            PreKey(preKey.id, Base64.encodeToString(preKey.data, Base64.DEFAULT))

        private fun createId(userId: UUID?, clientId: String?): String? {
            return String.format("%s_%s", userId, clientId)
        }

        private fun createId(userId: String, clientId: String): String {
            return String.format("%s_%s", userId, clientId)
        }
    }

}
