package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.exceptions.ProteusException

actual class ProteusSession {
    @Throws(ProteusException::class)
    actual fun encrypt(data: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun decrypt(data: ByteArray): ByteArray {
        TODO("Not yet implemented")
    }

}

actual class ProteusClient actual constructor(rootDir: String, userId: String) {
    @Throws(ProteusException::class)
    actual fun open() {
    }

    @Throws(ProteusException::class)
    actual fun close() {
    }

    @Throws(ProteusException::class)
    actual fun getIdentity(): ByteArray {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun getLocalFingerprint(): ByteArray {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun newLastPreKey(): PreKey {
        TODO("Not yet implemented")
    }

    actual fun getSession(sessionId: CryptoSessionId): ProteusSession? {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun createSession(
        preKey: PreKey,
        sessionId: CryptoSessionId
    ): ProteusSession {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun createSessionAndDecrypt(
        message: ByteArray,
        sessionId: CryptoSessionId
    ): ByteArray {
        TODO("Not yet implemented")
    }

}
