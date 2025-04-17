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
package com.wire.kalium.persistence.dao.message.attachment

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MessageAttachmentsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentMapper.toDao
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow

interface MessageAttachmentsDao {
    suspend fun getAssetPath(assetId: String): String?
    suspend fun setLocalPath(assetId: String, path: String?)
    suspend fun setPreviewUrl(assetId: String, previewUrl: String?)
    suspend fun setTransferStatus(assetId: String, status: String)
    suspend fun getAttachment(assetId: String): MessageAttachmentEntity
    suspend fun updateAttachment(assetId: String, url: String?, hash: String?, remotePath: String)
    suspend fun getAttachments(messageId: String, conversationId: QualifiedIDEntity): List<MessageAttachmentEntity>
    suspend fun getAttachments(): List<MessageAttachmentEntity>
    suspend fun observeAttachments(): Flow<List<MessageAttachmentEntity>>
}

internal class MessageAttachmentsDaoImpl(
    private val queries: MessageAttachmentsQueries,
) : MessageAttachmentsDao {

    override suspend fun getAttachments(messageId: String, conversationId: QualifiedIDEntity): List<MessageAttachmentEntity> =
        queries.getAttachments(messageId, conversationId, ::toDao).executeAsList()

    override suspend fun getAttachments(): List<MessageAttachmentEntity> =
        queries.getAllAttachments(::toDao).executeAsList()

    override suspend fun observeAttachments(): Flow<List<MessageAttachmentEntity>> =
        queries.getAllAttachments(::toDao).asFlow().mapToList()

    override suspend fun getAttachment(assetId: String): MessageAttachmentEntity =
        queries.getAttachment(asset_id = assetId, ::toDao).executeAsOne()

    override suspend fun updateAttachment(assetId: String, url: String?, hash: String?, remotePath: String) {
        queries.updateAttachment(url, hash, remotePath, assetId)
    }

    override suspend fun getAssetPath(assetId: String): String? =
        queries.getAssetPath(asset_id = assetId).executeAsOneOrNull()?.asset_path

    override suspend fun setLocalPath(assetId: String, path: String?) {
        queries.setLocalPath(
            local_path = path,
            asset_id = assetId
        )
    }

    override suspend fun setPreviewUrl(assetId: String, previewUrl: String?) {
        queries.setPreviewUrl(
            preview_url = previewUrl,
            asset_id = assetId
        )
    }

    override suspend fun setTransferStatus(assetId: String, status: String) {
        queries.setTransferStatus(
            asset_transfer_status = status,
            asset_id = assetId
        )
    }
}
