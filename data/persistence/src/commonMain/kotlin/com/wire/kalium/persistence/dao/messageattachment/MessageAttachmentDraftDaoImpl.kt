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
package com.wire.kalium.persistence.dao.messageattachment

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.MessageAttachmentDraftQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.messageattachment.MessageAttachmentDraftMapper.toDao
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

internal class MessageAttachmentDraftDaoImpl internal constructor(
    private val queries: MessageAttachmentDraftQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : MessageAttachmentDraftDao {

    override suspend fun addAttachment(
        uuid: String,
        versionId: String,
        conversationId: QualifiedIDEntity,
        mimeType: String,
        fileName: String,
        fileSize: Long,
        assetWidth: Int?,
        assetHeight: Int?,
        assetDuration: Long?,
        dataPath: String,
        nodePath: String,
        status: String,
    ) = withContext(writeDispatcher.value) {
        queries.upsertDraft(
            attachment_id = uuid,
            version_id = versionId,
            conversation_id = conversationId,
            mime_type = mimeType,
            file_name = fileName,
            file_size = fileSize,
            asset_width = assetWidth,
            asset_height = assetHeight,
            asset_duration_ms = assetDuration,
            data_path = dataPath,
            node_path = nodePath,
            upload_status = status,
        )
    }

    override suspend fun observeAttachments(): Flow<List<MessageAttachmentDraftEntity>> {
        return queries.getAllDrafts(mapper = ::toDao)
            .asFlow()
            .mapToList()
            .flowOn(readDispatcher.value)
    }

    override suspend fun updateUploadStatus(uuid: String, status: String) = withContext(writeDispatcher.value) {
        queries.updateUploadStatus(status, uuid)
    }

    override suspend fun getAttachments(conversationId: QualifiedIDEntity): List<MessageAttachmentDraftEntity> =
        withContext(readDispatcher.value) {
            queries.getDrafts(conversationId, ::toDao).executeAsList()
        }

    override suspend fun observeAttachments(conversationId: QualifiedIDEntity): Flow<List<MessageAttachmentDraftEntity>> =
            queries.getDrafts(conversationId, ::toDao).asFlow()
                .mapToList()
                .flowOn(readDispatcher.value)

    override suspend fun getAttachment(uuid: String): MessageAttachmentDraftEntity? = withContext(readDispatcher.value) {
        queries.getDraft(uuid, ::toDao).executeAsOneOrNull()
    }

    override suspend fun deleteAttachment(uuid: String) = withContext(writeDispatcher.value) {
        queries.deleteDraft(uuid)
    }

    override suspend fun deleteAttachments(conversationId: QualifiedIDEntity) = withContext(writeDispatcher.value) {
        queries.deleteDraftsForConversation(conversationId)
    }
}
