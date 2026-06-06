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

import com.wire.kalium.cells.domain.usecase.BackupCellFileUseCase
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.asset.UploadedAssetId
import okio.Path

internal interface BackupFileUploader {
    suspend fun upload(filePath: Path, fileName: String): Either<CoreFailure, UploadedAssetId>
}

internal class BackupFileUploaderImpl(
    private val backupConversationResolver: BackupConversationResolver,
    private val backupCellFile: BackupCellFileUseCase,
) : BackupFileUploader {

    override suspend fun upload(filePath: Path, fileName: String): Either<CoreFailure, UploadedAssetId> =
        backupConversationResolver.getOrCreateBackupConversation().flatMap { conversationId ->
            backupCellFile.upload(conversationId, filePath, fileName)
        }.map { uploadedFile ->
            UploadedAssetId(
                key = uploadedFile.uuid,
                domain = uploadedFile.versionId,
                assetToken = uploadedFile.path,
            )
        }
}
