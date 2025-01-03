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
package com.wire.kalium.persistence.dao.conversation.folder

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationFolder
import com.wire.kalium.persistence.ConversationFoldersQueries
import com.wire.kalium.persistence.GetAllFoldersWithConversations
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsMapper
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ConversationFolderDAOImpl internal constructor(
    private val conversationFoldersQueries: ConversationFoldersQueries,
    private val coroutineContext: CoroutineContext,
) : ConversationFolderDAO {
    private val conversationDetailsWithEventsMapper = ConversationDetailsWithEventsMapper

    override suspend fun observeFolders(): Flow<List<ConversationFolderEntity>> {
        return conversationFoldersQueries.getFolders()
            .asFlow()
            .mapToList()
            .map { it.map(::toEntity) }
            .flowOn(coroutineContext)
    }

    override suspend fun getFoldersWithConversations(): List<FolderWithConversationsEntity> = withContext(coroutineContext) {
        val labeledConversationList = conversationFoldersQueries.getAllFoldersWithConversations().executeAsList().map(::toEntity)

        val folderMap = labeledConversationList.groupBy { it.folderId }.mapValues { entry ->
            val folderId = entry.key
            val firstRow = entry.value.first()
            FolderWithConversationsEntity(
                id = folderId,
                name = firstRow.folderName,
                type = firstRow.folderType,
                conversationIdList = entry.value.mapNotNull { it.conversationId }
            )
        }

        folderMap.values.toList()
    }

    private fun toEntity(row: GetAllFoldersWithConversations) = LabeledConversationEntity(
        folderId = row.label_id,
        folderName = row.label_name,
        folderType = row.label_type,
        conversationId = row.conversation_id
    )

    private fun toEntity(row: ConversationFolder) = ConversationFolderEntity(
        id = row.id,
        name = row.name,
        type = row.folder_type
    )

    override suspend fun observeConversationListFromFolder(folderId: String): Flow<List<ConversationDetailsWithEventsEntity>> {
        return conversationFoldersQueries.getConversationsFromFolder(
            folderId,
            conversationDetailsWithEventsMapper::fromViewToModel
        )
            .asFlow()
            .mapToList()
            .flowOn(coroutineContext)
    }

    override suspend fun getFavoriteConversationFolder(): ConversationFolderEntity? {
        return conversationFoldersQueries.getFavoriteFolder { id, name, folderType ->
            ConversationFolderEntity(id, name, folderType)
        }
            .executeAsOneOrNull()
    }

    override suspend fun updateConversationFolders(folderWithConversationsList: List<FolderWithConversationsEntity>) =
        withContext(coroutineContext) {
            conversationFoldersQueries.transaction {
                conversationFoldersQueries.clearLabeledConversations()
                conversationFoldersQueries.clearFolders()
                folderWithConversationsList.forEach { folderWithConversations ->
                    conversationFoldersQueries.upsertFolder(
                        folderWithConversations.id,
                        folderWithConversations.name,
                        folderWithConversations.type
                    )
                    folderWithConversations.conversationIdList.forEach { conversationId ->
                        conversationFoldersQueries.insertLabeledConversation(
                            conversationId,
                            folderWithConversations.id
                        )
                    }
                }
            }
        }

    override suspend fun addConversationToFolder(conversationId: QualifiedIDEntity, folderId: String) = withContext(coroutineContext) {
        conversationFoldersQueries.insertLabeledConversation(conversationId, folderId)
    }

    override suspend fun removeConversationFromFolder(conversationId: QualifiedIDEntity, folderId: String) = withContext(coroutineContext) {
        conversationFoldersQueries.deleteLabeledConversation(conversationId, folderId)
    }
}
