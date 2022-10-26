package com.wire.kalium.cryptography

import com.wire.crypto.CoreCrypto
import com.wire.crypto.CryptoException
import com.wire.kalium.cryptography.exceptions.ProteusException
import io.ktor.util.decodeBase64Bytes
import io.ktor.util.encodeBase64
import java.io.File

@Suppress("TooManyFunctions")
class ProteusClientCoreCryptoImpl constructor(private val rootDir: String, private val databaseKey: ProteusDBSecret) : ProteusClient {

    private val path: String = "$rootDir/$KEYSTORE_NAME"
    private lateinit var coreCrypto: CoreCrypto

    override fun clearLocalFiles(): Boolean {
        coreCrypto.close()
        return File(path).deleteRecursively()
    }

    override suspend fun openOrCreate() {
        coreCrypto = wrapException {
            File(rootDir).mkdirs()
            // TODO client ID is not relevant for proteus but must be provided atm
            val coreCrypto = CoreCrypto(path, databaseKey.value, CLIENT_ID.toString(), null)
            migrateFromCryptoBoxIfNecessary(coreCrypto)
            coreCrypto.proteusInit()
            coreCrypto
        }
    }

    override suspend fun openOrError() {
        val directory = File(rootDir)
        if (directory.exists()) {
            coreCrypto = wrapException {
                // TODO client ID is not relevant for proteus but must be provided atm
                val coreCrypto = CoreCrypto(path, databaseKey.value, CLIENT_ID.toString(), null)
                migrateFromCryptoBoxIfNecessary(coreCrypto)
                coreCrypto.proteusInit()
                coreCrypto
            }
        } else {
            throw ProteusException("Local files were not found", ProteusException.Code.LOCAL_FILES_NOT_FOUND)
        }
    }

    private fun cryptoBoxFilesExists(): Boolean =
        CRYPTO_BOX_FILES.any {
            File(rootDir).resolve(it).exists()
        }

    private fun deleteCryptoBoxFiles(): Boolean =
        CRYPTO_BOX_FILES.fold(true) { acc, file ->
            acc && File(rootDir).resolve(file).deleteRecursively()
        }

    private fun migrateFromCryptoBoxIfNecessary(coreCrypto: CoreCrypto) {
        if (cryptoBoxFilesExists()) {
            migrateFromCryptoBox(coreCrypto)
        }
    }

    private fun migrateFromCryptoBox(coreCrypto: CoreCrypto) {
        kaliumLogger.i("migrating from crypto box at: $rootDir")
        coreCrypto.proteusCryptoboxMigrate(rootDir)
        kaliumLogger.i("migration successful")

        if (deleteCryptoBoxFiles()) {
            kaliumLogger.i("successfully deleted old crypto box files")
        } else {
            kaliumLogger.e("Failed to deleted old crypto box files at $rootDir")
        }
    }

    override fun getIdentity(): ByteArray {
        return ByteArray(0)
    }

    override fun getLocalFingerprint(): ByteArray {
        return wrapException { coreCrypto.proteusFingerprint().toByteArray() }
    }

    override suspend fun newPreKeys(from: Int, count: Int): ArrayList<PreKeyCrypto> {
        return wrapException {
            from.until(from + count).map {
                toPreKey(it, toByteArray(coreCrypto.proteusNewPrekey(it.toUShort())))
            } as ArrayList<PreKeyCrypto>
        }
    }

    override fun newLastPreKey(): PreKeyCrypto {
        return wrapException { toPreKey(UShort.MAX_VALUE.toInt(), toByteArray(coreCrypto.proteusNewPrekey(UShort.MAX_VALUE))) }
    }

    override suspend fun doesSessionExist(sessionId: CryptoSessionId): Boolean {
        // TODO hack until we have the proteusSessionExists API
        try {
            coreCrypto.proteusDecrypt(sessionId.value, toUByteList(""))
        } catch (e: CryptoException) {
            if (e.message == "Couldn't find conversation") {
                return false
            }
        }

        return true
    }

    override suspend fun createSession(preKeyCrypto: PreKeyCrypto, sessionId: CryptoSessionId) {
        wrapException { coreCrypto.proteusSessionFromPrekey(sessionId.value, toUByteList(preKeyCrypto.encodedData.decodeBase64Bytes())) }
    }

    override suspend fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        val sessionExists = doesSessionExist(sessionId)

        return wrapException {
            if (sessionExists) {
                val decryptedMessage = toByteArray(coreCrypto.proteusDecrypt(sessionId.value, toUByteList(message)))
                coreCrypto.proteusSessionSave(sessionId.value)
                decryptedMessage
            } else {
                val decryptedMessage = toByteArray(coreCrypto.proteusSessionFromMessage(sessionId.value, toUByteList(message)))
                coreCrypto.proteusSessionSave(sessionId.value)
                decryptedMessage
            }
        }
    }

    override suspend fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        return wrapException {
            val encryptedMessage = toByteArray(coreCrypto.proteusEncrypt(sessionId.value, toUByteList(message)))
            coreCrypto.proteusSessionSave(sessionId.value)
            encryptedMessage
        }
    }

    override suspend fun encryptWithPreKey(
        message: ByteArray,
        preKeyCrypto: PreKeyCrypto,
        sessionId: CryptoSessionId
    ): ByteArray {
        return wrapException {
            coreCrypto.proteusSessionFromPrekey(sessionId.value, toUByteList(preKeyCrypto.encodedData.decodeBase64Bytes()))
            val encryptedMessage = toByteArray(coreCrypto.proteusEncrypt(sessionId.value, toUByteList(message)))
            coreCrypto.proteusSessionSave(sessionId.value)
            encryptedMessage
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> wrapException(b: () -> T): T {
        try {
            return b()
        } catch (e: CryptoException) {
            // TODO underlying proteus error is not exposed atm
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        } catch (e: Exception) {
            throw ProteusException(e.message, ProteusException.Code.UNKNOWN_ERROR)
        }
    }

    private companion object {

        fun toUByteList(value: ByteArray): List<UByte> = value.asUByteArray().asList()
        fun toUByteList(value: String): List<UByte> = value.encodeToByteArray().asUByteArray().asList()
        fun toByteArray(value: List<UByte>) = value.toUByteArray().asByteArray()
        fun toPreKey(id: Int, data: ByteArray): PreKeyCrypto =
            PreKeyCrypto(id, data.encodeBase64())

        val CLIENT_ID = CryptoQualifiedID("2380b74d-f321-4c11-b7dd-552a74502e30", "wire.com")
        val CRYPTO_BOX_FILES = listOf("identities", "prekeys", "sessions", "version")
        const val KEYSTORE_NAME = "keystore"
    }
}
