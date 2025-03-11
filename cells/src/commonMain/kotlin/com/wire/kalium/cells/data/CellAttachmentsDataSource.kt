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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentsDao
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

internal class CellAttachmentsDataSource(
    private val messageAttachments: MessageAttachmentsDao,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl,
) : CellAttachmentsRepository {

    override suspend fun savePreviewUrl(assetId: String, url: String?) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.setPreviewUrl(assetId, url)
        }
    }

    override suspend fun getAssetPath(assetId: String): Either<StorageFailure, String?> = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.getAssetPath(assetId)
        }
    }

    override suspend fun setAssetTransferStatus(assetId: String, status: AssetTransferStatus): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            messageAttachments.setTransferStatus(assetId, status.name)
        }

    override suspend fun saveLocalPath(assetId: String, path: String?): Either<StorageFailure, Unit> = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.setLocalPath(assetId, path)
        }
    }

    override suspend fun saveContentUrlAndHash(assetId: String, contentUrl: String?, hash: String?) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachments.setContentUrlAndHash(assetId, contentUrl, hash)
        }
    }

    override suspend fun getAttachment(assetId: String): Either<StorageFailure, MessageAttachment> =
        withContext(dispatchers.io) {
            wrapStorageRequest {
                messageAttachments.getAttachment(assetId).toModel()
            }
        }

    override suspend fun getAttachments(
        messageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, List<MessageAttachment>> = withContext(dispatchers.io) {
            wrapStorageRequest {
                messageAttachments.getAttachments(
                    messageId = messageId,
                    conversationId = QualifiedIDEntity(conversationId.value, conversationId.domain)
                ).map { it.toModel() }
            }
        }
}

// TODO: Where to host the mapper (currently part of logic module)?
private fun MessageAttachmentEntity.toModel(): MessageAttachment =
    if (cellAsset) {
        CellAssetContent(
            id = assetId,
            versionId = assetVersionId,
            mimeType = mimeType,
            assetPath = assetPath,
            assetSize = assetSize,
            previewUrl = previewUrl?.takeIf { it.isNotEmpty() },
            localPath = localPath?.takeIf { it.isNotEmpty() },
            transferStatus = AssetTransferStatus.valueOf(assetTransferStatus),
            metadata = null,
            contentHash = contentHash,
        )
    } else {
        TODO()
    }
