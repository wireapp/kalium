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
import com.wire.kalium.cells.domain.model.NodePreview
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.right
import kotlinx.coroutines.delay

public interface GetPreviewUrlUseCase {
    public suspend operator fun invoke(assetId: String): Either<NetworkFailure, Unit>
}

@Suppress("MagicNumber")
internal class GetPreviewUrlUseCaseImpl internal constructor(
    private val cellsRepository: CellsRepository,
) : GetPreviewUrlUseCase {
    override suspend fun invoke(assetId: String): Either<NetworkFailure, Unit> {

        var retry = 0
        val previewList = mutableListOf<NodePreview>()

        do {
            previewList.addAll(
                cellsRepository.getPreviews(assetId)
                    .onFailure { return it.left() }
                    .getOrElse { emptyList() }
            )

            if (previewList.isEmpty()) {
                delay(500)
            }

        } while (previewList.isEmpty() && retry++ < 20)

        previewList.maxByOrNull { it.density }?.let {
            cellsRepository.savePreviewUrl(assetId, it.url)
        }

        return Unit.right()
    }
}
