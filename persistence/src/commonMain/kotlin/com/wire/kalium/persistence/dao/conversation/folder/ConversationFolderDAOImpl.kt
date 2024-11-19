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
import com.wire.kalium.persistence.ConversationFoldersQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsMapper
import com.wire.kalium.persistence.util.mapToList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

class ConversationFolderDAOImpl internal constructor(
    private val conversationFoldersQueries: ConversationFoldersQueries,
    private val coroutineContext: CoroutineContext,
) : ConversationFolderDAO {
    private val conversationDetailsWithEventsMapper = ConversationDetailsWithEventsMapper

    override suspend fun getFoldersWithConversations(): List<FolderWithConversationsEntity> = withContext(coroutineContext) {
        val folderMap = mutableMapOf<String, FolderWithConversationsEntity>()

        conversationFoldersQueries.getFolderWithConversations { folderId, folderName, folderType, conversationId ->
            val folder = folderMap.getOrPut(folderId) {
                FolderWithConversationsEntity(
                    id = folderId,
                    name = folderName,
                    type = folderType,
                    conversationIdList = mutableListOf()
                )
            }

            if (conversationId != null) {
                (folder.conversationIdList as MutableList).add(conversationId)
            }
        }

        folderMap.values.toList()
    }

    override suspend fun observeConversationListFromFolder(folderId: String): Flow<List<ConversationDetailsWithEventsEntity>> {
        return conversationFoldersQueries.getConversationsFromFolder(
            folderId,
            conversationDetailsWithEventsMapper::fromViewToModel
        )
            .asFlow()
            .mapToList()
            .flowOn(coroutineContext)
    }

    override suspend fun getFavoriteConversationFolder(): ConversationFolderEntity {
        return conversationFoldersQueries.getFavoriteFolder { id, name, folderType ->
            ConversationFolderEntity(id, name, folderType)
        }
            .executeAsOne()
    }

    override suspend fun updateConversationFolders(folderWithConversationsList: List<FolderWithConversationsEntity>) =
        withContext(coroutineContext) {
            conversationFoldersQueries.transaction {
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
