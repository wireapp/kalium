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
package com.wire.kalium.cells.domain.usecase.publiclink

import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellsCredentials
import com.wire.kalium.cells.domain.model.PublicLink
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map

/**
 * Create new public link for given asset id. Created Public link is stored on the Wire Cell server.
 * Returns created link URL and Id.
 */
public interface CreatePublicLinkUseCase {
    public suspend operator fun invoke(assetId: String, fileName: String): Either<CoreFailure, PublicLink>
}

internal class CreatePublicLinkUseCaseImpl(
    private val cellsCredentials: CellsCredentials,
    private val cellsRepository: CellsRepository,
) : CreatePublicLinkUseCase {
    override suspend fun invoke(assetId: String, fileName: String): Either<CoreFailure, PublicLink> {
        return cellsRepository.createPublicLink(assetId, fileName)
            .map { link ->
                link.copy(
                    url = "${cellsCredentials.serverUrl}${link.url}"
                )
            }
    }
}
