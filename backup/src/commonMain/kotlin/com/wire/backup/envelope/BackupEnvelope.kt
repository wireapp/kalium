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
package com.wire.backup.envelope

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.envelope.cryptography.BackupPassphrase
import com.wire.backup.envelope.header.BackupHeader
import com.wire.backup.envelope.header.DefaultFileHeaderSerializer
import com.wire.backup.envelope.header.HeaderParseResult
import okio.Sink
import okio.Source

internal class BackupEnvelope private constructor(
    val header: BackupHeader,
    internal val archivedData: Source
) {


    fun isAuthorUserId(qualifiedId: BackupQualifiedId): Boolean {
        TODO("Add function to see if user matches")
    }

    fun decryptData(output: Sink, passphrase: BackupPassphrase?) {
        if (!header.isEncrypted) throw IllegalStateException("Cannot decrypt non encrypted data!")

    }

    companion object Reader : BackupEnvelopeReader {
        override fun read(source: Source): BackupEnvelopeReadResult =
            when (val headerResult = DefaultFileHeaderSerializer.parseHeader(source)) {
                HeaderParseResult.Failure.UnknownFormat -> BackupEnvelopeReadResult.Failure.UnknownFormat
                is HeaderParseResult.Failure.UnsupportedVersion -> BackupEnvelopeReadResult.Failure.UnsupportedVersion
                is HeaderParseResult.Success -> BackupEnvelopeReadResult.Success(BackupEnvelope(headerResult.header, source))
            }
    }
}

internal sealed interface BackupEnvelopeReadResult {
    class Success(val envelope: BackupEnvelope) : BackupEnvelopeReadResult
    sealed interface Failure : BackupEnvelopeReadResult {
        data object UnsupportedVersion : Failure
        data object UnknownFormat : Failure
    }
}

internal interface BackupEnvelopeReader {
    fun read(source: Source): BackupEnvelopeReadResult
}

internal interface BackupEnvelopeWriter {
    fun createFile(userId: BackupQualifiedId, unencryptedArchive: Source, passphrase: BackupPassphrase?): Source
}



