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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.data.FileFilters
import com.wire.kalium.cells.data.SortingCriteria
import com.wire.kalium.cells.data.SortingSpec
import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.SelfTeamIdProvider
import com.wire.kalium.cells.domain.model.CellNodeType
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.model.PaginatedList
import com.wire.kalium.cells.domain.model.toFileModel
import com.wire.kalium.cells.domain.model.toFolderModel
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.message.CellAssetContent

public interface GetPaginatedNodesUseCase {
    /**
     * Get files and folders from all conversations or search all files with text query.
     * Requests list of nodes from wire cell matching the query (all nodes for empty query)
     * Combines it with local data (local file name, file owner and conversation names)
     *
     * @return [List] of [Node]
     */
    @Suppress("LongParameterList")
    public suspend operator fun invoke(
        conversationId: String?,
        query: String,
        limit: Int = 100,
        offset: Int = 0,
        fileFilters: FileFilters,
        sortingSpec: SortingSpec = SortingSpec(
            criteria = SortingCriteria.FOLDERS_FIRST_THEN_ALPHABETICAL,
            descending = true
        ),
    ): Either<CoreFailure, PaginatedList<Node>>
}

internal class GetPaginatedNodesUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val conversationRepository: CellConversationRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
    private val usersRepository: CellUsersRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider,
) : GetPaginatedNodesUseCase {

    override suspend operator fun invoke(
        conversationId: String?,
        query: String,
        limit: Int,
        offset: Int,
        fileFilters: FileFilters,
        sortingSpec: SortingSpec
    ): Either<CoreFailure, PaginatedList<Node>> {

        val attachments = attachmentsRepository.getAttachments().getOrElse { emptyList() }.filterIsInstance<CellAssetContent>()
        val assets = attachmentsRepository.getStandaloneAssetPaths().getOrElse { emptyList() }

        return cellsRepository.getPaginatedNodes(
            path = conversationId,
            query = query,
            limit = limit,
            offset = offset,
            fileFilters = fileFilters,
            sortingSpec = sortingSpec,
        ).map { nodes ->
            val visibleNodes = nodes.data.filterNot { it.isDraft }

            val ownerIds = visibleNodes.mapNotNull { it.ownerUserId }.distinct()
            val conversationIds = visibleNodes.mapNotNull { it.conversationId }.distinct()

            val userNames = usersRepository.getUserNamesByIds(ownerIds).getOrElse(emptyList()).toMap()
            val conversations = conversationRepository.getConversationsByIds(conversationIds).getOrElse(emptyList())
            val conversationNames = conversations.associate { it.id to it.name }
            val selfTeamId = selfTeamIdProvider()
            val viewerOnlyByConversation = conversations.associate { conversation ->
                conversation.id to (conversation.teamId != null && conversation.teamId != selfTeamId)
            }

            PaginatedList(
                data = visibleNodes.map { node ->
                    val isViewerOnly = node.conversationId?.let { viewerOnlyByConversation[it] } ?: false

                    if (node.type == CellNodeType.FOLDER.value) {
                        node.toFolderModel().copy(
                            userName = node.ownerUserId?.let { userNames[it] },
                            conversationName = node.conversationId?.let { conversationNames[it] },
                            isViewerOnly = isViewerOnly,
                        )
                    } else {
                        val attachment = attachments.firstOrNull { attachment -> attachment.id == node.uuid }
                        node.toFileModel().copy(
                            localPath = attachment?.localPath ?: assets.firstOrNull { it.uuid == node.uuid }?.localPath,
                            metadata = attachment?.metadata,
                            userName = node.ownerUserId?.let { userNames[it] },
                            conversationName = node.conversationId?.let { conversationNames[it] },
                            isEditSupported = node.supportedEditors.isNotEmpty(),
                            isViewerOnly = isViewerOnly,
                        )
                    }
                },
                pagination = nodes.pagination
            )
        }
    }
}
