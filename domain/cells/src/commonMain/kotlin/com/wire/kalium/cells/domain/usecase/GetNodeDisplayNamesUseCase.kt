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

/** Returns the conversation name for the given conversationId from local storage, or null if not found. */
public interface GetConversationNameUseCase {
    public suspend operator fun invoke(conversationId: String): String?
}

internal class GetConversationNameUseCaseImpl(
    private val repository: CellConversationRepository,
) : GetConversationNameUseCase {
    override suspend fun invoke(conversationId: String): String? =
        repository.getConversationNameById(conversationId).getOrElse(null)
}

/** Returns the display name for the given userId from local storage, or null if not found. */
public interface GetUserNameUseCase {
    public suspend operator fun invoke(userId: String): String?
}

internal class GetUserNameUseCaseImpl(
    private val repository: CellUsersRepository,
) : GetUserNameUseCase {
    override suspend fun invoke(userId: String): String? =
        repository.getUserNameById(userId).getOrElse(null)
}
