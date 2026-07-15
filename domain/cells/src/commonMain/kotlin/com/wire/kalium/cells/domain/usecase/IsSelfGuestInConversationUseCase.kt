/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.logic.data.user.UserId

/**
 * Returns whether the self user is a guest in the given conversation, i.e. the conversation is
 * owned by a team that the self user does not belong to.
 */
public interface IsSelfGuestInConversationUseCase {
    public suspend operator fun invoke(conversationId: String): Boolean
}

internal class IsSelfGuestInConversationUseCaseImpl(
    private val selfUserId: UserId,
    private val usersRepository: CellUsersRepository,
    private val conversationRepository: CellConversationRepository,
) : IsSelfGuestInConversationUseCase {
    override suspend fun invoke(conversationId: String): Boolean {
        val conversationTeamId = conversationRepository.getConversationTeamId(conversationId).getOrElse(null)
            ?: return false
        val selfTeamId = usersRepository.getUserTeamId(selfUserId).getOrElse(null)
        return conversationTeamId != selfTeamId
    }
}
