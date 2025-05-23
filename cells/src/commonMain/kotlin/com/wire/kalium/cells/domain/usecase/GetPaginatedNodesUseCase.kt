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

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.CellsRepository
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
    public suspend operator fun invoke(
        conversationId: String?,
        query: String,
        limit: Int = 100,
        offset: Int = 0,
        onlyDeleted: Boolean = false,
    ): Either<CoreFailure, PaginatedList<Node>>
}

internal class GetPaginatedNodesUseCaseImpl(
    private val cellsRepository: CellsRepository,
    private val conversationRepository: CellConversationRepository,
    private val attachmentsRepository: CellAttachmentsRepository,
    private val usersRepository: CellUsersRepository,
) : GetPaginatedNodesUseCase {

    override suspend operator fun invoke(
        conversationId: String?,
        query: String,
        limit: Int,
        offset: Int,
        onlyDeleted: Boolean
    ): Either<CoreFailure, PaginatedList<Node>> {

        // Collect all data required to show the file/folder
        val userNames = usersRepository.getUserNames().getOrElse(emptyList())
        val conversationNames = conversationRepository.getConversationNames().getOrElse(emptyList())
        val attachments = attachmentsRepository.getAttachments().getOrElse { emptyList() }.filterIsInstance<CellAssetContent>()
        val assets = attachmentsRepository.getStandaloneAssetPaths().getOrElse { emptyList() }

        return cellsRepository.getPaginatedNodes(conversationId, query, limit, offset, onlyDeleted)
            .map { nodes ->
                PaginatedList(
                    data = nodes.data.asSequence()
                        .filterNot { it.isDraft }
                        .map { node ->
                            if (node.type == CellNodeType.FOLDER.value) {
                                node.toFolderModel().copy(
                                    userName = userNames.firstOrNull { it.first == node.ownerUserId }?.second,
                                    conversationName = conversationNames.firstOrNull { it.first == node.conversationId }?.second,
                                )
                            } else {
                                val attachment = attachments.firstOrNull { attachment -> attachment.id == node.uuid }
                                node.toFileModel().copy(
                                    localPath = attachment?.localPath ?: assets.firstOrNull { it.first == node.uuid }?.second,
                                    metadata = attachment?.metadata,
                                    userName = userNames.firstOrNull { it.first == node.ownerUserId }?.second,
                                    conversationName = conversationNames.firstOrNull { it.first == node.conversationId }?.second,
                                )
                            }
                        }.toList()
                        .sortedWith(
                            compareBy<Node> { it is Node.File }.thenBy {
                                when (it) {
                                    is Node.File -> it.name
                                    is Node.Folder -> it.name
                                }
                            }
                        ),
                    pagination = nodes.pagination
                )
            }
    }
}
