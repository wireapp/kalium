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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.retry
import com.wire.kalium.common.functional.right

/**
 * Get preview URL for the asset from Wire Cell server.
 * Url is saved in the app database.
 */
public interface GetPreviewUrlUseCase {
    public suspend operator fun invoke(assetId: String): Either<CoreFailure, String?>
}

internal class GetPreviewUrlUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
) : GetPreviewUrlUseCase {

    private companion object {
        private const val MAX_RETRIES = 20
        private const val DELAY = 500L
    }

    override suspend fun invoke(assetId: String): Either<CoreFailure, String?> {
        return retry(MAX_RETRIES, DELAY) {
            cellsRepository.getPreviews(assetId).flatMap { response ->
                when {
                    response.isEmpty() -> StorageFailure.DataNotFound.left()
                    else -> response.right()
                }
            }
        }.flatMap { previews ->
            previews.maxBy { it.density }.let { preview ->
                cellsRepository.savePreviewUrl(assetId, preview.url).map { preview.url }
            }
        }
    }
}
