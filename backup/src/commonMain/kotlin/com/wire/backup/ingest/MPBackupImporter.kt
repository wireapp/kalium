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
package com.wire.backup.ingest

import com.wire.backup.data.BackupData
import com.wire.backup.encryption.DecryptionResult
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.BackupHeader
import com.wire.backup.envelope.BackupHeaderSerializer
import com.wire.backup.envelope.HeaderParseResult
import com.wire.backup.filesystem.EntryStorage
import okio.Buffer
import okio.Sink
import okio.Source
import okio.buffer
import kotlin.js.JsExport

/**
 * Entity able to parse backed-up data and returns
 * digestible data in [BackupData] format.
 */
@JsExport
public abstract class CommonMPBackupImporter internal constructor(
    private val encryptedStream: EncryptedStream<XChaChaPoly1305AuthenticationData> = EncryptedStream.XChaCha20Poly1305,
    private val headerSerializer: BackupHeaderSerializer = BackupHeaderSerializer.Default
) {

    /**
     * Peeks into a backup artifact, returning information about it.
     * @see BackupPeekResult
     */
    internal fun peekBackup(
        source: Source,
    ): BackupPeekResult {
        val peekBuffer = source.buffer().peek()
        return when (val result = headerSerializer.parseHeader(peekBuffer)) {
            HeaderParseResult.Failure.UnknownFormat -> BackupPeekResult.Failure.UnknownFormat
            is HeaderParseResult.Failure.UnsupportedVersion -> BackupPeekResult.Failure.UnsupportedVersion(result.version.toString())
            is HeaderParseResult.Success -> {
                val header = result.header
                BackupPeekResult.Success(header.version.toString(), header.isEncrypted, header.hashData)
            }
        }
    }

    /**
     * Decrypt (if needed) and unzip the backup artifact.
     * The resulting [BackupImportResult.Success] contains a [BackupImportPager], that can be used to
     * consume pages of backed up application data, like messages, users and conversations.
     */
    internal suspend fun importBackup(
        source: Source,
        passphrase: String?
    ): BackupImportResult = when (val result = headerSerializer.parseHeader(source)) {
        HeaderParseResult.Failure.UnknownFormat -> BackupImportResult.Failure.ParsingFailure
        is HeaderParseResult.Failure.UnsupportedVersion -> BackupImportResult.Failure.ParsingFailure
        is HeaderParseResult.Success -> handleCompatibleHeader(result, passphrase, source)
    }

    private suspend fun handleCompatibleHeader(
        result: HeaderParseResult.Success,
        passphrase: String?,
        source: Source
    ): BackupImportResult {
        val header = result.header
        val sink = getUnencryptedArchiveSink()
        val isEncrypted = header.isEncrypted
        return if (isEncrypted && passphrase == null) {
            BackupImportResult.Failure.MissingOrWrongPassphrase
        } else {
            extractDataArchiveFromBackupFile(isEncrypted, passphrase, source, sink, header)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun extractDataArchiveFromBackupFile(
        isEncrypted: Boolean,
        passphrase: String?,
        source: Source,
        sink: Sink,
        header: BackupHeader
    ): BackupImportResult {
        if (isEncrypted && passphrase != null) {
            val decryptionResult = decryptArchiveToDestination(source, sink, passphrase, header)
            if (decryptionResult is DecryptionResult.Failure) {
                return when (decryptionResult) {
                    DecryptionResult.Failure.AuthenticationFailure -> BackupImportResult.Failure.MissingOrWrongPassphrase
                    is DecryptionResult.Failure.Unknown -> BackupImportResult.Failure.UnknownError(
                        decryptionResult.message
                    )
                }
            }
        } else {
            copyUnencryptedArchiveToDestination(source, sink)
        }
        sink.close()
        return try {
            BackupImportResult.Success(BackupImportPager(unzipAllEntries()))
        } catch (t: Throwable) {
            BackupImportResult.Failure.UnzippingError(t.message ?: "Unknown zipping error.")
        }
    }

    private fun copyUnencryptedArchiveToDestination(source: Source, sink: Sink) {
        // No need to decrypt. We skip the encryption header bytes and copy the zip archive to the destination
        source.read(Buffer(), EncryptedStream.XCHACHA_20_POLY_1305_HEADER_LENGTH.toLong())
        val buffer = sink.buffer()
        buffer.writeAll(source)
        buffer.flush()
        sink.close()
    }

    private suspend fun decryptArchiveToDestination(
        source: Source,
        sink: Sink,
        passphrase: String,
        header: BackupHeader
    ): DecryptionResult = header.hashData.run {
        encryptedStream.decrypt(
            source = source,
            outputSink = sink,
            authenticationData = XChaChaPoly1305AuthenticationData(
                passphrase = passphrase,
                salt = salt,
                additionalData = headerSerializer.headerToBytes(header).toUByteArray(),
                hashOpsLimit = operationsLimit,
                hashMemLimit = hashingMemoryLimit
            )
        )
    }

    /**
     * Provides a sink to store the unencrypted data.
     * Be the archive encrypted or not, the data will be moved to this sink until [unzipAllEntries] is used.
     */
    internal abstract fun getUnencryptedArchiveSink(): Sink

    /**
     * Unzips all entries in the zip archive stored in the sink returned by [getUnencryptedArchiveSink].
     */
    internal abstract suspend fun unzipAllEntries(): EntryStorage
}

public expect class MPBackupImporter : CommonMPBackupImporter
