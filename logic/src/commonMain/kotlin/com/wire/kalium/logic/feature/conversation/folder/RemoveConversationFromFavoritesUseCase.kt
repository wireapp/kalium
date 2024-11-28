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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will remove a conversation from the favorites folder.
 */
interface RemoveConversationFromFavoritesUseCase {
    /**
     * @param conversationId the id of the conversation
     * @return the [Result] indicating a successful operation, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(conversationId: ConversationId): Result

    sealed interface Result {
        data object Success : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

internal class RemoveConversationFromFavoritesUseCaseImpl(
    private val conversationFolderRepository: ConversationFolderRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : RemoveConversationFromFavoritesUseCase {
    override suspend fun invoke(
        conversationId: ConversationId
    ): RemoveConversationFromFavoritesUseCase.Result = withContext(dispatchers.io) {
        conversationFolderRepository.getFavoriteConversationFolder().fold(
            { RemoveConversationFromFavoritesUseCase.Result.Failure(it) },
            { folder ->
                conversationFolderRepository.removeConversationFromFolder(conversationId, folder.id)
                    .flatMap {
                        conversationFolderRepository.syncConversationFoldersFromLocal()
                    }
                    .fold({
                        RemoveConversationFromFavoritesUseCase.Result.Failure(it)
                    }, {
                        RemoveConversationFromFavoritesUseCase.Result.Success
                    })
            }
        )
    }
}
