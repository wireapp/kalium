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

import com.wire.backup.encryption.DecryptionResult
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.BackupHeaderSerializer
import com.wire.backup.envelope.HeaderParseResult
import com.wire.backup.envelope.header.FakeHeaderSerializer
import com.wire.backup.filesystem.EntryStorage
import com.wire.backup.filesystem.InMemoryEntryStorage
import kotlinx.coroutines.test.runTest
import okio.Buffer
import okio.Sink
import okio.Source
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MPBackupImporterTest {

    private fun createSubject(
        unzipEntries: () -> EntryStorage = { InMemoryEntryStorage() },
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
}
