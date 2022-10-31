package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.ionspin.kotlin.crypto.util.toHexString
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.Backup
import com.wire.kalium.cryptography.kaliumLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.nextUBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChaCha20UtilsTest {

    @Test
    @OptIn(ExperimentalUnsignedTypes::class)
    fun `given a backup object, when providing the ChaCha20 secret key, it is a valid one`() = runTest {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val password = "some password"
        val userId = CryptoUserID("some-user-id", "some-domain.com")
        val passphrase = Backup.Passphrase(password, userId)
        val backup = Backup(salt.toUByteArray(), passphrase)
        val chachakey = backup.provideChaCha20Key()
        kaliumLogger.d("ChaCha20 key: ${chachakey.toHexString()}")
        assertTrue(chachakey.isNotEmpty())
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `test chacha20`() = runTest {
        val fakeData = "Some file data to be encrypted".toByteArray()
        val arrangement = Arrangement().arrange()

        with(arrangement) {
            val inputPath = "$rootPath/test-data.txt".toPath()
            val encryptedOutputPath = "$rootPath/test-data.cc20".toPath()
            fakeFileSystem.write(inputPath) {
                write(fakeData)
            }
            val decryptedOutputPath = "$rootPath/test-data-decrypted.txt".toPath()
            val inputDataSource = fakeFileSystem.source(inputPath)
            val encryptedOutputSink = fakeFileSystem.sink(encryptedOutputPath)
            val decryptedDataOutputSink = fakeFileSystem.sink(decryptedOutputPath)
            val salt = Random(0).nextUBytes(crypto_pwhash_SALTBYTES)
            val password = "some password"
            val userId = CryptoUserID("some-user-id", "some-domain.com")
            val passphrase = Backup.Passphrase(password, userId)
            val backup = Backup(salt, passphrase)
            val saltDecoded = salt.toByteArray().decodeToString()
            kaliumLogger.d("Salt: $saltDecoded")

            val outputSize = ChaCha20Utils().encryptBackupFile(inputDataSource, encryptedOutputSink, backup)

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val decryptedDataSize = ChaCha20Utils().decryptFile(encryptedDataSource, decryptedDataOutputSink, passphrase)

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }

            assertEquals(decryptedOutputContent.decodeToString(), fakeData.decodeToString())
            assertTrue(outputSize > 0)
            assertEquals(decryptedDataSize, fakeData.size.toLong())
        }
    }

    class Arrangement {
        val fakeFileSystem = FakeFileSystem()
        val rootPath = "/Users/me".toPath()

        init {
            fakeFileSystem.createDirectories(rootPath)
        }

        fun arrange(): Arrangement {
            return this
        }
    }
}
