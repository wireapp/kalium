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

import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase.Result
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.fold

/**
 * This use case will return the favorite folder.
 * @return [Result.Success] with [ConversationFolder] in case of success,
 * or [Result.Failure] if something went wrong - can't get data from local DB.
 */
fun interface GetFavoriteFolderUseCase {
    suspend operator fun invoke(): Result

    sealed class Result {
        data class Success(val folder: ConversationFolder) : Result()
        data object Failure : Result()
    }
}

internal class GetFavoriteFolderUseCaseImpl(
    private val conversationFolderRepository: ConversationFolderRepository,
) : GetFavoriteFolderUseCase {

    override suspend operator fun invoke(): Result {
        return conversationFolderRepository.getFavoriteConversationFolder()
            .flatMapLeft {
                conversationFolderRepository.fetchConversationFolders().flatMap {
                    conversationFolderRepository.getFavoriteConversationFolder()
                }
            }
            .fold(
            { Result.Failure },
            { Result.Success(it) }
        )
    }
}
