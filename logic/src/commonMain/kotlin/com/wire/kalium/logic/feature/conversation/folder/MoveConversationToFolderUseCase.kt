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

package com.wire.kalium.logic.feature.conversation.folder

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will move a conversation to the selected folder.
 */
interface MoveConversationToFolderUseCase {
    /**
     * @param conversationId the id of the conversation
     * @param folderId the id of the conversation folder
     * @param previousFolderId the id of the previous folder, if any
     * @return the [Result] indicating a successful operation, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(
        conversationId: ConversationId,
        folderId: String,
        previousFolderId: String?
    ): Result

    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

internal class MoveConversationToFolderUseCaseImpl(
    private val conversationFolderRepository: ConversationFolderRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : MoveConversationToFolderUseCase {
    override suspend fun invoke(
        conversationId: ConversationId,
        folderId: String,
        previousFolderId: String?
    ): MoveConversationToFolderUseCase.Result = withContext(dispatchers.io) {
        (
                previousFolderId?.let {
                    conversationFolderRepository.removeConversationFromFolder(conversationId, it)
                } ?: Either.Right(Unit)
                )
            .flatMap {
                conversationFolderRepository.addConversationToFolder(
                    conversationId,
                    folderId
                )
            }
            .flatMap { conversationFolderRepository.syncConversationFoldersFromLocal() }
            .fold({
                MoveConversationToFolderUseCase.Result.Failure(it)
            }, {
                MoveConversationToFolderUseCase.Result.Success
            })
    }
}
