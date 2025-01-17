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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationFolder
import com.wire.kalium.logic.data.conversation.FolderType
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will create a new conversation folder.
 */
interface CreateConversationFolderUseCase {
    /**
     * @param folderName the name of the folder
     * @return the [Result] indicating a successful operation, otherwise a [CoreFailure]
     */
    suspend operator fun invoke(
        folderName: String
    ): Result

    sealed interface Result {
        data class Success(val folderId: String) : Result
        data class Failure(val cause: CoreFailure) : Result
    }
}

internal class CreateConversationFolderUseCaseImpl(
    private val conversationFolderRepository: ConversationFolderRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : CreateConversationFolderUseCase {
    override suspend fun invoke(folderName: String): CreateConversationFolderUseCase.Result = withContext(dispatchers.io) {
        val folder = ConversationFolder(
            id = uuid4().toString(),
            name = folderName,
            type = FolderType.USER
        )
        conversationFolderRepository.addFolder(folder)
            .fold({
                CreateConversationFolderUseCase.Result.Failure(it)
            }, {
                CreateConversationFolderUseCase.Result.Success(folder.id)
            })
    }
}
