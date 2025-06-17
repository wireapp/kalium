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

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.durationMs
import com.wire.kalium.logic.data.message.height
import com.wire.kalium.logic.data.message.width
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftDao
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftEntity
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class MessageAttachmentDraftDataSource internal constructor(
    private val messageAttachmentDao: MessageAttachmentDraftDao,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : MessageAttachmentDraftRepository {

    override suspend fun add(
        conversationId: QualifiedID,
        node: CellNode,
        mimeType: String,
        dataPath: String,
        metadata: AssetContent.AssetMetadata?,
        uploadStatus: AttachmentUploadStatus,
    ) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachmentDao.addAttachment(
                uuid = node.uuid,
                versionId = node.versionId,
                conversationId = QualifiedIDEntity(conversationId.value, conversationId.domain),
                mimeType = mimeType,
                fileName = node.path.substringAfterLast("/"),
                fileSize = node.size ?: 0,
                dataPath = dataPath,
                nodePath = node.path,
                status = uploadStatus.name,
                assetWidth = metadata?.width(),
                assetHeight = metadata?.height(),
                assetDuration = metadata?.durationMs(),
            )
        }
    }

    override suspend fun observe(conversationId: QualifiedID): Flow<List<AttachmentDraft>> =
        messageAttachmentDao.observeAttachments(QualifiedIDEntity(conversationId.value, conversationId.domain))
            .map { list -> list.map { it.toModel() } }

    override suspend fun updateStatus(uuid: String, status: AttachmentUploadStatus) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachmentDao.updateUploadStatus(uuid, status.name)
        }
    }

    override suspend fun remove(uuid: String) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachmentDao.deleteAttachment(uuid)
        }
    }

    override suspend fun removeAttachmentDrafts(conversationId: ConversationId) = withContext(dispatchers.io) {
        messageAttachmentDao.deleteAttachments(QualifiedIDEntity(conversationId.value, conversationId.domain))
    }

    override suspend fun get(uuid: String) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachmentDao.getAttachment(uuid)?.toModel()
        }
    }

    override suspend fun getAll(conversationId: ConversationId) = withContext(dispatchers.io) {
        wrapStorageRequest {
            messageAttachmentDao.getAttachments(
                QualifiedIDEntity(conversationId.value, conversationId.domain)
            ).map { it.toModel() }
        }
    }
}

private fun MessageAttachmentDraftEntity.toModel() = AttachmentDraft(
    uuid = uuid,
    versionId = versionId,
    fileName = fileName,
    remoteFilePath = nodePath,
    localFilePath = dataPath,
    fileSize = fileSize,
    uploadStatus = AttachmentUploadStatus.valueOf(uploadStatus),
    mimeType = mimeType,
    assetWidth = assetWidth,
    assetHeight = assetHeight,
    assetDuration = assetDuration,
)
