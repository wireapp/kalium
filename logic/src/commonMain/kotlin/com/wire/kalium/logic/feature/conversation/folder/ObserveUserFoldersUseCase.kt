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
import com.wire.kalium.logic.functional.mapToRightOr
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * This use case will observe and return the list of all user folders.
 * @see ConversationFolder
 */
fun interface ObserveUserFoldersUseCase {
    suspend operator fun invoke(): Flow<List<ConversationFolder>>
}

internal class ObserveUserFoldersUseCaseImpl(
    private val conversationFolderRepository: ConversationFolderRepository,
//     private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveUserFoldersUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationFolder>> {
        return conversationFolderRepository.observeUserFolders()
            .mapToRightOr(emptyList())
//             .flowOn(dispatchers.io)
    }
}
