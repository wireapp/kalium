/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.backup.ingest

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.encryption.DecryptionResult
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.BackupHeader
import com.wire.backup.envelope.BackupHeaderSerializer
import com.wire.backup.envelope.HashData
import com.wire.backup.envelope.HeaderParseResult
import com.wire.backup.envelope.header.FakeHeaderSerializer
import com.wire.backup.filesystem.BackupPageStorage
import com.wire.backup.filesystem.InMemoryBackupPageStorage
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Sink
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MPBackupImporterTest {

    private fun createSubject(
        unzipEntries: () -> BackupPageStorage = { InMemoryBackupPageStorage() },
        encryptedStream: EncryptedStream<XChaChaPoly1305AuthenticationData> = EncryptedStream.XChaCha20Poly1305,
        headerSerializer: BackupHeaderSerializer = BackupHeaderSerializer.Default,
    ): CommonMPBackupImporter = object : CommonMPBackupImporter(encryptedStream, headerSerializer) {
        override fun getUnencryptedArchiveSink(): Sink = Buffer()

        override suspend fun unzipAllEntries() = unzipEntries()
    }

    @Test
    fun givenFailureToParseHeader_whenImporting_thenFailureIsReturned() = runTest {
        val subject = createSubject(
            headerSerializer = FakeHeaderSerializer(parseResult = HeaderParseResult.Failure.UnknownFormat)
        )
        val result = subject.importBackup(Buffer(), null)

        assertEquals(BackupImportResult.Failure.ParsingFailure, result)
    }

    @Test
    fun givenNoPasswordAndBackupIsEncrypted_whenImporting_thenMissingPasswordIsReturned() = runTest {
        val subject = createSubject(
            headerSerializer = FakeHeaderSerializer()
        )
        val result = subject.importBackup(Buffer(), null)

        assertEquals(BackupImportResult.Failure.MissingOrWrongPassphrase, result)
    }

    @Test
    fun givenDecryptionFailsForUnknownReason_whenImporting_thenFailureIsReturned() = runTest {
        val decryptionResult = DecryptionResult.Failure.Unknown("Oopsie")
        val subject = createSubject(
            headerSerializer = FakeHeaderSerializer(),
            encryptedStream = object : EncryptedStream<XChaChaPoly1305AuthenticationData> by EncryptedStream.XChaCha20Poly1305 {
                override suspend fun decrypt(
                    source: Source,
                    outputSink: Sink,
                    authenticationData: XChaChaPoly1305AuthenticationData
                ): DecryptionResult = decryptionResult
            }
        )
        val result = subject.importBackup(Buffer(), "pass")

        assertIs<BackupImportResult.Failure.UnknownError>(result)
        assertEquals(decryptionResult.message, result.message)
    }

    @Test
    fun givenDecryptionFailsDueToWrongPassword_whenImporting_thenFailureIsReturned() = runTest {
        val decryptionResult = DecryptionResult.Failure.AuthenticationFailure
        val subject = createSubject(
            headerSerializer = FakeHeaderSerializer(),
            encryptedStream = object : EncryptedStream<XChaChaPoly1305AuthenticationData> by EncryptedStream.XChaCha20Poly1305 {
                override suspend fun decrypt(
                    source: Source,
                    outputSink: Sink,
                    authenticationData: XChaChaPoly1305AuthenticationData
                ): DecryptionResult = decryptionResult
            }
        )
        val result = subject.importBackup(Buffer(), "pass")

        assertIs<BackupImportResult.Failure.MissingOrWrongPassphrase>(result)
    }

    @Test
    fun givenUnzippingThrows_whenImporting_thenFailureIsReturned() = runTest {
        val throwable = IllegalStateException("something went wrong")
        val subject = createSubject(
            unzipEntries = { throw throwable },
            encryptedStream = object : EncryptedStream<XChaChaPoly1305AuthenticationData> by EncryptedStream.XChaCha20Poly1305 {
                override suspend fun decrypt(
                    source: Source,
                    outputSink: Sink,
                    authenticationData: XChaChaPoly1305AuthenticationData
                ): DecryptionResult = DecryptionResult.Success
            },
            headerSerializer = FakeHeaderSerializer()
        )
        val result = subject.importBackup(Buffer(), "pass")

        assertIs<BackupImportResult.Failure.UnzippingError>(result)
        assertEquals(throwable.message, result.message)
    }

    @Test
    fun givenBackupHeaderBuffer_whenPeeking_thenCorrectDataIsReturned() = runTest {
        val userId = BackupQualifiedId("user", "domain")
        val header = BackupHeader(
            version = BackupHeaderSerializer.Default.MAXIMUM_SUPPORTED_VERSION,
            isEncrypted = true,
            hashData = HashData.defaultFromUserId(userId)
        )
        val data = BackupHeaderSerializer.Default.headerToBytes(header)
        val buffer = Buffer()
        buffer.write(data)
        val subject = createSubject()

        val result = subject.peekBackup(buffer)
        assertIs<BackupPeekResult.Success>(result)
        assertEquals(header.version.toString(), result.version)
        assertEquals(header.isEncrypted, result.isEncrypted)
    }

    @Test
    fun givenBackupIsFromAnUnsupportedVersion_whenPeeking_thenCorrectDataIsReturned() = runTest {
        val userId = BackupQualifiedId("user", "domain")
        val header = BackupHeader(
            version = BackupHeaderSerializer.Default.MINIMUM_SUPPORTED_VERSION - 1,
            isEncrypted = true,
            hashData = HashData.defaultFromUserId(userId)
        )
        val data = BackupHeaderSerializer.Default.headerToBytes(header)
        val buffer = Buffer()
        buffer.write(data)
        val subject = createSubject()

        val result = subject.peekBackup(buffer)
        assertIs<BackupPeekResult.Failure.UnsupportedVersion>(result)
        assertEquals(header.version.toString(), result.backupVersion)
    }

    @Test
    fun givenDataIsNotFromValidBackup_whenPeeking_thenCorrectDataIsReturned() = runTest {
        val data = byteArrayOf(0x00, 0x01, 0x02)
        val buffer = Buffer()
        buffer.write(data)
        val subject = createSubject()

        val result = subject.peekBackup(buffer)
        assertIs<BackupPeekResult.Failure.UnknownFormat>(result)
    }
}
