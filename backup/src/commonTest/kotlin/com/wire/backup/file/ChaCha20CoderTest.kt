/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.backup.file

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.file.cryptography.ChaCha20Decryptor
import com.wire.backup.file.cryptography.ChaCha20Encryptor
import com.wire.backup.file.cryptography.LibsodiumInitializer.initializeLibsodiumIfNeeded
import com.wire.backup.file.cryptography.BackupPassphrase
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ChaCha20CoderTest {

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun givenSomePassphrase_whenGeneratingTheChaCha20SecretKey_itIsAValidOne() = runTest {
        val password = "some password"
        val passphrase = BackupPassphrase(password)
        val backupCoder = BackupCoder(BackupQualifiedId("some user id", "some-domain"), passphrase)
        initializeLibsodiumIfNeeded()
        val header = BackupHeader(
            format = "some format",
            version = "some version",
            salt = UByteArray(16) { 0u },
            hashedUserId = "some hashed user id".encodeToByteArray().toUByteArray(),
            operationsLimit = 1,
            hashingMemoryLimit = 10000 // Can't be too small
        )

        val chaCha20Key = backupCoder.generateChaCha20Key(header)

        assertTrue(chaCha20Key.isNotEmpty())
        assertTrue(chaCha20Key.size == 32)
    }

    @Test
    fun givenSomeDummyBackupData_whenEncryptingAndDecryptingWithChacha20_dataMatches() = runTest {
        // Given
        val fakeData = "Some file data to be encrypted".encodeToByteArray()
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
            val userId = BackupQualifiedId("some-user-id", "some-domain.com")
            val passphrase = BackupPassphrase(password)

            val outputSize =
                ChaCha20Encryptor.encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val (_, decryptedDataSize) = ChaCha20Decryptor.decryptBackupFile(
                encryptedDataSource,
                decryptedDataOutputSink,
                passphrase,
                userId
            )

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }

            assertEquals(fakeData.decodeToString(), decryptedOutputContent.decodeToString())
            assertTrue(outputSize > 0)
            assertEquals(decryptedDataSize, fakeData.size.toLong())
        }
    }

    @Test
    fun givenSomeDummyBackupData_whenDecryptingAnOldBackupFormat_theAppropriateErrorIsReturned() =
        runTest {
            // Given
            val fakeData = "Some file data to be encrypted".encodeToByteArray()
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
                val userId = BackupQualifiedId("some-user-id", "some-domain.com")
                val passphrase = BackupPassphrase(password)

                val outputSize =
                    ChaCha20Encryptor.encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)

                val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
                val (_, decryptedDataSize) = ChaCha20Decryptor.decryptBackupFile(
                    encryptedDataSource,
                    decryptedDataOutputSink,
                    passphrase,
                    userId
                )

                val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                    readByteArray()
                }

                assertEquals(fakeData.decodeToString(), decryptedOutputContent.decodeToString())
                assertTrue(outputSize > 0)
                assertEquals(decryptedDataSize, fakeData.size.toLong())
            }
        }

    @Test
    fun givenSomeBigBackupData_whenEncryptingAndDecryptingWithChacha20_dataMatches() = runTest {
        // Given
        val realFileData = ByteArray(10_000_000) { it.toByte() }
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
            val userId = BackupQualifiedId("some-user-id", "some-domain.com")
            val passphrase = BackupPassphrase(password)
            val outputSize =
                ChaCha20Encryptor.encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)
            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val (_, decryptedDataSize) = ChaCha20Decryptor.decryptBackupFile(
                encryptedDataSource,
                decryptedDataOutputSink,
                passphrase,
                userId
            )

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
