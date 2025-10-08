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
package com.wire.kalium.cells.domain

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageAttachment
import io.mockative.Mockable

@Mockable
internal interface CellAttachmentsRepository {
    suspend fun getAssetPath(assetId: String): Either<StorageFailure, String?>
    suspend fun setAssetTransferStatus(assetId: String, status: AssetTransferStatus): Either<StorageFailure, Unit>
    suspend fun getAttachment(assetId: String): Either<StorageFailure, MessageAttachment>
    suspend fun savePreviewUrl(assetId: String, url: String?): Either<StorageFailure, Unit>
    suspend fun saveLocalPath(assetId: String, path: String?): Either<StorageFailure, Unit>
    suspend fun updateAttachment(assetId: String, contentUrl: String?, hash: String?, remotePath: String): Either<StorageFailure, Unit>
    suspend fun getAttachments(messageId: String, conversationId: ConversationId): Either<StorageFailure, List<MessageAttachment>>
    suspend fun getAttachments(): Either<StorageFailure, List<MessageAttachment>>
    suspend fun saveStandaloneAssetPath(assetId: String, path: String, size: Long): Either<StorageFailure, Unit>
    suspend fun getStandaloneAssetPaths(): Either<StorageFailure, List<Pair<String, String>>>
    suspend fun deleteStandaloneAsset(assetId: String): Either<StorageFailure, Unit>
    suspend fun updateAssetPath(assetId: String, remotePath: String): Either<StorageFailure, Unit>
}
