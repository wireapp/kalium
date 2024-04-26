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

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return the list of members of a given conversation.
 */
interface ObserveConversationMembersUseCase {
    /**
     * @param conversationId the id of the conversation to observe
     * @return a flow of [Result] with the list of [MemberDetails] of the conversation
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>>
}

class ObserveConversationMembersUseCaseImpl internal constructor(
    private val conversationRepository: ConversationRepository,
    private val userRepository: UserRepository
) : ObserveConversationMembersUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend operator fun invoke(conversationId: ConversationId): Flow<List<MemberDetails>> {
        return conversationRepository.observeConversationMembers(conversationId).map { members ->
            members.map { member ->
                userRepository.observeUser(member.id).filterNotNull().map {
                    MemberDetails(it, member.role)
                }
            }
        }.flatMapLatest { detailsFlows ->
            combine(detailsFlows) { it.toList() }
        }.distinctUntilChanged()
    }
}
