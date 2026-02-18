/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.client

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId

internal interface CryptoBackupExporter {
    suspend fun exportCryptoDB(): Either<CoreFailure, CryptoBackupMetadata>
}

/**
 * Metadata for the cryptographic backup.
 */
internal data class CryptoBackupMetadata(
    val dbPath: String,
    val passphrase: ByteArray,
    val clientId: ClientId
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CryptoBackupMetadata

        if (dbPath != other.dbPath) return false
        if (!passphrase.contentEquals(other.passphrase)) return false
        if (clientId != other.clientId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dbPath.hashCode()
        result = 31 * result + passphrase.contentHashCode()
        result = 31 * result + clientId.hashCode()
        return result
    }
}
