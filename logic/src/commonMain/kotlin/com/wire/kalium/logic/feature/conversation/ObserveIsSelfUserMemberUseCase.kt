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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ObserveIsSelfUserMemberUseCase {
    /**
     * Use case that check if self user is member of given conversation
     * @param conversationId the id of the conversation where user checks his membership
     * @return an [IsSelfUserMemberResult] containing Success or Failure cases
     */
    suspend operator fun invoke(
        conversationId: ConversationId
    ): Flow<IsSelfUserMemberResult>
}

internal class ObserveIsSelfUserMemberUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
) : ObserveIsSelfUserMemberUseCase {

    override suspend operator fun invoke(conversationId: ConversationId): Flow<IsSelfUserMemberResult> {
        return conversationRepository.observeIsUserMember(conversationId, selfUserId)
                .map { it.fold({ IsSelfUserMemberResult.Failure(it) }, { IsSelfUserMemberResult.Success(it) }) }
    }
}

sealed class IsSelfUserMemberResult {
    data class Success(val isMember: Boolean) : IsSelfUserMemberResult()
    data class Failure(val coreFailure: CoreFailure) : IsSelfUserMemberResult()
}
