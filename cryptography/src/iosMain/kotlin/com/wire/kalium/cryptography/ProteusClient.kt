package com.wire.kalium.cryptography

import platform.Foundation.*
import platform.darwin.NSObject
import com.wire.WireCryptobox.*

actual class ProteusSession {
    actual  fun hello() {

    }
}

//actual class ProteusClient actual constructor(rootDir: String, userId: String) {
//
//    actual fun open() {
//    }
//
//    actual fun close() {
//    }
//
//    actual fun getIdentity(): ByteArray {
//        ByteArray()
//    }
//
//    actual fun getLocalFingerprint(): ByteArray {
//        ByteArray()
//    }
//
//    actual fun newLastPreKey(): PreKey {
//        com.wire.cryptobox.PreKey()
//    }
//
//    actual fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
//        return wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKey> }
//    }
//
//    actual fun getSession(sessionId: CryptoSessionId): ProteusSession? {
//        return box.let { ProteusSession(it, sessionId) }
//    }
//
//    actual fun createSession(
//        preKey: PreKey,
//        sessionId: CryptoSessionId
//    ): ProteusSession {
//        return wrapException {
//            box.encryptFromPreKeys(sessionId.value, toPreKey(preKey), ByteArray(0))
//            ProteusSession(box, sessionId)
//        }
//    }
//
//    actual fun createSessionAndDecrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
//        return wrapException { box.decrypt(sessionId.value, message) }
//    }
//}
//
//actual class ProteusSession(private val box: CryptoBox, private val sessionId: CryptoSessionId) {
//
//    actual fun encrypt(data: ByteArray): ByteArray {
//        return wrapException { box.encryptFromSession(sessionId.value, data) }
//    }
//
//    actual fun decrypt(data: ByteArray): ByteArray {
//        return wrapException { box.decrypt(sessionId.value, data) }
//    }
//
//    private fun <T> wrapException(b: () -> T): T {
//        try {
//            return b()
//        } catch (e: CryptoException) {
//            throw ProteusException(e.message, e.code.ordinal)
//        } catch (e: Exception) {
//            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
//        }
//    }
//}
