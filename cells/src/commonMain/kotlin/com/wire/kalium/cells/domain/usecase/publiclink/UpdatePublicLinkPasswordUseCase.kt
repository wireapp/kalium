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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess

/**
 * Update the password of a public link with the given [linkUuid].
 * If [password] is null, the password will be removed.
 * Local password is also updated (or removed).
 */
public interface UpdatePublicLinkPasswordUseCase {
    public suspend operator fun invoke(linkUuid: String, password: String?): Either<CoreFailure, Unit>
}

internal class UpdatePublicLinkPasswordUseCaseImpl(
    private val repository: CellsRepository
) : UpdatePublicLinkPasswordUseCase {
    override suspend fun invoke(
        linkUuid: String,
        password: String?
    ): Either<CoreFailure, Unit> =
        if (password.isNullOrEmpty()) {
            repository.removePublicLinkPassword(linkUuid)
                .onSuccess {
                    repository.clearPublicLinkPassword(linkUuid)
                }
        } else {
            repository.updatePublicLinkPassword(linkUuid, password)
                .onSuccess {
                    repository.savePublicLinkPassword(linkUuid, password)
                }
        }
}
