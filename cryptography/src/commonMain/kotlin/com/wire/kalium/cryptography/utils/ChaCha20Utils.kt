package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.LibsodiumInitializer
import com.ionspin.kotlin.crypto.pwhash.PasswordHash
import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_ALG_DEFAULT
import com.ionspin.kotlin.crypto.secretstream.SecretStream
import com.wire.kalium.cryptography.backup.BackupPassphrase
import com.wire.kalium.cryptography.kaliumLogger
import io.ktor.utils.io.core.*
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer

@OptIn(ExperimentalUnsignedTypes::class)
internal class ChaCha20Utils {

    suspend fun encryptBackupFile(
        backupDataSource: Source,
        outputSink: Sink,
        salt: PlainData,
        userId: String,
        passphrase: BackupPassphrase
    ): Long {

        initializeLibsodiumIfNeeded()

        var encryptedDataSize = 0L
        try {
            val magicNumber = "WBUX".encodeToByteArray()
            val extraGap = 0x00.toString().toByteArray()
            val version = "03".toByteArray()

            val chaCha20Key = PasswordHash.pwhash(
                passphrase.password.length,
                passphrase.password,
                salt.data.toUByteArray(),
                OPSLIMIT_INTERACTIVE_VALUE,
                MEMLIMIT_INTERACTIVE_VALUE,
                crypto_pwhash_ALG_DEFAULT
            )

            val hashedUserId = PasswordHash.pwhash(
                userId.length,
                userId,
                salt.data.toUByteArray(),
                OPSLIMIT_INTERACTIVE_VALUE,
                MEMLIMIT_INTERACTIVE_VALUE,
                crypto_pwhash_ALG_DEFAULT
            )

            val stateAndHeader = SecretStream.xChaCha20Poly1305InitPush(chaCha20Key)
            val state = stateAndHeader.state
            val header = stateAndHeader.header

            // We append all the metadata to the beginning of the file data
            val outputBuffer = outputSink.buffer()
            outputBuffer.write(magicNumber)
            outputBuffer.write(extraGap)
            outputBuffer.write(version)
            outputBuffer.write(salt.data)
            outputBuffer.write(hashedUserId.toByteArray())
            outputBuffer.flush()

//             outputBuffer.chaCha20CipherSink(cipher).buffer().use { cipheredSink ->
//                 val contentBuffer = Buffer()
//                 var byteCount: Long
//                 while (backupDataSource.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
//                     encryptedDataSize += byteCount
//                     cipheredSink.write(contentBuffer, byteCount)
//                     cipheredSink.flush()
//                 }
//             }
        } catch (e: Exception) {
            kaliumLogger.e("There was an error while encrypting the backup data with ChaCha20:\n $e}")
        } finally {
            backupDataSource.close()
            outputSink.close()
        }
        return encryptedDataSize
    }

    private suspend fun initializeLibsodiumIfNeeded() {
        if (!LibsodiumInitializer.isInitialized()) {
            LibsodiumInitializer.initialize()
        }
    }

    @Throws(Exception::class)
    fun decryptFile(
        encryptedDataSource: Source,
        outputSink: Sink,
        key: ChaCha20Key,
        salt: PlainData
    ): Long {
        var decryptedDataSize = 0L
//         try {
//             val cipher = Cipher.getInstance(KEY_ALGORITHM_CONFIGURATION)
//             val param = IvParameterSpec(salt.data)
//             val secretChaCha20Key = SecretKeySpec(key.data, KEY_ALGORITHM)
//
//             cipher.init(Cipher.DECRYPT_MODE, secretChaCha20Key, param)
//
//             // we read all the metadata from the beginning of the file data
//             val buffer = Buffer()
//
//             // TODO: Check valid magic number
//             encryptedDataSource.read(buffer, MAGIC_NUMBER_SIZE)
//             val magicNumber = buffer.readString(MAGIC_NUMBER_SIZE, Charsets.UTF_8)
//             encryptedDataSource.read(buffer, EXTRA_GAP_SIZE)
//             buffer.clear()
//
//             // TODO: Check valid version
//             encryptedDataSource.read(buffer, BACKUP_VERSION_SIZE)
//             val version = buffer.readString(BACKUP_VERSION_SIZE, Charsets.UTF_8)
//
//             // Check random salt matches with the one used to encrypt the file
//             encryptedDataSource.read(buffer, SALT_SIZE)
//             val decryptedSalt = buffer.readByteArray(SALT_SIZE)
//             check(salt.data.contentEquals(decryptedSalt)) {
//                 "The salt used to decrypt the backup data is not the same as the one used to encrypt it"
//             }
//
//             encryptedDataSource.read(buffer, HASHED_USER_ID_SIZE)
//             val hashedUserId = buffer.readByteArray(HASHED_USER_ID_SIZE)
//
//             encryptedDataSource.chaCha20CipherSource(cipher).buffer().use { bufferedSource ->
//                 val source = bufferedSource.inputStream().source()
//                 val contentBuffer = Buffer()
//                 var byteCount: Long
//                 while (source.read(contentBuffer, BUFFER_SIZE).also { byteCount = it } != -1L) {
//                     decryptedDataSize += byteCount
//                     outputSink.write(contentBuffer, byteCount)
//                     outputSink.flush()
//                 }
//                 source.close()
//             }
//         } catch (e: Exception) {
//             kaliumLogger.e("There was an error while decrypting the backup data with ChaCha20:\n $e}")
//         } finally {
//             encryptedDataSource.close()
//             outputSink.close()
//         }
        return decryptedDataSize
    }

    companion object {
        private const val OPSLIMIT_INTERACTIVE_VALUE = 4UL
        private const val MEMLIMIT_INTERACTIVE_VALUE = 33554432
    }
}
