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

import com.wire.backup.data.BackupQualifiedId
import com.wire.backup.envelope.HashData
import com.wire.backup.hash.hashUserId
import kotlin.js.JsExport

@JsExport
public sealed class BackupPeekResult {
    /**
     * The provided data corresponds to a compatible backup artifact.
     */
    public class Success internal constructor(
        public val version: String,
        public val isEncrypted: Boolean,
        internal val hashData: HashData
        /** TODO: Add more info about the backup */
    ) : BackupPeekResult()

    public sealed class Failure : BackupPeekResult() {
        public data object UnknownFormat : Failure()
        public data class UnsupportedVersion(val backupVersion: String) : Failure()
    }
}

public suspend fun BackupPeekResult.Success.isCreatedBySameUser(userId: BackupQualifiedId): Boolean {
    val candidateHash = hashUserId(userId, hashData.salt, hashData.hashingMemoryLimit, hashData.operationsLimit)
    val actualHash = hashData.hashedUserId
    return candidateHash.contentEquals(actualHash)
}
