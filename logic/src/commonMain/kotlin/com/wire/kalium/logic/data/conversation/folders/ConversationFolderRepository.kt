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

import kotlin.uuid.Uuid
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.mapRight
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CONVERSATIONS_FOLDERS
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.ConversationMapper
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.FolderWithConversations
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.properties.LabelListResponseDTO
import com.wire.kalium.network.api.authenticated.properties.PropertyKey
import com.wire.kalium.network.api.base.authenticated.properties.PropertiesApi
import com.wire.kalium.common.error.ServerError
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderDAO
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Suppress("TooManyFunctions")
internal interface ConversationFolderRepository {

    suspend fun getFavoriteConversationFolder(): Either<CoreFailure, ConversationFolder>
    fun observeConversationsFromFolder(folderId: String): Flow<List<ConversationDetailsWithEvents>>
    suspend fun updateConversationFolders(folderWithConversations: List<FolderWithConversations>): Either<CoreFailure, Unit>
    suspend fun fetchConversationFolders(): Either<CoreFailure, Unit>
    suspend fun addConversationToFolder(conversationId: QualifiedID, folderId: String): Either<CoreFailure, Unit>
    suspend fun removeConversationFromFolder(
        conversationId: QualifiedID,
        folderId: String,
        isFavorite: Boolean = false
    ): Either<CoreFailure, Unit>

    suspend fun removeFolder(folderId: String): Either<CoreFailure, Unit>
    suspend fun syncConversationFoldersFromLocal(): Either<CoreFailure, Unit>
    fun observeFolders(): Flow<Either<CoreFailure, List<ConversationFolder>>>
    suspend fun addFolder(folder: ConversationFolder): Either<CoreFailure, Unit>
    suspend fun getFolder(folderId: String): Either<CoreFailure, FolderWithConversations>
    suspend fun updateMembership(
        folderId: String,
        added: Set<QualifiedID>,
        removed: Set<QualifiedID>,
    ): Either<CoreFailure, ConversationFolder>
    suspend fun saveFolder(
        folder: ConversationFolder,
        added: Set<QualifiedID>,
        removed: Set<QualifiedID>,
    ): Either<CoreFailure, Unit>
    suspend fun deleteFolderAndSync(folderId: String): Either<CoreFailure, Unit>

    /** Moves the given folders into the listed order, within the places they already occupy. */
    suspend fun reorderFolders(folderIds: List<String>): Either<CoreFailure, Unit>
}

@Suppress("TooManyFunctions")
internal class ConversationFolderDataSource internal constructor(
    private val conversationFolderDAO: ConversationFolderDAO,
    private val userPropertiesApi: PropertiesApi,
    private val selfUserId: QualifiedID,
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId)
) : ConversationFolderRepository {
    private val folderMutationMutex = Mutex()

    override suspend fun updateConversationFolders(folderWithConversations: List<FolderWithConversations>): Either<CoreFailure, Unit> =
        folderMutationMutex.withLock { persistFolders(folderWithConversations) }

    private suspend fun persistFolders(folders: List<FolderWithConversations>): Either<CoreFailure, Unit> = wrapStorageRequest {
        conversationFolderDAO.updateConversationFolders(folders.map { it.toDao() })
    }

    override suspend fun getFavoriteConversationFolder(): Either<CoreFailure, ConversationFolder> = wrapStorageRequest {
        conversationFolderDAO.getFavoriteConversationFolder()?.toModel()
    }

    override fun observeConversationsFromFolder(folderId: String): Flow<List<ConversationDetailsWithEvents>> =
        conversationFolderDAO.observeConversationListFromFolder(folderId).map { conversationDetailsWithEventsEntityList ->
            conversationDetailsWithEventsEntityList.map {
                conversationMapper.fromDaoModelToDetailsWithEvents(it)
            }
        }

    override suspend fun fetchConversationFolders(): Either<CoreFailure, Unit> = folderMutationMutex.withLock {
        fetchFolders()
    }

    private suspend fun fetchFolders(): Either<CoreFailure, Unit> = wrapApiRequest {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).v("Fetching conversation folders")
        userPropertiesApi.getLabels()
    }
        .flatMapLeft {
            if (it is NetworkFailure.ServerMiscommunication
                && it.reason is ServerError.InvalidRequest
                && (it.reason as ServerError.InvalidRequest).errorResponse.code == HttpStatusCode.NotFound.value
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
                        id = Uuid.random().toString(),
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
        return folderMutationMutex.withLock {
            wrapStorageRequest { conversationFolderDAO.addConversationToFolder(conversationId.toDao(), folderId) }
        }
    }

    override suspend fun removeConversationFromFolder(
        conversationId: QualifiedID,
        folderId: String,
        isFavorite: Boolean
    ): Either<CoreFailure, Unit> {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS)
            .v("Removing conversation ${conversationId.toLogString()} from folder ${folderId.obfuscateId()}")
        return folderMutationMutex.withLock {
            wrapStorageRequest { conversationFolderDAO.removeConversationFromFolder(conversationId.toDao(), folderId) }
        }
    }

    override suspend fun removeFolder(folderId: String): Either<CoreFailure, Unit> = folderMutationMutex.withLock {
        wrapStorageRequest { conversationFolderDAO.removeFolder(folderId) }
    }

    override suspend fun syncConversationFoldersFromLocal(): Either<CoreFailure, Unit> = folderMutationMutex.withLock {
        kaliumLogger.withFeatureId(CONVERSATIONS_FOLDERS).v("Syncing conversation folders from local")
        wrapStorageRequest { conversationFolderDAO.getFoldersWithConversations().map { it.toModel() } }
            .flatMap {
                wrapApiRequest {
                    userPropertiesApi.updateLabels(
                        LabelListResponseDTO(it.map { it.toLabel() })
                    )
                }
            }
    }

    override fun observeFolders(): Flow<Either<CoreFailure, List<ConversationFolder>>> {
        return conversationFolderDAO.observeFolders()
            .wrapStorageRequest()
            .mapRight { folderEntities -> folderEntities.map { it.toModel() } }
    }

    override suspend fun addFolder(folder: ConversationFolder): Either<CoreFailure, Unit> {
        return folderMutationMutex.withLock {
            wrapStorageRequest { conversationFolderDAO.addFolder(folder.toDao()) }
        }
    }

    override suspend fun getFolder(folderId: String): Either<CoreFailure, FolderWithConversations> = wrapStorageRequest {
        conversationFolderDAO.getFoldersWithConversations().first { it.id == folderId }.toModel()
    }

    override suspend fun updateMembership(
        folderId: String,
        added: Set<QualifiedID>,
        removed: Set<QualifiedID>,
    ): Either<CoreFailure, ConversationFolder> = mutateFolders { folders ->
        val previous = folders.first { it.id == folderId }
        val updated = previous.copy(conversationIdList = ((previous.conversationIdList.toSet() - removed) + added).toList())
        folders.map { if (it.id == folderId) updated else it }
    }.map { folders ->
        val saved = folders.first { it.id == folderId }
        ConversationFolder(saved.id, saved.name, saved.type)
    }

    override suspend fun saveFolder(
        folder: ConversationFolder,
        added: Set<QualifiedID>,
        removed: Set<QualifiedID>,
    ): Either<CoreFailure, Unit> = mutateFolders { folders ->
        require(folder.type == FolderType.USER)
        require(folder.name.isNotBlank() && folder.name.length <= MAX_FOLDER_NAME_LENGTH)
        val previous = folders.firstOrNull { it.id == folder.id }
        require(
            previous?.name == folder.name || folders.none { it.id != folder.id && it.name.equals(folder.name, ignoreCase = true) }
        )
        require(previous == null || previous.type == FolderType.USER)
        val members = (previous?.conversationIdList.orEmpty().toSet() - removed) + added
        val saved = FolderWithConversations(folder.id, folder.name, FolderType.USER, members.toList())
        // Keep an edited folder where the user placed it; a new folder goes last.
        if (previous == null) folders + saved else folders.map { if (it.id == folder.id) saved else it }
    }.map { }

    override suspend fun deleteFolderAndSync(folderId: String): Either<CoreFailure, Unit> = mutateFolders { folders ->
        require(folders.first { it.id == folderId }.type == FolderType.USER)
        folders.filterNot { it.id == folderId }
    }.map { }

    // Only user folders move. Favorites and folders missing from the request, such as one just added on another
    // device, keep their places even if a caller lists Favorites.
    override suspend fun reorderFolders(folderIds: List<String>): Either<CoreFailure, Unit> = mutateFolders { folders ->
        val requested = folderIds.withIndex().associate { (index, id) -> id to index }
        fun FolderWithConversations.moves() = type == FolderType.USER && id in requested
        val reordered = folders.filter { it.moves() }.sortedBy { requested.getValue(it.id) }.iterator()
        folders.map { if (it.moves()) reordered.next() else it }
    }.map { }

    // Send one snapshot per batch. A network failure leaves local membership unchanged,
    // allowing the editor to keep its draft and retry without a partially applied batch.
    private suspend fun mutateFolders(
        transform: (List<FolderWithConversations>) -> List<FolderWithConversations>,
    ): Either<CoreFailure, List<FolderWithConversations>> = folderMutationMutex.withLock {
        wrapStorageRequest { transform(conversationFolderDAO.getFoldersWithConversations().map { it.toModel() }) }
            .flatMap { folders ->
                wrapApiRequest { userPropertiesApi.updateLabels(LabelListResponseDTO(folders.map { it.toLabel() })) }
                    .flatMap { persistFolders(folders).map { folders } }
            }
    }

    private companion object {
        const val MAX_FOLDER_NAME_LENGTH = 64
    }
}
