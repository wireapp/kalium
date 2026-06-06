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
package com.wire.kalium.logic.data.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.UploadedAssetId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Instant

internal interface OnlineBackupRepository {
    suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>>
    suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata>
}

internal class OnlineBackupDataSource : OnlineBackupRepository {

    override suspend fun listBackups(): Either<CoreFailure, List<OnlineBackupMetadata>> =
        TODO("Online backup storage API is not implemented")

    override suspend fun registerBackup(metadata: OnlineBackupMetadata): Either<CoreFailure, OnlineBackupMetadata> =
        TODO("Online backup storage API is not implemented")
}

public data class OnlineBackupMetadata(
    public val backupId: String,
    public val userId: UserId,
    public val clientId: String,
    public val fileName: String,
    public val lastMessageDate: Instant,
    public val assetId: UploadedAssetId,
    public val rootKeyId: String,
    public val encryptionAlgorithm: String,
)
