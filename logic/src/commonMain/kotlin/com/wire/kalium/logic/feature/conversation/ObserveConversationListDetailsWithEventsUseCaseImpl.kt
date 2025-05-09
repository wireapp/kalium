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

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationFilter
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * This use case will observe and return the list of conversation details for the current user.
 * @see ConversationDetails
 */
fun interface ObserveConversationListDetailsWithEventsUseCase {
    suspend operator fun invoke(fromArchive: Boolean, conversationFilter: ConversationFilter): Flow<List<ConversationDetailsWithEvents>>
}

internal class ObserveConversationListDetailsWithEventsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val conversationFolderRepository: ConversationFolderRepository,
    private val getFavoriteFolder: GetFavoriteFolderUseCase
) : ObserveConversationListDetailsWithEventsUseCase {

    override suspend operator fun invoke(
        fromArchive: Boolean,
        conversationFilter: ConversationFilter
    ): Flow<List<ConversationDetailsWithEvents>> {
        return when (conversationFilter) {
            ConversationFilter.Favorites -> {
                when (val result = getFavoriteFolder()) {
                    GetFavoriteFolderUseCase.Result.Failure -> {
                        flowOf(emptyList())
                    }

                    is GetFavoriteFolderUseCase.Result.Success ->
                        conversationFolderRepository.observeConversationsFromFolder(result.folder.id)
                }
            }

            is ConversationFilter.Folder -> {
                conversationFolderRepository.observeConversationsFromFolder(conversationFilter.folderId)
            }

            ConversationFilter.All,
            ConversationFilter.Channels,
            ConversationFilter.Groups,
            ConversationFilter.OneOnOne ->
                conversationRepository.observeConversationListDetailsWithEvents(fromArchive, conversationFilter)
        }
    }
}
