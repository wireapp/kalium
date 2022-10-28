package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.Backup
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChaCha20UtilsTest {

    @Test
    fun `test chacha20`() = runTest{
        val fakeData = "Some file data to be encrypted".toByteArray()
        val arrangement = Arrangement()
            .arrange()

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

            val salt = ByteArray(16)
            SecureRandom().nextBytes(salt)

            val password = "some password"
            val userId = CryptoUserID("some-user-id", "some-domain.com")
            val passphrase = Backup.Passphrase(password, userId)
            val backup = Backup(PlainData(salt), passphrase)

            val outputSize = ChaCha20Utils().encryptBackupFile(inputDataSource, encryptedOutputSink, backup)
            val outputContent = fakeFileSystem.read(encryptedOutputPath) {
                readByteArray()
            }.decodeToString()

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val decryptedDataSize = ChaCha20Utils().decryptFile(encryptedDataSource, decryptedDataOutputSink, passphrase)

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }.decodeToString()

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
