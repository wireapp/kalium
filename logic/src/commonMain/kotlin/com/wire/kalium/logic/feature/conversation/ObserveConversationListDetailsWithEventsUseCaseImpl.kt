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

import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationFilter
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.folders.ConversationFolderRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.conversation.folder.GetFavoriteFolderUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/**
 * This use case will observe and return the list of conversation details for the current user.
 * @see ConversationDetails
 */
public interface ObserveConversationListDetailsWithEventsUseCase {
    public suspend operator fun invoke(
        fromArchive: Boolean,
        conversationFilter: ConversationFilter,
        strictMlsFilter: Boolean = true,
    ): Flow<List<ConversationDetailsWithEvents>>
}

internal class ObserveConversationListDetailsWithEventsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val conversationFolderRepository: ConversationFolderRepository,
    private val getFavoriteFolder: GetFavoriteFolderUseCase,
    private val callRepository: CallRepository
) : ObserveConversationListDetailsWithEventsUseCase {

    override suspend operator fun invoke(
        fromArchive: Boolean,
        conversationFilter: ConversationFilter,
        strictMlsFilter: Boolean,
    ): Flow<List<ConversationDetailsWithEvents>> {
        return when (conversationFilter) {
            ConversationFilter.Favorites -> {
                when (val result = getFavoriteFolder()) {
                    GetFavoriteFolderUseCase.Result.Failure -> {
                        flowOf(emptyList())
                    }

                    is GetFavoriteFolderUseCase.Result.Success ->
                        conversationFolderRepository.observeConversationsFromFolder(result.folder.id)
                            .withOngoingCalls()
                }
            }

            is ConversationFilter.Folder -> {
                conversationFolderRepository.observeConversationsFromFolder(conversationFilter.folderId)
                    .withOngoingCalls()
            }

            ConversationFilter.All,
            ConversationFilter.Channels,
            ConversationFilter.Groups,
            ConversationFilter.OneOnOne ->
                conversationRepository.observeConversationListDetailsWithEvents(fromArchive, conversationFilter, strictMlsFilter)
                    .withOngoingCalls(fromArchive)
        }
    }

    private fun Flow<List<ConversationDetailsWithEvents>>.withOngoingCalls(
        fromArchive: Boolean = false
    ): Flow<List<ConversationDetailsWithEvents>> =
        if (fromArchive) {
            this
        } else {
            combine(callRepository.ongoingCallsFlow()) { conversations, ongoingCalls ->
                conversations.withOngoingCalls(ongoingCalls.map { it.conversationId }.toSet())
            }
        }

    private fun List<ConversationDetailsWithEvents>.withOngoingCalls(
        ongoingCallConversationIds: Set<ConversationId>
    ): List<ConversationDetailsWithEvents> =
        map { conversation ->
            val hasOngoingCall =
                conversation.conversationDetails.conversation.id in ongoingCallConversationIds &&
                        conversation.conversationDetails is ConversationDetails.Group
            conversation.withOngoingCall(hasOngoingCall)
        }.sortedByDescending {
            it.conversationDetails.conversation.id in ongoingCallConversationIds &&
                    it.conversationDetails is ConversationDetails.Group
        }

    private fun ConversationDetailsWithEvents.withOngoingCall(hasOngoingCall: Boolean): ConversationDetailsWithEvents =
        when (val details = conversationDetails) {
            is ConversationDetails.Group.Channel -> copy(
                conversationDetails = details.copy(hasOngoingCall = hasOngoingCall),
                hasNewActivitiesToShow = hasNewActivitiesToShow || hasOngoingCall
            )
            is ConversationDetails.Group.Regular -> copy(
                conversationDetails = details.copy(hasOngoingCall = hasOngoingCall),
                hasNewActivitiesToShow = hasNewActivitiesToShow || hasOngoingCall
            )
            else -> this
        }
}
