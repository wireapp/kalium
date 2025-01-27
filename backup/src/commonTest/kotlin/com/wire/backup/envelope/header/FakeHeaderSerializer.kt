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
package com.wire.backup.envelope.header

import com.wire.backup.envelope.BackupHeader
import com.wire.backup.envelope.BackupHeaderSerializer
import com.wire.backup.envelope.HashData
import com.wire.backup.envelope.HeaderParseResult
import okio.Source

internal class FakeHeaderSerializer(
    private val bytes: ByteArray = byteArrayOf(),
    private val parseResult: HeaderParseResult = HeaderParseResult.Success(fakeBackupHeader())
) : BackupHeaderSerializer {
    override fun headerToBytes(header: BackupHeader): ByteArray {
        return bytes
    }

    override fun parseHeader(source: Source): HeaderParseResult {
        return parseResult
    }
}

internal fun fakeBackupHeader() = BackupHeader(
    version = 1,
    isEncrypted = true,
    hashData = HashData(
        hashedUserId = UByteArray(HashData.HASHED_USER_ID_SIZE_IN_BYTES),
        salt = UByteArray(HashData.SALT_SIZE_IN_BYTES),
        operationsLimit = 4UL,
        hashingMemoryLimit = HashData.MINIMUM_MEMORY_LIMIT
    )
)
