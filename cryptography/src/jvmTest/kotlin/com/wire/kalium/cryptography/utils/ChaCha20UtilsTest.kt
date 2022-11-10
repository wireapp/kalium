package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.BackupCoder
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

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `given some passphrase, when generating the ChaCha20 secret key, it is a valid one`() = runTest {

        val password = "some password"
        val passphrase = BackupCoder.Passphrase(password)
        val backupCoder = BackupCoder(CryptoUserID("some user id", "some-domain"), passphrase)
        val header = BackupCoder.Header(
            format = "some format",
            version = "some version",
            salt = UByteArray(16) { 0u },
            hashedUserId = "some hashed user id".toByteArray().toUByteArray(),
            opslimit = 1,
            memlimit = 1
        )

        val chaCha20Key = backupCoder.generateChaCha20Key(header)

        assertTrue(chaCha20Key.isNotEmpty())
        assertTrue(chaCha20Key.size == 32)
    }

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
            val password = "some password"
            val userId = CryptoUserID("some-user-id", "some-domain.com")
            val passphrase = BackupCoder.Passphrase(password)

            val outputSize = ChaCha20Utils().encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val decryptedDataSize = ChaCha20Utils().decryptBackupFile(encryptedDataSource, decryptedDataOutputSink, passphrase, userId)

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }

            assertEquals(fakeData.decodeToString(), decryptedOutputContent.decodeToString())
            assertTrue(outputSize > 0)
            assertEquals(decryptedDataSize, fakeData.size.toLong())
        }
    }

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
            val password = "some password"
            val userId = CryptoUserID("some-user-id", "some-domain.com")
            val passphrase = BackupCoder.Passphrase(password)
            val outputSize = ChaCha20Utils().encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)
            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val decryptedDataSize = ChaCha20Utils().decryptBackupFile(encryptedDataSource, decryptedDataOutputSink, passphrase, userId)

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
