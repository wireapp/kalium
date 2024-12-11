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
package com.wire.kalium.logic.data.conversation.folders

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS_FOLDERS
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.authenticated.properties.LabelListResponseDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface ConversationFolderRepository {

    suspend fun getFavoriteConversationFolder(): Either<CoreFailure, ConversationFolder>
    suspend fun observeConversationsFromFolder(folderId: String): Flow<List<ConversationDetailsWithEvents>>
    suspend fun updateConversationFolders(folderWithConversations: List<FolderWithConversations>): Either<CoreFailure, Unit>
    suspend fun fetchConversationFolders(): Either<CoreFailure, Unit>
    suspend fun addConversationToFolder(conversationId: QualifiedID, folderId: String): Either<CoreFailure, Unit>
    suspend fun removeConversationFromFolder(conversationId: QualifiedID, folderId: String): Either<CoreFailure, Unit>
    suspend fun syncConversationFoldersFromLocal(): Either<CoreFailure, Unit>
    suspend fun observeUserFolders(): Flow<Either<CoreFailure, List<ConversationFolder>>>
}

internal class ConversationFolderDataSource internal constructor(
    private val conversationFolderDAO: ConversationFolderDAO,
    private val userPropertiesApi: PropertiesApi,
    private val selfUserId: QualifiedID,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) : ConversationFolderRepository {

    override suspend fun updateConversationFolders(folderWithConversations: List<FolderWithConversations>): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationFolderDAO.updateConversationFolders(folderWithConversations.map { it.toDao() })
        }

    override suspend fun getFavoriteConversationFolder(): Either<CoreFailure, ConversationFolder> = wrapStorageRequest {
        conversationFolderDAO.getFavoriteConversationFolder()?.toModel()
    }

    override suspend fun observeConversationsFromFolder(folderId: String): Flow<List<ConversationDetailsWithEvents>> =
        conversationFolderDAO.observeConversationListFromFolder(folderId).map { conversationDetailsWithEventsEntityList ->
            conversationDetailsWithEventsEntityList.map {
                conversationMapper.fromDaoModelToDetailsWithEvents(it)
            }
        }

    override suspend fun fetchConversationFolders(): Either<CoreFailure, Unit> = wrapApiRequest {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).v("Fetching conversation folders")
        userPropertiesApi.getLabels()
    }
        .flatMapLeft {
            if (it is NetworkFailure.ServerMiscommunication
                && it.kaliumException is KaliumException.InvalidRequestError
                && it.kaliumException.errorResponse.code == HttpStatusCode.NotFound.value
            ) {
                kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).v("User has no labels, creating an empty list")
                // If the user has no labels, we create an empty list and on next stage we will create a favorite label
                Either.Right(LabelListResponseDTO(emptyList()))
            } else {
                Either.Left(it)
            }
        }
        .onSuccess { labelsResponse ->
            val folders = labelsResponse.labels.map { it.toFolder(selfUserId.domain) }.toMutableList()
            val favoriteLabel = folders.firstOrNull { it.type == FolderType.FAVORITE }

            if (favoriteLabel == null) {
                kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).v("Favorite label not found, creating a new one")
                folders.add(
                    FolderWithConversations(
                        id = uuid4().toString(),
                        name = "", // name will be handled by localization
                        type = FolderType.FAVORITE,
                        conversationIdList = emptyList()
                    )
                )
                userPropertiesApi.setProperty(
                    PropertyKey.WIRE_LABELS,
                    labelsResponse.copy(labels = folders.map { it.toLabel() })
                )
            }
            conversationFolderDAO.updateConversationFolders(folders.map { it.toDao() })
        }
        .onFailure {
            kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).e("Error fetching conversation folders $it")
            Either.Left(it)
        }
        .map { }

    override suspend fun addConversationToFolder(conversationId: QualifiedID, folderId: String): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS)
            .v("Adding conversation ${conversationId.toLogString()} to folder ${folderId.obfuscateId()}")
        return wrapStorageRequest {
            conversationFolderDAO.addConversationToFolder(conversationId.toDao(), folderId)
        }
    }

    override suspend fun removeConversationFromFolder(conversationId: QualifiedID, folderId: String): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS)
            .v("Removing conversation ${conversationId.toLogString()} from folder ${folderId.obfuscateId()}")
        return wrapStorageRequest {
            conversationFolderDAO.removeConversationFromFolder(conversationId.toDao(), folderId)
        }
    }

    override suspend fun syncConversationFoldersFromLocal(): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).v("Syncing conversation folders from local")
        return wrapStorageRequest { conversationFolderDAO.getFoldersWithConversations().map { it.toModel() } }
            .flatMap {
                wrapApiRequest {
                    userPropertiesApi.updateLabels(
                        LabelListResponseDTO(it.map { it.toLabel() })
                    )
                }
            }
    }

    override suspend fun observeUserFolders(): Flow<Either<CoreFailure, List<ConversationFolder>>> {
        return conversationFolderDAO.observeUserFolders()
            .wrapStorageRequest()
            .mapRight { folderEntities -> folderEntities.map { it.toModel() } }
    }
}
