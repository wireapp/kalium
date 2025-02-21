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

import com.wire.kalium.cells.CellsScope.Companion.ROOT_CELL
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

public interface ObserveCellFilesUseCase {
    /**
     * For TESTING purposes only.
     * Observe files from all conversations.
     *
     * @return [Flow] of [List] of [ConversationFiles]
     */
    public suspend operator fun invoke(): Flow<List<ConversationFiles>>
}

internal class ObserveCellFilesUseCaseImpl(
    private val conversationsDAO: ConversationDAO,
    private val cellsRepository: CellsRepository,
) : ObserveCellFilesUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationFiles>> {
        return conversationsDAO.getAllConversationDetails(fromArchive = false, filter = ConversationFilterEntity.ALL).map { conversations ->
            conversations.map { conversation ->
                ConversationFiles(
                    conversationId = QualifiedID(conversation.id.value, conversation.id.domain),
                    conversationTitle = conversation.name ?: "",
                    files = cellsRepository.getFiles("${ROOT_CELL}/${conversation.id}").getOrElse { emptyList() }
                )
            }.filter { it.files.isNotEmpty() }
        }
    }
}
public data class ConversationFiles(
    val conversationId: ConversationId,
    val conversationTitle: String,
    val files: List<CellNode>
)
