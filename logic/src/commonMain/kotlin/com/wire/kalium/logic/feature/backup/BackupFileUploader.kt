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
package com.wire.kalium.logic.feature.backup

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import com.wire.kalium.logic.data.asset.UploadedAssetId
import okio.Path

internal interface BackupFileUploader {
    suspend fun upload(filePath: Path, fileName: String): Either<CoreFailure, UploadedAssetId>
}

internal class BackupFileUploaderImpl(
    private val assetRepository: AssetRepository,
    private val kaliumFileSystem: KaliumFileSystem,
) : BackupFileUploader {

    override suspend fun upload(filePath: Path, fileName: String): Either<CoreFailure, UploadedAssetId> =
        assetRepository.uploadAndPersistPublicAsset(
            mimeType = BACKUP_MIME_TYPE,
            assetDataPath = filePath,
            assetDataSize = kaliumFileSystem.size(filePath) ?: 0L,
            filename = fileName,
            filetype = BACKUP_FILE_TYPE,
        )

    private companion object {
        const val BACKUP_MIME_TYPE = "application/octet-stream"
        const val BACKUP_FILE_TYPE = "wire-mp-backup"
    }
}
