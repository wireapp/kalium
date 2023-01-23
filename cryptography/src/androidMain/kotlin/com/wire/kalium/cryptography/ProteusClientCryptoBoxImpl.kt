package com.wire.kalium.cryptography

import android.util.Base64
import com.wire.cryptobox.CryptoBox
import com.wire.cryptobox.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
class ProteusClientCryptoBoxImpl constructor(
    rootDir: String,
    private val ioContext: CoroutineContext,
    private val defaultContext: CoroutineContext
) : ProteusClient {

    private val path: String
    private lateinit var box: CryptoBox

    init {
        path = rootDir
    }

    override fun clearLocalFiles(): Boolean {
        box.close()
        return File(path).deleteRecursively()
    }

    override fun needsMigration(): Boolean {
        return false
    }

    /**
     * Create the crypto files if missing and call box.open
     * this must be called only one time
     */
    override suspend fun openOrCreate() {
        val directory = File(path)
        box = wrapException {
            directory.mkdirs()
            CryptoBox.open(path)
        }
    }

    /**
     * open the crypto box if and only if the local files are already created
     * this must be called only one time
     */
    override suspend fun openOrError() = withContext(ioContext) {
        val directory = File(path)
        if (directory.exists()) {
            box = wrapException {
                directory.mkdirs()
                CryptoBox.open(path)
            }
        } else {
            throw ProteusException("Local files were not found in: ${directory.absolutePath}", ProteusException.Code.LOCAL_FILES_NOT_FOUND)
        }
    }

    override fun getIdentity(): ByteArray = wrapException { box.copyIdentity() }

    override fun getLocalFingerprint(): ByteArray = wrapException { box.localFingerprint }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> =
        wrapException { box.newPreKeys(from, count).map { toPreKey(it) } as ArrayList<PreKeyCrypto> }

    override fun newLastPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(box.newLastPreKey()) }
    }

    // TODO: this function calls the native function session_load which does open the session file and
    //  parse it content we can consider changing it to a simple check if the session file exists on the local storage or not
    //  or rename it to doesValidSessionExist
    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean =
        withContext(ioContext) {
            box.tryGetSession(sessionId.value)?.let { true } ?: false
        }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        withContext(ioContext) {
            wrapException { box.initSessionFromPreKey(sessionId.value, toPreKey(preKeyCrypto)) }
        }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray = withContext(defaultContext) {
        val session = box.tryGetSession(sessionId.value)
        wrapException {
            if (session != null) {
                val decryptedMessage = session.decrypt(message)
                session.save()
                decryptedMessage
            } else {
                val result = box.initSessionFromMessage(sessionId.value, message)
                result.session.save()
                result.message
            }
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray = withContext(defaultContext) {
        wrapException {
            val session = box.getSession(sessionId.value)
            val encryptedMessage = session.encrypt(message)
            session.save()
            encryptedMessage
        }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray = withContext(defaultContext) {
        wrapException {
            val session = box.initSessionFromPreKey(sessionId.value, toPreKey(preKeyCrypto))
            val encryptedMessage = session.encrypt(message)
            session.save()
            encryptedMessage
        }
    }

    override suspend fun deleteSession(sessionId: CryptoSessionId) {
        withContext(defaultContext) {
            wrapException {
                box.deleteSession(sessionId.value)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            throw ProteusException(e.message, fromCryptoException(e))
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        }
    }

        @Suppress("ComplexMethod")
        private fun fromCryptoException(e: CryptoException): ProteusException.Code {
            return when (e.code) {
                CryptoException.Code.SESSION_NOT_FOUND -> ProteusException.Code.SESSION_NOT_FOUND
                CryptoException.Code.REMOTE_IDENTITY_CHANGED -> ProteusException.Code.REMOTE_IDENTITY_CHANGED
                CryptoException.Code.INVALID_SIGNATURE -> ProteusException.Code.INVALID_SIGNATURE
                CryptoException.Code.INVALID_MESSAGE -> ProteusException.Code.INVALID_MESSAGE
                CryptoException.Code.DUPLICATE_MESSAGE -> ProteusException.Code.DUPLICATE_MESSAGE
                CryptoException.Code.TOO_DISTANT_FUTURE -> ProteusException.Code.TOO_DISTANT_FUTURE
                CryptoException.Code.OUTDATED_MESSAGE -> ProteusException.Code.OUTDATED_MESSAGE
                CryptoException.Code.DECODE_ERROR -> ProteusException.Code.DECODE_ERROR
                CryptoException.Code.STORAGE_ERROR -> ProteusException.Code.STORAGE_ERROR
                CryptoException.Code.IDENTITY_ERROR -> ProteusException.Code.IDENTITY_ERROR
                CryptoException.Code.PREKEY_NOT_FOUND -> ProteusException.Code.PREKEY_NOT_FOUND
                CryptoException.Code.PANIC -> ProteusException.Code.PANIC
                CryptoException.Code.INIT_ERROR -> ProteusException.Code.UNKNOWN_ERROR
                CryptoException.Code.DEGENERATED_KEY -> ProteusException.Code.UNKNOWN_ERROR
                CryptoException.Code.INVALID_STRING -> ProteusException.Code.UNKNOWN_ERROR
                CryptoException.Code.UNKNOWN_ERROR -> ProteusException.Code.UNKNOWN_ERROR
                else -> ProteusException.Code.UNKNOWN_ERROR
            }
        }

        companion object {
            private fun toPreKey(preKey: PreKeyCrypto): com.wire.cryptobox.PreKey =
                com.wire.cryptobox.PreKey(preKey.id, Base64.decode(preKey.encodedData, Base64.NO_WRAP))

            private fun toPreKey(preKey: com.wire.cryptobox.PreKey): PreKeyCrypto =
                PreKeyCrypto(preKey.id, Base64.encodeToString(preKey.data, Base64.NO_WRAP))
        }
}
