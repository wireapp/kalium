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

package com.wire.kalium.cryptography.utils

import com.wire.kalium.cryptography.CryptoUserID
import com.wire.kalium.cryptography.backup.BackupCoder
import com.wire.kalium.cryptography.backup.BackupHeader
import com.wire.kalium.cryptography.backup.Passphrase
import com.wire.kalium.cryptography.readBinaryResource
import com.wire.kalium.cryptography.utils.ChaCha20Decryptor.decryptBackupFile
import com.wire.kalium.cryptography.utils.ChaCha20Encryptor.encryptBackupFile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChaCha20CoderTest {

    @OptIn(ExperimentalUnsignedTypes::class)
    @Test
    fun `given some passphrase, when generating the ChaCha20 secret key, it is a valid one`() = runTest {

        val password = "some password"
        val passphrase = Passphrase(password)
        val backupCoder = BackupCoder(CryptoUserID("some user id", "some-domain"), passphrase)
        val header = BackupHeader(
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
            val passphrase = Passphrase(password)

            val outputSize = encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val (_, decryptedDataSize) = decryptBackupFile(encryptedDataSource, decryptedDataOutputSink, passphrase, userId)

            val decryptedOutputContent = fakeFileSystem.read(decryptedOutputPath) {
                readByteArray()
            }

            assertEquals(fakeData.decodeToString(), decryptedOutputContent.decodeToString())
            assertTrue(outputSize > 0)
            assertEquals(decryptedDataSize, fakeData.size.toLong())
        }
    }

    @Test
    fun `given some dummy backup data, when decrypting an old backup format, the appropriate error is returned`() = runTest {
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
            val passphrase = Passphrase(password)

            val outputSize = encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)

            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val (_, decryptedDataSize) = decryptBackupFile(encryptedDataSource, decryptedDataOutputSink, passphrase, userId)

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
            val passphrase = Passphrase(password)
            val outputSize = encryptBackupFile(inputDataSource, encryptedOutputSink, userId, passphrase)
            val encryptedDataSource = fakeFileSystem.source(encryptedOutputPath)
            val (_, decryptedDataSize) = decryptBackupFile(encryptedDataSource, decryptedDataOutputSink, passphrase, userId)

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
