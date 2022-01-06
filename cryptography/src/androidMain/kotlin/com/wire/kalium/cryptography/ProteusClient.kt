package com.wire.kalium.cryptography

import android.util.Base64
import com.wire.cryptobox.CryptoBox
import com.wire.cryptobox.CryptoException
import com.wire.cryptobox.CryptoSession
import com.wire.kalium.cryptography.exceptions.ProteusException
import java.io.File
import java.util.UUID

actual class ProteusClient actual constructor(rootDir: String, userId: String) {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = String.format("%s/%s", rootDir, userId)
    }

    actual suspend fun open() {
        box = wrapException {
            File(path).mkdirs()
            CryptoBox.open(path)
        }
    }

    actual fun close() {
        box.close()
    }

    actual fun getIdentity(): ByteArray {
        return wrapException { box.copyIdentity() }
    }

    actual fun getLocalFingerprint(): ByteArray {
        return wrapException { box.localFingerprint }
    }

    actual suspend  fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKey> }
    }

    actual fun newLastPreKey(): PreKey {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    actual suspend fun createSession(preKey: PreKey, sessionId: CryptoSessionId) {
        wrapException { box.initSessionFromPreKey(sessionId.value, toPreKey(preKey)) }
    }

    actual suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        val session = box.tryGetSession(sessionId.value)

        return wrapException {
            if (session != null) {
                session.decrypt(message)
            } else {
                box.initSessionFromMessage(sessionId.value, message).message
            }
        }
    }

    actual suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray? {
        return wrapException { box.getSession(sessionId.value)?.encrypt(message) }
    }

    actual suspend fun encryptWithPreKey(
        message: ByteArray,
        preKey: PreKey,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException {
            val session =  box.initSessionFromPreKey(sessionId.value, toPreKey(preKey))
            session.encrypt(message)
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
