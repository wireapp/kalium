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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import kotlinx.coroutines.flow.Flow

/**
 * Use case to observe if there is at least one conversation available with cell feature enabled.
 * This is required to determine if we can call Cell API methods without getting a Not Authenticated error.
 */
public interface ObserveIsAtLeastOneCellAvailableUseCase {
    public suspend operator fun invoke(): Either<StorageFailure, Flow<Boolean>>
}

internal class ObserveIsAtLeastOneCellAvailableUseCaseImpl(
    private val conversationRepository: CellConversationRepository
) : ObserveIsAtLeastOneCellAvailableUseCase {

    override suspend fun invoke(): Either<StorageFailure, Flow<Boolean>> = conversationRepository.observeHasConversationWithCell()
}
