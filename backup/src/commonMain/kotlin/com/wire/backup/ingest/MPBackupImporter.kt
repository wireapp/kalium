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
import com.wire.backup.encryption.EncryptedStream
import com.wire.backup.encryption.XChaChaPoly1305AuthenticationData
import com.wire.backup.envelope.cryptography.BackupPassphrase
import com.wire.backup.envelope.header.BackupHeaderSerializer
import com.wire.backup.envelope.header.HeaderParseResult
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
public abstract class CommonMPBackupImporter {

    /**
     * Peeks into the complete backup artifact, returning [BackupPeekResult] with basic information about the artifact.
     */
    internal fun getBackupInfo(source: Source): BackupPeekResult = try {
        val peekBuffer = source.buffer().peek()
        when (val result = BackupHeaderSerializer.Default.parseHeader(peekBuffer)) {
            HeaderParseResult.Failure.UnknownFormat -> BackupPeekResult.Failure.UnknownFormat
            is HeaderParseResult.Failure.UnsupportedVersion -> BackupPeekResult.Failure.UnsupportedVersion(result.version.toString())
            is HeaderParseResult.Success -> {
                val header = result.header
                BackupPeekResult.Success(header.version.toString(), header.isEncrypted)
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
        println(e)
        BackupPeekResult.Failure.UnknownFormat
    }

    /**
     * Decrypt (if needed) and unzip the backup artifact.
     * The resulting [BackupImportResult.Success] contains a [BackupImportPager], that can be used to
     * consume pages of backed up application data, like messages, users and conversations.
     */
    internal suspend fun importBackup(source: Source, passphrase: BackupPassphrase?): BackupImportResult {
        return when (val result = BackupHeaderSerializer.Default.parseHeader(source)) {
            HeaderParseResult.Failure.UnknownFormat -> BackupImportResult.Failure.ParsingFailure
            is HeaderParseResult.Failure.UnsupportedVersion -> BackupImportResult.Failure.ParsingFailure
            is HeaderParseResult.Success -> {
                val header = result.header
                val sink = getUnencryptedArchiveSink()
                val isEncrypted = header.isEncrypted
                if (isEncrypted && passphrase == null) {
                    BackupImportResult.Failure.MissingOrWrongPassphrase
                } else {
                    if (isEncrypted && passphrase != null) {
                        EncryptedStream.decrypt(
                            source, sink, XChaChaPoly1305AuthenticationData(
                                passphrase,
                                header.hashData.salt,
                                BackupHeaderSerializer.Default.headerToBytes(header).toUByteArray(),
                                header.hashData.operationsLimit,
                                header.hashData.hashingMemoryLimit
                            )
                        )
                    } else {
                        // No need to decrypt. We skip the encryption header bytes and copy the zip archive to the destination
                        source.read(Buffer(), EncryptedStream.XCHACHA_20_POLY_1305_HEADER_LENGTH.toLong())
                        val buffer = sink.buffer()
                        buffer.writeAll(source)
                        buffer.flush()
                    }
                }
                sink.close()
                BackupImportResult.Success(BackupImportPager(unzipAllEntries()))
            }
        }
    }

    /**
     * Provides a sink to store the unencrypted data.
     * Be the archive encrypted or not, the data will be moved to this sink until [unzipAllEntries] is used.
     */
    internal abstract fun getUnencryptedArchiveSink(): Sink

    /**
     * Unzips all entries in the zip archive stored in the sink returned by [getUnencryptedArchiveSink].
     */
    internal abstract fun unzipAllEntries(): EntryStorage
}

public expect class MPBackupImporter : CommonMPBackupImporter
