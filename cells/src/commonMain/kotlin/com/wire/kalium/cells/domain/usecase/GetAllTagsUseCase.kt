/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

/**
 * Use case to retrieve all tags from the cells repository.
 */
public interface GetAllTagsUseCase {
    public suspend operator fun invoke(): Either<CoreFailure, List<String>>
}

internal class GetAllTagsUseCaseImpl(
    private val cellsRepository: CellsRepository,
) : GetAllTagsUseCase {
    override suspend fun invoke(): Either<CoreFailure, List<String>> =
        cellsRepository.getAllTags()
}
