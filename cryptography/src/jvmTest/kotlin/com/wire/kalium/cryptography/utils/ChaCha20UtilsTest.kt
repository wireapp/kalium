package com.wire.kalium.cryptography.utils

import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import java.security.SecureRandom
import kotlin.test.assertTrue

internal class ChaCha20UtilsTest {

    @Test
    fun `test chacha20`() {
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

            val key = ChaCha20Utils().generateRandomChaCha20Key()

            val salt = ByteArray(12)
            SecureRandom().nextBytes(salt)
            val hashedUserId = PlainData(calcSHA256("some-random-user-id".toByteArray()))
            val outputSize = ChaCha20Utils().encryptBackupFile(inputDataSource, encryptedOutputSink, key, PlainData(salt), hashedUserId)
            val outputContent = fakeFileSystem.read(encryptedOutputPath) {
                readByteArray()
            }.decodeToString()

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val decryptedContent = ChaCha20Utils().decryptFile(encryptedDataSource, decryptedDataOutputSink, key, PlainData(salt))

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }.decodeToString()

            assertTrue(outputSize > 1)
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
