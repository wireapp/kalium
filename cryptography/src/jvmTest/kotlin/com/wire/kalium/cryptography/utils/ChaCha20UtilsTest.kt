package com.wire.kalium.cryptography.utils

import com.ionspin.kotlin.crypto.pwhash.crypto_pwhash_SALTBYTES
import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.Backup
import com.wire.kalium.cryptography.readBinaryResource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChaCha20UtilsTest {

    @Test
    @OptIn(ExperimentalUnsignedTypes::class)
    fun `salt generated has correct size`() = runTest {
        val salt = generateSalt()

        assertTrue(salt.isNotEmpty())
        assertEquals(salt.size, crypto_pwhash_SALTBYTES)
    }

    @Test
    @OptIn(ExperimentalUnsignedTypes::class)
    fun `given some dummy backup object data, when generating the ChaCha20 secret key, it is a valid one`() = runTest {
        val salt = generateSalt()

        val password = "some password"
        val userId = CryptoUserID("some-user-id", "some-domain.com")
        val passphrase = Backup.Passphrase(password, userId)
        val backup = Backup(salt.toUByteArray(), passphrase)

        val chaCha20Key = backup.generateChaCha20Key()

        assertTrue(chaCha20Key.isNotEmpty())
        assertTrue(chaCha20Key.size == 32)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `given some dummy backup data, when encrypting and decrypting with chacha20, data matches`() = runTest {
        // Given
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
            val salt = generateSalt()
            val password = "some password"
            val userId = CryptoUserID("some-user-id", "some-domain.com")
            val passphrase = Backup.Passphrase(password, userId)
            val backup = Backup(salt, passphrase)

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

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `given some big backup data, when encrypting and decrypting with chacha20, data matches`() = runTest {
        // Given
        val realFileData = readBinaryResource("dummy.pdf")
        val arrangement = Arrangement().arrange()

        with(arrangement) {
            val inputPath = "$rootPath/test-data.txt".toPath()
            val encryptedOutputPath = "$rootPath/test-data.cc20".toPath()
            fakeFileSystem.write(inputPath) {
                write(realFileData)
            }
            val decryptedOutputPath = "$rootPath/test-data-decrypted.txt".toPath()
            val inputDataSource = fakeFileSystem.source(inputPath)
            val encryptedOutputSink = fakeFileSystem.sink(encryptedOutputPath)
            val decryptedDataOutputSink = fakeFileSystem.sink(decryptedOutputPath)
            val salt = generateSalt()
            val password = "some password"
            val userId = CryptoUserID("some-user-id", "some-domain.com")
            val passphrase = Backup.Passphrase(password, userId)
            val backup = Backup(salt, passphrase)

            val outputSize = ChaCha20Utils().encryptBackupFile(inputDataSource, encryptedOutputSink, backup)

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val decryptedDataSize = ChaCha20Utils().decryptFile(encryptedDataSource, decryptedDataOutputSink, passphrase)

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }

            assertTrue(outputSize > 0)
            assertEquals(realFileData.size.toLong(), decryptedDataSize)
            assertTrue(realFileData.contentEquals(decryptedOutputContent))
            assertEquals(realFileData.decodeToString(), decryptedOutputContent.decodeToString())
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
