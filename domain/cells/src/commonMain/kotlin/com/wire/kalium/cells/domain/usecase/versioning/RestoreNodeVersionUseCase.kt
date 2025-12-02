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
package com.wire.kalium.cells.domain.usecase.versioning

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

/**
 * Use case to restore a node version
 * @param uuid The node uuid
 * @param versionId The version uuid
 */
public interface RestoreNodeVersionUseCase {
    public suspend operator fun invoke(uuid: String, versionId: String): Either<CoreFailure, Unit>
}

internal class RestoreNodeVersionUseCaseImpl(
    private val cellsRepository: CellsRepository
) : RestoreNodeVersionUseCase {
    override suspend fun invoke(uuid: String, versionId: String): Either<CoreFailure, Unit> =
        cellsRepository.restoreNodeVersion(uuid, versionId)
}
